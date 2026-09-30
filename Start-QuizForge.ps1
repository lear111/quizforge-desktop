param([switch]$LiveCss, [switch]$LiveWeb, [switch]$LiveJava, [switch]$LiveUi)

if ($LiveUi) { $LiveCss = $true; $LiveWeb = $true; $LiveJava = $true }

$ErrorActionPreference = 'Stop'
$logDirectory = Join-Path $env:LOCALAPPDATA 'QuizForge\logs'
$logPath = Join-Path $logDirectory 'desktop-launch.log'
$previousLiveCssDirectory = $env:QUIZFORGE_LIVE_CSS_DIR
$previousCanvasDevUrl = $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$viteProcess = $null
$viteJob = $null
$viteLogPath = Join-Path $logDirectory 'canvas-vite.log'
$viteErrorPath = Join-Path $logDirectory 'canvas-vite-error.log'
if ($LiveCss) {
    $env:QUIZFORGE_LIVE_CSS_DIR = Join-Path $PSScriptRoot 'quizforge-desktop-app\src\main\resources\io\quizforge\desktop\ui'
}

try {
    [void][System.IO.Directory]::CreateDirectory($logDirectory)
    Set-Location -LiteralPath $PSScriptRoot
    if ($LiveWeb) {
        $nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
        $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if (-not $nodeCommand -or -not $npmCommand) {
            throw 'LiveWeb requires Node.js and npm on PATH. Ordinary startup does not require them.'
        }
        $webDirectory = Join-Path $PSScriptRoot 'quizforge-desktop-app\canvas-editor-web'
        if (-not (Test-Path -LiteralPath (Join-Path $webDirectory 'node_modules\vite\bin\vite.js'))) {
            throw "Vite is not installed. Run npm ci in $webDirectory before using LiveWeb."
        }
        # Probe the exact interface before starting our own server; never reuse another process.
        $portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse('127.0.0.1'), 5173)
        try { $portProbe.Start() } catch { throw 'LiveWeb cannot start: 127.0.0.1:5173 is already occupied.' }
        finally { $portProbe.Stop() }

        Write-Host 'Canvas LiveWeb: npm run dev -- --host 127.0.0.1 --port 5173 --strictPort'
        . (Join-Path $PSScriptRoot 'tools\QuizForgeLiveWebProcess.ps1')
        $viteJob = [QuizForge.LiveWebProcessJob]::new()
        $viteArguments = '/d /s /c ""' + $npmCommand.Source + '" run dev -- --host 127.0.0.1 --port 5173 --strictPort"'
        $viteProcess = Start-Process -FilePath $env:ComSpec -ArgumentList $viteArguments `
            -WorkingDirectory $webDirectory -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $viteLogPath -RedirectStandardError $viteErrorPath
        try { $viteJob.Attach($viteProcess) } catch {
            # Assignment failure must not leave a server outside our ownership.
            $cleanupPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & "$env:SystemRoot\System32\taskkill.exe" /PID $viteProcess.Id /T /F *> $null
            $ErrorActionPreference = $cleanupPreference
            throw
        }
        $ready = $false
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        while ([DateTime]::UtcNow -lt $deadline) {
            $viteProcess.Refresh()
            if ($viteProcess.HasExited) {
                throw "Vite exited before startup (exit $($viteProcess.ExitCode)). See $viteLogPath and $viteErrorPath"
            }
            try {
                $response = Invoke-WebRequest -Uri 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 2
                if ($response.StatusCode -eq 200 -and $response.Content.Contains('/@vite/client')) { $ready = $true; break }
            } catch { # Continue only while our server starts, bounded by the deadline.
            }
            Start-Sleep -Milliseconds 200
        }
        if (-not $ready) { throw "Vite was not ready within 30 seconds. See $viteLogPath and $viteErrorPath" }
        $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL = 'http://127.0.0.1:5173'
        # JavaFX's HTTP/2 loader attempts h2c upgrades against Vite's HTTP/1 server.
        # Use HTTP/1 only for development; WebSocket HMR remains enabled.
        $env:JAVA_TOOL_OPTIONS = ($previousJavaToolOptions + ' -Dcom.sun.webkit.useHTTP2Loader=false').Trim()
    } else {
        # A normal launch always uses packaged resources, even from a development shell.
        Remove-Item Env:QUIZFORGE_CANVAS_EDITOR_DEV_URL -ErrorAction SilentlyContinue
    }
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

    if ($LiveJava) {
        $ErrorActionPreference = 'Stop'
        . (Join-Path $PSScriptRoot 'tools\QuizForgeLiveJava.ps1')
        Start-QuizForgeLiveJava -Repository $PSScriptRoot -Maven $maven -LogDirectory $logDirectory
        $launchExitCode = 0
    } else {
        & $maven -B -pl quizforge-desktop-app javafx:run *>> $logPath
        $launchExitCode = $LASTEXITCODE
    }
    $ErrorActionPreference = 'Stop'
    if ($launchExitCode -ne 0) {
        throw "QuizForge could not start. See the startup log: $logPath"
    }
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    if ($LiveWeb -or $LiveJava) { exit 1 }
    Add-Type -AssemblyName PresentationFramework
    [void][System.Windows.MessageBox]::Show(
        $_.Exception.Message, 'QuizForge V2', 'OK', 'Error')
    exit 1
} finally {
    if ($null -ne $viteJob) { $viteJob.Dispose() }
    if ($null -ne $viteProcess) { $viteProcess.Dispose() }
    if ($null -eq $previousCanvasDevUrl) {
        Remove-Item Env:QUIZFORGE_CANVAS_EDITOR_DEV_URL -ErrorAction SilentlyContinue
    } else {
        $env:QUIZFORGE_CANVAS_EDITOR_DEV_URL = $previousCanvasDevUrl
    }
    if ($LiveWeb) {
        if ($null -eq $previousJavaToolOptions) {
            Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
        } else { $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions }
    }
    if ($LiveCss) {
        if ($null -eq $previousLiveCssDirectory) {
            Remove-Item Env:QUIZFORGE_LIVE_CSS_DIR -ErrorAction SilentlyContinue
        } else {
            $env:QUIZFORGE_LIVE_CSS_DIR = $previousLiveCssDirectory
        }
    }
}
