param([switch]$Verify, [switch]$SkipBuild, [switch]$Product, [switch]$Editor, [switch]$History, [string]$ExtensionsDirectory)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$previousDirectory = Get-Location
try {
    Set-Location -LiteralPath $repository
    $maven = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
    if (-not $maven) { throw 'Maven is required. Put mvn.cmd on PATH.' }
    if (-not $SkipBuild) {
        & (Join-Path $PSScriptRoot 'Build-WebView2.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'Native build failed.' }
        & npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
        & $maven -q -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/webview2-check' -DskipTests install
        if ($LASTEXITCODE -ne 0) { throw 'Java build failed.' }
    }
    & $maven -q -pl quizforge-desktop-app '-Dquizforge.build.directory=target/webview2-check' dependency:build-classpath '-Dmdep.outputFile=target/webview2-check/runtime-classpath.txt' '-Dmdep.includeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed.' }
    $desktop = Join-Path $repository 'quizforge-desktop-app'
    $dependencies = (Get-Content -LiteralPath (Join-Path $desktop 'target/webview2-check/runtime-classpath.txt') -Raw).Trim()
    $classpath = (Join-Path $desktop 'target/webview2-check/classes') + [IO.Path]::PathSeparator + $dependencies
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
    $arguments = @('-cp', $classpath, 'io.quizforge.desktop.browser.webview2.WebView2Entry')
    if (-not $ExtensionsDirectory) { $ExtensionsDirectory = Join-Path $repository 'extensions/dist' }
    $arguments += "--extensions=$ExtensionsDirectory"
    if ($Verify) { $arguments += '--verify' }
    if ($Product) { $arguments += '--product' }
    if ($Editor) { $arguments += '--editor' }
    if ($History) { $arguments += '--history' }
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'WebView2 verification exited with an error.' }
} finally { Set-Location -LiteralPath $previousDirectory.Path }
