param(
    [ValidateSet('up','down','ps','logs','restart','recreate-app','config','build')][string]$Action = 'up',
    [string]$EnvFile = '.env',
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')][string]$ProjectName = 'scarlet-nexus',
    [string[]]$ComposeFiles = @('docker-compose.yml')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = if ([IO.Path]::IsPathRooted($EnvFile)) { [IO.Path]::GetFullPath($EnvFile) } else { [IO.Path]::GetFullPath((Join-Path $projectRoot $EnvFile)) }
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { throw 'Create .env from .env.example and configure its database password and administrator bootstrap first.' }
$actionArguments = switch ($Action) {
    'up' { @('up','--build','-d','--wait','--wait-timeout','180') }
    'down' { @('down') }
    'ps' { @('ps') }
    'logs' { @('logs','--tail','100','backend') }
    'restart' { @('restart') }
    'recreate-app' { @('up','--no-deps','--force-recreate','-d','--wait','--wait-timeout','120','backend','web') }
    'config' { @('config','--quiet') }
    'build' { @('build') }
}
function Convert-WslPath([string]$Path) {
    if ($Path -notmatch '^([A-Za-z]):[\\/](.*)$') { throw 'The local WSL fallback requires an absolute Windows drive path.' }
    return '/mnt/' + $Matches[1].ToLowerInvariant() + '/' + $Matches[2].Replace('\','/')
}
$composePaths = @($ComposeFiles | ForEach-Object {
    $candidate = [IO.Path]::GetFullPath((Join-Path $projectRoot $_))
    if (-not $candidate.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw 'ComposeFiles must name existing files inside this workspace.' }
    $candidate
})
if ($composePaths.Count -eq 0) { throw 'Provide at least one Compose file.' }
$nativeDocker = Get-Command docker -CommandType Application -ErrorAction SilentlyContinue
$ErrorActionPreference = 'Continue'
if ($nativeDocker) {
    $fileArguments = @($composePaths | ForEach-Object { '-f'; $_ })
    & $nativeDocker.Source compose --project-directory $projectRoot --env-file $envPath -p $ProjectName @fileArguments @actionArguments
    $result = $LASTEXITCODE
} else {
    $runtime = '/root/.cache/scarlet-nexus-p0-runtime'
    $linuxRoot = Convert-WslPath $projectRoot
    $linuxEnv = Convert-WslPath $envPath
    & wsl.exe -d Ubuntu --cd /tmp -- test -S "$runtime/docker.sock"
    if ($LASTEXITCODE -ne 0) { throw 'Start an available Docker engine first. See docs/OPERATIONS.md for the existing isolated WSL runtime.' }
    $wslArguments = @('-d','Ubuntu','--cd','/tmp','--','env',
        "DOCKER_HOST=unix://$runtime/docker.sock", "DOCKER_CONFIG=$runtime/config",
        "PATH=$runtime/bin`:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        'HTTP_PROXY=http://127.0.0.1:17877','HTTPS_PROXY=http://127.0.0.1:17877','NO_PROXY=localhost,127.0.0.1',
        "$runtime/bin/docker",'compose','--project-directory',$linuxRoot,'--env-file',$linuxEnv,'-p',$ProjectName) +
        @($composePaths | ForEach-Object { '-f'; Convert-WslPath $_ }) + $actionArguments
    & wsl.exe @wslArguments
    $result = $LASTEXITCODE
}
$ErrorActionPreference = 'Stop'
if ($result -ne 0) { throw "Compose action '$Action' failed ($result)." }
