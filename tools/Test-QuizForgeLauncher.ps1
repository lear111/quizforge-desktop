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
echo %*>> "%QUIZFORGE_LAUNCHER_TEST_LOG%.args"
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
    Set-Content -LiteralPath "$callsPath.args" -Value ''
    & (Join-Path $repository 'tools\Start-QuizForge.ps1')
    $calls = @(Get-Content -LiteralPath $callsPath | Where-Object { $_ })
    if ($calls.Count -ne 2 -or ($calls | Where-Object { $_ -ne 'dev= css=' })) { throw 'Normal launch did not isolate packaged mode.' }
    $argumentCalls = @(Get-Content -LiteralPath "$callsPath.args" | Where-Object { $_ })
    if ($argumentCalls.Count -ne 2 -or ($argumentCalls | Where-Object { $_ -notmatch '-Dquizforge.build.directory=target/launcher' })) {
        throw 'Startup build/run did not use isolated compiler output.'
    }
    if ($env:QUIZFORGE_CANVAS_EDITOR_DEV_URL -ne 'http://127.0.0.1:5173') { throw 'Development environment was not restored.' }
    Write-Host 'PASS: normal launch invokes no Node/npm and clears inherited dev URL.'
    Write-Host 'PASS: both startup build and JavaFX run use isolated compiler output.'

    Set-Content -LiteralPath $callsPath -Value ''
    & (Join-Path $repository 'tools\Start-QuizForge.ps1') -LiveCss
    $calls = @(Get-Content -LiteralPath $callsPath | Where-Object { $_ })
    $expected = 'dev= css=' + (Join-Path $repository 'quizforge-desktop-app\src\main\resources\styles')
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

# Exercise the actual CMD entry points from a path with spaces and a foreign cwd.
# Only the shared backend is replaced; no application or build tools are started.
$entrypointDirectory = Join-Path $testDirectory ('entry points ' + [Guid]::NewGuid().ToString('N'))
$entrypointTools = Join-Path $entrypointDirectory 'tools'
[void][IO.Directory]::CreateDirectory($entrypointTools)
foreach ($entrypoint in @('Start-QuizForge.cmd', 'Start-QuizForge-LiveUi.cmd')) {
    Copy-Item -LiteralPath (Join-Path $repository $entrypoint) -Destination $entrypointDirectory
}
[IO.File]::WriteAllText((Join-Path $entrypointTools 'Start-QuizForge.ps1'), @'
param([switch]$LiveCss, [switch]$LiveWeb, [switch]$LiveJava, [switch]$LiveUi)
[IO.File]::WriteAllText($env:QUIZFORGE_ENTRYPOINT_TEST_LOG,
    (@($LiveCss.IsPresent, $LiveWeb.IsPresent, $LiveJava.IsPresent, $LiveUi.IsPresent) -join ','))
exit ([int]$env:QUIZFORGE_ENTRYPOINT_TEST_EXIT)
'@, [Text.UTF8Encoding]::new($false))
$previousEntrypointLog = $env:QUIZFORGE_ENTRYPOINT_TEST_LOG
$previousEntrypointExit = $env:QUIZFORGE_ENTRYPOINT_TEST_EXIT
function Invoke-EntrypointProbe([string]$Name, [string]$Flags, [string]$ExpectedFlags, [int]$ExitCode) {
    $env:QUIZFORGE_ENTRYPOINT_TEST_LOG = Join-Path $entrypointDirectory 'flags.txt'
    $env:QUIZFORGE_ENTRYPOINT_TEST_EXIT = [string]$ExitCode
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $env:ComSpec
    $startInfo.Arguments = '/d /s /c ""' + (Join-Path $entrypointDirectory $Name) + '" ' + $Flags + '"'
    $startInfo.WorkingDirectory = $testDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $probe = [Diagnostics.Process]::new()
    $probe.StartInfo = $startInfo
    try {
        [void]$probe.Start()
        $stdout = $probe.StandardOutput.ReadToEndAsync()
        $stderr = $probe.StandardError.ReadToEndAsync()
        # Supply input for the normal failure pause so the probe stays unattended.
        $probe.StandardInput.WriteLine()
        $probe.StandardInput.Close()
        if (-not $probe.WaitForExit(15000)) {
            & "$env:SystemRoot\System32\taskkill.exe" /PID $probe.Id /T /F *> $null
            throw "Entrypoint probe timed out: $Name"
        }
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($probe.ExitCode -ne $ExitCode) { throw "Entrypoint exit code changed: $Name, $output" }
        if ([IO.File]::ReadAllText($env:QUIZFORGE_ENTRYPOINT_TEST_LOG) -ne $ExpectedFlags) {
            throw "Entrypoint flags changed: $Name"
        }
    } finally { $probe.Dispose() }
}
try {
    Invoke-EntrypointProbe 'Start-QuizForge.cmd' '' 'False,False,False,False' 0
    Invoke-EntrypointProbe 'Start-QuizForge-LiveUi.cmd' '-LiveCss' 'True,False,False,True' 0
    Write-Host 'PASS: both CMD entry points resolve the backend from paths with spaces and forward startup flags.'
    Invoke-EntrypointProbe 'Start-QuizForge-LiveUi.cmd' '' 'False,False,False,True' 23
    Write-Host 'PASS: the development shortcut preserves backend failure exit codes.'
} finally {
    $env:QUIZFORGE_ENTRYPOINT_TEST_LOG = $previousEntrypointLog
    $env:QUIZFORGE_ENTRYPOINT_TEST_EXIT = $previousEntrypointExit
}
