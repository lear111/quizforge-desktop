$ErrorActionPreference = 'Stop'
$logDirectory = Join-Path $env:LOCALAPPDATA 'QuizForge\logs'
$logPath = Join-Path $logDirectory 'desktop-launch.log'

try {
    [void][System.IO.Directory]::CreateDirectory($logDirectory)
    Set-Location -LiteralPath $PSScriptRoot
    $maven = (Get-Command mvn.cmd -ErrorAction Stop).Source

    # Keep the development shortcut up to date with desktop source changes.
    # Windows PowerShell treats native stderr as errors, including JVM warnings.
    $ErrorActionPreference = 'Continue'
    & $maven -B -pl quizforge-desktop-app javafx:run *> $logPath
    $launchExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($launchExitCode -ne 0) {
        throw "QuizForge could not start. See the startup log: $logPath"
    }
} catch {
    Add-Type -AssemblyName PresentationFramework
    [void][System.Windows.MessageBox]::Show(
        $_.Exception.Message, 'QuizForge V2', 'OK', 'Error')
    exit 1
}
