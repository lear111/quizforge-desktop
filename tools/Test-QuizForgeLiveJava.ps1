# Isolated real-JVM HotSwap verification. No acceptance workspace or DB is opened.
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$javaHome = if ($env:QUIZFORGE_LIVE_JAVA_HOME) { $env:QUIZFORGE_LIVE_JAVA_HOME } else { $env:JAVA_HOME }
$javaHome = $javaHome.Trim('"')
$java = Join-Path $javaHome 'bin\java.exe'
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'
$testRoot = Join-Path $repository ('target\live-java-smoke\' + [Guid]::NewGuid().ToString('N'))
$sourceDirectory = Join-Path $testRoot 'fixture\src\main\java\io\quizforge\probe'
$classes = Join-Path $testRoot 'fixture\target\classes'
$runtime = Join-Path $testRoot 'runtime'
$agentClasses = Join-Path $testRoot 'agent'
foreach ($directory in @($sourceDirectory, $classes, $runtime, $agentClasses)) { [void][IO.Directory]::CreateDirectory($directory) }
$source = Join-Path $sourceDirectory 'LiveValue.java'
$probeSource = Join-Path $sourceDirectory 'Probe.java'
$resourceDirectory = Join-Path $testRoot 'fixture\src\main\resources'
[void][IO.Directory]::CreateDirectory($resourceDirectory)
$resource = Join-Path $resourceDirectory 'banner.txt'
$before = 'package io.quizforge.probe; public class LiveValue { public int state=7; public String value() { return "before:"+state; } }'
$after = 'package io.quizforge.probe; public class LiveValue { public int state=7; public int added; public String extra() { return "new"; } public String value() { return "after:"+state+":"+extra()+":"+added; } }'
$recovered = $after.Replace('after:', 'recovered:')
[IO.File]::WriteAllText($source, $before)
[IO.File]::WriteAllText($probeSource, @'
package io.quizforge.probe;
public class Probe {
 public static void main(String[] args) throws Exception {
  LiveValue instance = new LiveValue();
  while (true) {
   String banner="none";
   try (var input=Probe.class.getResourceAsStream("/banner.txt")) { if(input!=null)banner=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8); }
   System.out.println(instance.value()+" pid="+ProcessHandle.current().pid()+" banner="+banner); Thread.sleep(300);
  }
 }
}
'@)
& $javac --release 21 -d $classes $source $probeSource
if ($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed.' }
Copy-Item -LiteralPath $classes -Destination (Join-Path $runtime 'fixture') -Recurse
& $javac --release 21 -d $agentClasses (Join-Path $repository 'tools\live-java\QuizForgeLiveAgent.java')
if ($LASTEXITCODE -ne 0) { throw 'Agent compilation failed.' }
$manifest = Join-Path $testRoot 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`nPremain-Class: io.quizforge.dev.QuizForgeLiveAgent`nCan-Redefine-Classes: true`n`n", [Text.Encoding]::ASCII)
$agentJar = Join-Path $testRoot 'agent.jar'
& $jar --create --file $agentJar --manifest $manifest -C $agentClasses .
if ($LASTEXITCODE -ne 0) { throw 'Agent JAR failed.' }
$fakeMaven = Join-Path $testRoot 'mvn.cmd'
[IO.File]::WriteAllText($fakeMaven, "@echo off`r`n`"$javac`" --release 21 -d `"$classes`" `"$source`" `"$probeSource`"`r`nset compileResult=%ERRORLEVEL%`r`nif exist `"$resource`" copy /y `"$resource`" `"$classes\banner.txt`" >nul`r`nexit /b %compileResult%`r`n", [Text.Encoding]::ASCII)
$stdout = Join-Path $testRoot 'stdout.log'
$stderr = Join-Path $testRoot 'stderr.log'
$launchArguments = @('-XX:+AllowEnhancedClassRedefinition', "-javaagent:$agentJar", "-Dquizforge.liveJava.repo=$testRoot", "-Dquizforge.liveJava.output=$runtime", "-Dquizforge.liveJava.maven=$fakeMaven", '-Dquizforge.liveJava.modules=fixture', '-cp', (Join-Path $runtime 'fixture'), 'io.quizforge.probe.Probe')
$argsFile = Join-Path $testRoot 'java.args'
[IO.File]::WriteAllLines($argsFile, @($launchArguments | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' }), [Text.UTF8Encoding]::new($false))
. (Join-Path $repository 'tools\QuizForgeLiveWebProcess.ps1')
$job = [QuizForge.LiveWebProcessJob]::new()
$process = $null
function Wait-LiveText([string]$Expected) {
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) { throw "Probe exited: $(Get-Content -LiteralPath $stderr -Raw)" }
        if ((Get-Content -LiteralPath $stdout -Raw -ErrorAction SilentlyContinue) -match [regex]::Escape($Expected)) { return }
        Start-Sleep -Milliseconds 200
    }
    throw "Timeout waiting for $Expected. See $stdout and $stderr"
}
try {
    $process = Start-Process -FilePath $java -ArgumentList ('@"' + $argsFile + '"') -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $job.Attach($process)
    Wait-LiveText 'before:7'
    $originalPid = $process.Id
    [IO.File]::WriteAllText($resource, 'live-resource')
    Wait-LiveText 'banner=live-resource'
    if ((Get-Content -LiteralPath $stdout -Raw).Contains('COMPILING')) { throw 'Resource-only edit unnecessarily compiled Java.' }
    Write-Host 'PASS: added resource became readable in the same JVM without Java compilation.'
    [IO.File]::WriteAllText($source, $after)
    Wait-LiveText 'after:7:new:0'
    if ($process.Id -ne $originalPid) { throw 'JVM restarted.' }
    Write-Host 'PASS: method body + added method/field changed in the same JVM; existing state=7 retained.'
    [IO.File]::WriteAllText($source, 'not valid Java')
    Wait-LiveText 'COMPILE_FAILED'
    if ($process.HasExited) { throw 'Failed compilation killed the JVM.' }
    [IO.File]::WriteAllText($source, $recovered)
    Wait-LiveText 'recovered:7:new:0'
    Write-Host 'PASS: compilation failure retained running classes; fixing source resumed HotSwap.'
    [IO.File]::WriteAllText((Join-Path $testRoot 'pom.xml'), '<project/>')
    Wait-LiveText 'RESTART_REQUIRED'
    [IO.File]::WriteAllText($source, $recovered.Replace('recovered:', 'must-not-load:'))
    Start-Sleep -Seconds 3
    if ((Get-Content -LiteralPath $stdout -Raw).Contains('must-not-load:')) { throw 'Restart-required barrier was bypassed.' }
    Write-Host 'PASS: build/config change establishes a restart barrier; subsequent edits cannot bypass it.'
} finally {
    $job.Dispose()
    if ($null -ne $process) { [void]$process.WaitForExit(5000); $process.Dispose() }
}
Write-Host "PASS: owned probe/compiler process tree stopped. Evidence: $testRoot"
