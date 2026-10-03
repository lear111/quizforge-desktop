param([string]$QuestionBank)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$previousDirectory = Get-Location
try {
    Set-Location -LiteralPath $repository
    if (-not $QuestionBank) { $QuestionBank = Join-Path $repository 'examples/step7-practice/Java集合练习.qbank' }
    $QuestionBank = (Resolve-Path -LiteralPath $QuestionBank).Path
    # Native stderr may contain harmless JVM warnings; validate native exit codes explicitly.
    $ErrorActionPreference = 'Continue'
    & npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
    if ($LASTEXITCODE -ne 0) { throw 'Shared Practice frontend build failed.' }
    & mvn.cmd -B -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/shared-practice-poc' -DskipTests install
    if ($LASTEXITCODE -ne 0) { throw 'Shared Practice Java build failed.' }
    & mvn.cmd -B -pl quizforge-desktop-app '-Dquizforge.build.directory=target/shared-practice-poc' dependency:build-classpath '-Dmdep.outputFile=target/shared-practice-poc/runtime-classpath.txt' -Dmdep.includeScope=runtime
    if ($LASTEXITCODE -ne 0) { throw 'Shared Practice runtime classpath resolution failed.' }
    $desktopDirectory = Join-Path $repository 'quizforge-desktop-app'
    $dependencies = (Get-Content -LiteralPath (Join-Path $desktopDirectory 'target/shared-practice-poc/runtime-classpath.txt') -Raw).Trim()
    $classpath = (Join-Path $desktopDirectory 'target/shared-practice-poc/classes') + [IO.Path]::PathSeparator + $dependencies
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
    & $java -cp $classpath io.quizforge.desktop.poc.sharedpractice.SharedPracticeCanvasLauncher $QuestionBank
    if ($LASTEXITCODE -ne 0) { throw 'Shared Practice POC exited with an error.' }
} finally {
    Set-Location -LiteralPath $previousDirectory.Path
}
