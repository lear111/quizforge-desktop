param([switch]$Fresh, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$lab = Join-Path $repository 'target/extension-lab'
[void][System.IO.Directory]::CreateDirectory($lab)
$profileFile = Join-Path $lab 'current-profile.txt'
if ($Fresh -or -not (Test-Path -LiteralPath $profileFile)) {
    $dataDirectory = Join-Path $lab ('profiles/' + [guid]::NewGuid().ToString())
    Set-Content -LiteralPath $profileFile -Value $dataDirectory -Encoding UTF8
} else { $dataDirectory = (Get-Content -LiteralPath $profileFile -Raw).Trim() }
if (-not $dataDirectory.StartsWith($lab + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Invalid extension-lab profile path.'
}
$buildDirectory = 'target/extension-lab-runtime'
$classes = Join-Path $repository "quizforge-desktop-app/$buildDirectory/classes"
$classpathFile = Join-Path $repository "quizforge-desktop-app/$buildDirectory/runtime-classpath.txt"
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'Set JAVA_HOME to JDK 21 or newer.' }
Push-Location -LiteralPath $repository
try {
    if (-not (Test-Path -LiteralPath 'target/webview2-native/quizforge_webview2.dll')) {
        & (Join-Path $PSScriptRoot 'Build-WebView2.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'WebView2 native build failed.' }
    }
    if (-not $SkipBuild) {
        & mvn.cmd -B -pl quizforge-desktop-app -am "-Dquizforge.build.directory=$buildDirectory" -DskipTests install
        if ($LASTEXITCODE -ne 0) { throw 'Desktop build failed.' }
        & mvn.cmd -B -pl quizforge-desktop-app "-Dquizforge.build.directory=$buildDirectory" dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
        if ($LASTEXITCODE -ne 0) { throw 'Dependency resolution failed.' }
    }
    if (-not (Test-Path -LiteralPath $classpathFile)) { throw 'Run without -SkipBuild once.' }
    $dependencies = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    Write-Host "Extension test profile: $dataDirectory"
    Write-Host "Install packages from: $(Join-Path $repository 'extensions/dist')"
    Write-Host 'This is the standard desktop UI. No extensions are preinstalled or automatically approved.'
    & $java "-Dquizforge.dataDir=$dataDirectory" -cp ($classes + ';' + $dependencies) io.quizforge.desktop.bootstrap.DesktopEntry
    if ($LASTEXITCODE -ne 0) { throw 'Desktop application exited with an error.' }
} finally { Pop-Location }
