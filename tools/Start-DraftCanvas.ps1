param()
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$previousDirectory = Get-Location
try {
    Set-Location -LiteralPath $repository
    # Windows PowerShell maps native stderr (including JVM warnings) to ErrorRecord.
    # Check each native exit code explicitly so benign warnings do not abort startup.
    $ErrorActionPreference = 'Continue'
    & npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
    if ($LASTEXITCODE -ne 0) { throw 'Draft Canvas frontend build failed.' }
    & mvn.cmd -B -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/draft-poc' -DskipTests install
    if ($LASTEXITCODE -ne 0) { throw 'Draft Canvas Java build failed.' }
    & mvn.cmd -B -pl quizforge-desktop-app '-Dquizforge.build.directory=target/draft-poc' dependency:build-classpath '-Dmdep.outputFile=target/draft-poc/runtime-classpath.txt' -Dmdep.includeScope=runtime
    if ($LASTEXITCODE -ne 0) { throw 'Draft Canvas runtime classpath resolution failed.' }
    $desktopDirectory = Join-Path $repository 'quizforge-desktop-app'
    $dependencies = (Get-Content -LiteralPath (Join-Path $desktopDirectory 'target/draft-poc/runtime-classpath.txt') -Raw).Trim()
    $classpath = (Join-Path $desktopDirectory 'target/draft-poc/classes') + [IO.Path]::PathSeparator + $dependencies
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
    & $java -cp $classpath io.quizforge.desktop.poc.draftcanvas.DraftCanvasLauncher
    if ($LASTEXITCODE -ne 0) { throw 'Draft Canvas POC exited with an error.' }
} finally {
    Set-Location -LiteralPath $previousDirectory.Path
}
