param([switch]$WithLiveCss, [string]$CanvasDevUrl = '')
# End-to-end: real Maven, development launcher, native JavaFX scene, unchanged JVM and unsaved input.
$ErrorActionPreference = 'Stop'
$sourceRepository = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$root = Join-Path $sourceRepository ('target\live-java-ui-smoke\' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($root)
# HotSwap modifies an owned source copy so an interrupted probe cannot leave edits in the checkout.
$repository = Join-Path $root 'checkout'
[void][IO.Directory]::CreateDirectory($repository)
Copy-Item -LiteralPath (Join-Path $sourceRepository 'pom.xml') -Destination $repository
Copy-Item -LiteralPath (Join-Path $sourceRepository 'tools') -Destination $repository -Recurse
foreach ($module in @('quizforge-core', 'quizforge-infrastructure', 'quizforge-desktop-app')) {
    $moduleDirectory = Join-Path $repository $module
    [void][IO.Directory]::CreateDirectory($moduleDirectory)
    Copy-Item -LiteralPath (Join-Path $sourceRepository "$module\pom.xml") -Destination $moduleDirectory
    Copy-Item -LiteralPath (Join-Path $sourceRepository "$module\src") -Destination $moduleDirectory -Recurse
}
$maven = (Get-Command mvn.cmd).Source
$jdk = if ($env:QUIZFORGE_LIVE_JAVA_HOME) { $env:QUIZFORGE_LIVE_JAVA_HOME } else { $env:JAVA_HOME }
$dependencyFile = Join-Path $root 'classpath.txt'
& $maven -B -f (Join-Path $repository 'pom.xml') -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/launcher' -DskipTests install *> (Join-Path $root 'build.log')
if ($LASTEXITCODE -ne 0) { throw "Probe build failed: $root\build.log" }
& $maven -B -f (Join-Path $repository 'pom.xml') -pl quizforge-desktop-app dependency:build-classpath '-DincludeScope=runtime' "-Dmdep.outputFile=$dependencyFile" *> (Join-Path $root 'dependency.log')
if ($LASTEXITCODE -ne 0) { throw 'Probe dependency resolution failed.' }
$modules = @( 'quizforge-core', 'quizforge-infrastructure', 'quizforge-desktop-app')
$classpath = (@($modules | ForEach-Object { Join-Path $repository "$_\target\launcher\classes" }) + @([IO.File]::ReadAllText($dependencyFile).Trim())) -join ';'
$probeClasses = Join-Path $root 'probe'
[void][IO.Directory]::CreateDirectory($probeClasses)
& (Join-Path $jdk 'bin\javac.exe') --release 21 -cp $classpath -d $probeClasses (Join-Path $repository 'tools\live-java\QuizForgeLiveUiProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Native probe compilation failed.' }
$runner = Join-Path $root 'run.ps1'
[IO.File]::WriteAllText($runner, @'
param($Repository, $Maven, $Logs, $Probe)
$ErrorActionPreference='Stop'
. (Join-Path $Repository 'tools\QuizForgeLiveJava.ps1')
Start-QuizForgeLiveJava -Repository $Repository -Maven $Maven -LogDirectory $Logs -MainClass io.quizforge.desktop.ui.content.document.canvas.QuizForgeLiveUiProbe -AdditionalClasspath $Probe
'@)
$editorUi = Join-Path $repository 'quizforge-desktop-app\src\main\java\io\quizforge\desktop\ui\shared\EditorUi.java'
$original = [IO.File]::ReadAllBytes($editorUi)
$text = [Text.Encoding]::UTF8.GetString($original)
$changedText = $text.Replace('UiTheme.label(label, "editor-caption")', 'UiTheme.label(label + " [LiveJava native probe]", "editor-caption")')
if ($text -eq $changedText) { throw 'Native probe marker location missing.' }
$changedBytes = [Text.Encoding]::UTF8.GetBytes($changedText)
$stop = Join-Path $root 'stop'
$previousStop = $env:QUIZFORGE_LIVE_PROBE_STOP
$previousCss = $env:QUIZFORGE_LIVE_CSS_DIR
$previousWeb = $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$env:QUIZFORGE_LIVE_PROBE_STOP = $stop
$job = $null
$process = $null
$mutated = $false
$log = Join-Path $root 'live-java.log'
function Wait-Native([string]$Expected, [int]$Count=1) {
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) { throw "Native launcher exited: $(Get-Content (Join-Path $root 'live-java-error.log') -Raw -ErrorAction SilentlyContinue)" }
        $content = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
        if ($content -and [regex]::Matches($content, [regex]::Escape($Expected)).Count -ge $Count) { return }
        Start-Sleep -Milliseconds 300
    }
    throw "Native reload timed out: $Expected. See $root"
}
try {
    if ($WithLiveCss) { $env:QUIZFORGE_LIVE_CSS_DIR = Join-Path $repository 'quizforge-desktop-app\src\main\resources\styles' }
    if ($CanvasDevUrl) {
        # Optional read-only probe of an explicitly supplied dev server; this script never owns or stops it.
        $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL = $CanvasDevUrl
        $env:JAVA_TOOL_OPTIONS = ($previousJavaOptions + ' -Dcom.sun.webkit.useHTTP2Loader=false').Trim()
    }
    . (Join-Path $repository 'tools\QuizForgeLiveWebProcess.ps1')
    $job = [QuizForge.LiveWebProcessJob]::new()
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $runner + '" "' + $repository + '" "' + $maven + '" "' + $root + '" "' + $probeClasses + '"'
    $process = Start-Process powershell.exe -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'launcher.log') -RedirectStandardError (Join-Path $root 'launcher-error.log')
    $job.Attach($process)
    Wait-Native 'NATIVE_SCENE original'
    if ($env:QUIZFORGE_CANVAS_EDITOR_DEV_URL) { Wait-Native 'CANVAS_READY'; Write-Host 'PASS: Canvas dev page initialized in the Java agent JVM; Java/JS bridge and draft retained.' }
    [IO.File]::WriteAllBytes($editorUi, $changedBytes); $mutated = $true
    Wait-Native 'NATIVE_SCENE updated'
    $content = Get-Content -LiteralPath $log -Raw
    $nativePids = @([regex]::Matches($content, 'NATIVE_SCENE (?:original|updated) pid=(\d+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
    if ($nativePids.Count -ne 1) { throw 'Native scene JVM changed.' }
    Write-Host 'PASS: real Maven compiled a Java toolbar-label change; existing JavaFX window updated automatically; same PID and draft retained.'
    [IO.File]::WriteAllBytes($editorUi, $original); $mutated = $false
    Wait-Native 'NATIVE_SCENE original' 2
    Write-Host 'PASS: reverting Java source automatically restored the toolbar in the same window.'
    [IO.File]::WriteAllText($stop, 'stop')
    if (-not $process.WaitForExit(10000)) { throw 'Launcher did not stop after its JavaFX window closed.' }
    if ($process.ExitCode -ne 0) { throw 'Native launcher failed during cleanup.' }
    Write-Host "PASS: launcher and owned process tree exited. Evidence: $root"
} finally {
    if ($mutated) {
        if ([Convert]::ToBase64String([IO.File]::ReadAllBytes($editorUi)) -eq [Convert]::ToBase64String($changedBytes)) {
            [IO.File]::WriteAllBytes($editorUi, $original)
        } else { Write-Warning 'EditorUi changed concurrently; probe did not overwrite those edits.' }
    }
    if ($null -ne $job) { $job.Dispose() }
    if ($null -ne $process) { $process.Dispose() }
    $env:QUIZFORGE_LIVE_PROBE_STOP = $previousStop
    $env:QUIZFORGE_LIVE_CSS_DIR = $previousCss
    $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL = $previousWeb
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
