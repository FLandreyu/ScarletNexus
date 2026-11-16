param(
    [Parameter(Mandatory=$true)][string]$EnvFile,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9_-]*(acceptance|test|ci)[a-z0-9_-]*$')][string]$ProjectName,
    [string]$BaseUrl = 'http://127.0.0.1:8088',
    [string]$NodeHome,
    [string]$OutputRoot = '.runtime/browser-results'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$endpoint = [Uri]$BaseUrl
if ($endpoint.Host -notin @('127.0.0.1','localhost','[::1]') -or $endpoint.Scheme -notin @('http','https')) {
    throw 'Browser acceptance must use a local isolated gateway.'
}
if (-not $env:E2E_EMAIL -or -not $env:E2E_PASSWORD) { throw 'Set isolated E2E_EMAIL and E2E_PASSWORD in the environment first.' }
$artifactRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot $OutputRoot))
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.runtime')) + [IO.Path]::DirectorySeparatorChar
if (-not $artifactRoot.StartsWith($allowedRoot,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputRoot must be a child directory of the workspace .runtime folder.'
}
$previousBaseUrl = $env:PLAYWRIGHT_BASE_URL
$previousPath = $env:PATH
try {
    if ($NodeHome) {
        $resolvedNode = (Resolve-Path -LiteralPath $NodeHome).Path
        if (-not (Test-Path -LiteralPath (Join-Path $resolvedNode 'node.exe'))) { throw 'NodeHome must contain node.exe.' }
        $env:PATH = "$resolvedNode;$env:PATH"
    }
    $env:PLAYWRIGHT_BASE_URL = $BaseUrl
    foreach ($browserProject in @('desktop','mobile')) {
        # Each project gets a fresh application process; PostgreSQL and attachment volumes stay in place.
        # This exercises the default login limit instead of changing production security for a test suite.
        & (Join-Path $PSScriptRoot 'Invoke-Compose.ps1') -Action recreate-app -EnvFile $EnvFile -ProjectName $ProjectName
        Push-Location (Join-Path $projectRoot 'frontend')
        try {
            $testOutput = Join-Path $artifactRoot $browserProject
            $ErrorActionPreference = 'Continue'
            & npm.cmd run test:e2e -- "--project=$browserProject" --output $testOutput
            $testExit = $LASTEXITCODE
            $ErrorActionPreference = 'Stop'
            if ($testExit -ne 0) { throw "Browser project $browserProject failed ($testExit)." }
        } finally { Pop-Location }
    }
} finally {
    $env:PLAYWRIGHT_BASE_URL = $previousBaseUrl
    $env:PATH = $previousPath
}
