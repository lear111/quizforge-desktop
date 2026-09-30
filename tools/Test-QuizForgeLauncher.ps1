# Isolated launcher smoke test: no Java, Node or Vite is started.
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$testDirectory = Join-Path $repository 'target\launcher-smoke'
[void][System.IO.Directory]::CreateDirectory($testDirectory)
$mavenFixture = Join-Path $testDirectory 'mvn.cmd'
$callsPath = Join-Path $testDirectory 'calls.txt'
Set-Content -LiteralPath $mavenFixture -Encoding Ascii -Value @'
@echo off
echo dev=%QUIZFORGE_CANVAS_EDITOR_DEV_URL% css=%QUIZFORGE_LIVE_CSS_DIR%>> "%QUIZFORGE_LAUNCHER_TEST_LOG%"
exit /b 0
'@
$previousPath = $env:PATH
$previousDev = $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL
$previousCss = $env:QUIZFORGE_LIVE_CSS_DIR
$previousTestLog = $env:QUIZFORGE_LAUNCHER_TEST_LOG
$previousLocalAppData = $env:LOCALAPPDATA
$previousLocation = Get-Location
try {
    $env:PATH = "$testDirectory;$previousPath"
    $env:QUIZFORGE_LAUNCHER_TEST_LOG = $callsPath
    $env:LOCALAPPDATA = $testDirectory
    function Get-Command {
        param([string]$Name, $ErrorAction)
        if ($Name -in @('node.exe', 'npm.cmd')) { throw 'Non-LiveWeb startup queried Node/npm.' }
        Microsoft.PowerShell.Core\Get-Command $Name -ErrorAction SilentlyContinue
    }
    $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL = 'http://127.0.0.1:5173'
    $env:QUIZFORGE_LIVE_CSS_DIR = $null
    Set-Content -LiteralPath $callsPath -Value ''
    & (Join-Path $repository 'Start-QuizForge.ps1')
    $calls = @(Get-Content -LiteralPath $callsPath | Where-Object { $_ })
    if ($calls.Count -ne 2 -or ($calls | Where-Object { $_ -ne 'dev= css=' })) { throw 'Normal launch did not isolate packaged mode.' }
    if ($env:QUIZFORGE_CANVAS_EDITOR_DEV_URL -ne 'http://127.0.0.1:5173') { throw 'Development environment was not restored.' }
    Write-Host 'PASS: normal launch invokes no Node/npm and clears inherited dev URL.'

    Set-Content -LiteralPath $callsPath -Value ''
    & (Join-Path $repository 'Start-QuizForge.ps1') -LiveCss
    $calls = @(Get-Content -LiteralPath $callsPath | Where-Object { $_ })
    $expected = 'dev= css=' + (Join-Path $repository 'quizforge-desktop-app\src\main\resources\io\quizforge\desktop\ui')
    if ($calls.Count -ne 2 -or ($calls | Where-Object { $_ -ne $expected })) { throw 'LiveCss behavior changed.' }
    Write-Host 'PASS: LiveCss retains its CSS environment and invokes no Node/npm.'
} finally {
    Remove-Item Function:Get-Command -ErrorAction SilentlyContinue
    $env:PATH = $previousPath
    $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL = $previousDev
    $env:QUIZFORGE_LIVE_CSS_DIR = $previousCss
    $env:QUIZFORGE_LAUNCHER_TEST_LOG = $previousTestLog
    $env:LOCALAPPDATA = $previousLocalAppData
    Set-Location -LiteralPath $previousLocation.Path
}
