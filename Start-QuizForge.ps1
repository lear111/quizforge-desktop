$ErrorActionPreference = 'Stop'
$logDirectory = Join-Path $env:LOCALAPPDATA 'QuizForge\logs'
$logPath = Join-Path $logDirectory 'desktop-launch.log'

try {
    [void][System.IO.Directory]::CreateDirectory($logDirectory)
    Set-Location -LiteralPath $PSScriptRoot
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mavenCommand) {
        $maven = $mavenCommand.Source
    } else {
        $mavenHome = [Environment]::GetEnvironmentVariable('MAVEN_HOME')
        $maven = if ($mavenHome) { Join-Path $mavenHome.Trim('"') 'bin\mvn.cmd' }
        if (-not $maven -or -not (Test-Path -LiteralPath $maven -PathType Leaf)) {
            throw 'Maven was not found. Set MAVEN_HOME to a Maven installation or add mvn.cmd to PATH.'
        }
    }

    # The desktop-only JavaFX goal resolves other modules from the local Maven repository.
    # Install this reactor first so the shortcut never launches against stale module JARs.
    # Windows PowerShell treats native stderr as errors, including JVM warnings.
    $ErrorActionPreference = 'Continue'
    & $maven -B -pl quizforge-desktop-app -am -DskipTests install *> $logPath
    $buildExitCode = $LASTEXITCODE
    if ($buildExitCode -ne 0) {
        throw "QuizForge build failed. See the startup log: $logPath"
    }

    & $maven -B -pl quizforge-desktop-app javafx:run *>> $logPath
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
