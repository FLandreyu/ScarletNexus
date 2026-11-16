param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Provide -JavaHome with a Java 25 JDK directory.'
}
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome/bin;$env:PATH"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'backend')
try {
    Write-Host 'Starting P0 preview on 127.0.0.1:8080 with a disposable real PostgreSQL database.'
    Write-Host 'Test-only local trust authentication. Do not enter personal data. Ctrl+C stops the preview.'
    $ErrorActionPreference = 'Continue'
    & mvn -B test-compile exec:java '-Dexec.mainClass=dev.scarletnexus.P0Preview' '-Dexec.classpathScope=test'
    $nativeExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($nativeExit -ne 0) { throw "P0 preview exited with code $nativeExit" }
} finally {
    Pop-Location
}
