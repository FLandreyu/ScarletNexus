param([string]$JavaHome = $env:JAVA_HOME, [string]$NodeHome, [switch]$Testcontainers)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'Provide -JavaHome with a Java 21 JDK directory.' }
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome/bin;$env:PATH"
if ($NodeHome) {
    $resolvedNodeHome = (Resolve-Path -LiteralPath $NodeHome).Path
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedNodeHome 'node.exe'))) { throw 'NodeHome must contain node.exe.' }
    $env:PATH = "$resolvedNodeHome;$env:PATH"
}
Push-Location (Join-Path $projectRoot 'frontend')
try {
    foreach ($arguments in @(@('ci'), @('run','format:check'), @('run','typecheck'), @('test'), @('run','build'))) {
        $ErrorActionPreference = 'Continue'
        & npm.cmd @arguments
        $nativeExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($nativeExit -ne 0) { throw "npm $arguments failed ($nativeExit)" }
    }
} finally { Pop-Location }
Push-Location (Join-Path $projectRoot 'backend')
try {
    $mavenArguments = @('-B', 'verify')
    if ($Testcontainers) { $mavenArguments += '-Ptestcontainers' }
    $ErrorActionPreference = 'Continue'
    & mvn @mavenArguments
    $nativeExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($nativeExit -ne 0) { throw "Maven verification failed ($nativeExit)" }
} finally { Pop-Location }
