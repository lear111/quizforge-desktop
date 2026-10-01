function Start-QuizForgeLiveJava {
    param([string]$Repository, [string]$Maven, [string]$LogDirectory,
        [string]$MainClass = 'io.quizforge.dev.QuizForgeDevLauncher', [string]$AdditionalClasspath = '')
    $ErrorActionPreference = 'Stop'
    $javaHome = $env:QUIZFORGE_LIVE_JAVA_HOME
    if (-not $javaHome) { $javaHome = $env:JAVA_HOME }
    if (-not $javaHome) { throw 'LiveJava requires JAVA_HOME (or QUIZFORGE_LIVE_JAVA_HOME) pointing to JBR 21 with javac and jar.' }
    $javaHome = $javaHome.Trim('"')
    $java = Join-Path $javaHome 'bin\java.exe'
    $javac = Join-Path $javaHome 'bin\javac.exe'
    $jar = Join-Path $javaHome 'bin\jar.exe'
    foreach ($executable in @($java, $javac, $jar)) {
        if (-not (Test-Path -LiteralPath $executable)) { throw "LiveJava requires a JDK: missing $executable" }
    }
    $probePath = [System.IO.Path]::GetTempFileName()
    try {
        $ErrorActionPreference = 'Continue'
        & $java '-XX:+AllowEnhancedClassRedefinition' '-version' *> $probePath
        $supported = $LASTEXITCODE -eq 0
        $ErrorActionPreference = 'Stop'
        if (-not $supported) { throw 'This JVM does not support enhanced HotSwap. Set QUIZFORGE_LIVE_JAVA_HOME to a JetBrains JBR JDK.' }
    } finally { Remove-Item -LiteralPath $probePath -ErrorAction SilentlyContinue }

    # One compiler per checkout. Separate class directories also isolate running Java from failed builds.
    $identityBytes = [Text.Encoding]::UTF8.GetBytes([IO.Path]::GetFullPath($Repository).ToLowerInvariant())
    $identity = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($identityBytes)).Replace('-', '')
    $mutex = [Threading.Mutex]::new($false, "Local\QuizForgeLiveJava_$identity")
    $ownsMutex = $false
    $application = $null
    $job = $null
    try {
        try { $ownsMutex = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $ownsMutex = $true }
        if (-not $ownsMutex) { throw 'LiveJava is already running for this checkout. Close that instance before starting another.' }
        $output = Join-Path $Repository ('target\live-java\' + [Guid]::NewGuid().ToString('N'))
        $agentClasses = Join-Path $output 'agent'
        [void][IO.Directory]::CreateDirectory($agentClasses)
        $modules = @( 'quizforge-core', 'quizforge-infrastructure', 'quizforge-desktop-app')
        $buildDirectory = 'target/launcher'
        foreach ($module in $modules) {
            $source = Join-Path $Repository "$module\$buildDirectory\classes"
            if (-not (Test-Path -LiteralPath $source)) { throw "Missing compiled module: $module" }
            Copy-Item -LiteralPath $source -Destination (Join-Path $output $module) -Recurse
        }
        $ErrorActionPreference = 'Continue'
        & $javac --release 21 -d $agentClasses (Join-Path $Repository 'tools\live-java\QuizForgeLiveAgent.java') (Join-Path $Repository 'tools\live-java\QuizForgeDevLauncher.java')
        $compilerExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($compilerExit -ne 0) { throw 'LiveJava agent compilation failed.' }
        $manifest = Join-Path $output 'MANIFEST.MF'
        [IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`nPremain-Class: io.quizforge.dev.QuizForgeLiveAgent`nCan-Redefine-Classes: true`n`n", [Text.Encoding]::ASCII)
        $agentJar = Join-Path $output 'live-java-agent.jar'
        $ErrorActionPreference = 'Continue'
        & $jar --create --file $agentJar --manifest $manifest -C $agentClasses .
        $jarExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($jarExit -ne 0) { throw 'LiveJava agent packaging failed.' }

        $dependencyFile = Join-Path $output 'dependencies.txt'
        $ErrorActionPreference = 'Continue'
        & $Maven -B -pl quizforge-desktop-app dependency:build-classpath '-DincludeScope=runtime' "-Dmdep.outputFile=$dependencyFile" *> (Join-Path $output 'classpath.log')
        $classpathExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($classpathExit -ne 0) { throw "Could not resolve development classpath. See $output\classpath.log" }
        $dependencies = ([IO.File]::ReadAllText($dependencyFile)).Trim().Split(';') | Where-Object {
            # Reactor dependencies must come from development classes, never installed JARs.
            $_ -notmatch '[\\/]io[\\/]quizforge[\\/]'
        }
        $classpath = (@($modules | ForEach-Object { Join-Path $output $_ }) + @($dependencies) + @($agentJar)) -join ';'
        if ($AdditionalClasspath) { $classpath += ';' + $AdditionalClasspath }
        $arguments = @(
            '-XX:+AllowEnhancedClassRedefinition', "-javaagent:$agentJar",
            "-Dquizforge.liveJava.repo=$Repository", "-Dquizforge.liveJava.output=$output",
            "-Dquizforge.liveJava.maven=$Maven", ('-Dquizforge.liveJava.modules=' + ($modules -join ',')),
            "-Dquizforge.liveJava.buildDirectory=$buildDirectory",
            '-cp', $classpath, $MainClass
        )
        # A Java argument file handles Windows command length and spaces without shell interpolation.
        $argumentFile = Join-Path $output 'launch.args'
        $quoted = $arguments | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' }
        [IO.File]::WriteAllLines($argumentFile, $quoted, [Text.UTF8Encoding]::new($false))
        . (Join-Path $Repository 'tools\QuizForgeLiveWebProcess.ps1')
        $job = [QuizForge.LiveWebProcessJob]::new()
        $stdout = Join-Path $LogDirectory 'live-java.log'
        $stderr = Join-Path $LogDirectory 'live-java-error.log'
        $application = Start-Process -FilePath $java -ArgumentList ('@"' + $argumentFile + '"') -WorkingDirectory $Repository -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        try { $job.Attach($application) } catch {
            & "$env:SystemRoot\System32\taskkill.exe" /PID $application.Id /T /F *> $null
            throw
        }
        Write-Host "LiveJava enabled. Compilation and reload status: $stdout"
        $seen = 0
        while (-not $application.WaitForExit(500)) {
            $lines = @(Get-Content -LiteralPath $stdout -ErrorAction SilentlyContinue)
            if ($lines.Count -gt $seen) {
                $lines | Select-Object -Skip $seen | Where-Object { $_.StartsWith('[LiveJava]') } | ForEach-Object { Write-Host $_ }
                $seen = $lines.Count
            }
        }
        if ($application.ExitCode -ne 0) { throw "LiveJava desktop exited with $($application.ExitCode). See $stderr" }
    } finally {
        if ($null -ne $job) { $job.Dispose() }
        if ($null -ne $application) { $application.Dispose() }
        if ($ownsMutex) { $mutex.ReleaseMutex() }
        $mutex.Dispose()
    }
}
