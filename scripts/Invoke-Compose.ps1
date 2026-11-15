param(
    [ValidateSet('up','down','ps','logs','restart','config','build')][string]$Action = 'up',
    [string]$EnvFile = '.env',
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')][string]$ProjectName = 'scarlet-nexus'
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
    'config' { @('config','--quiet') }
    'build' { @('build') }
}
function Convert-WslPath([string]$Path) {
    if ($Path -notmatch '^([A-Za-z]):[\\/](.*)$') { throw 'The local WSL fallback requires an absolute Windows drive path.' }
    return '/mnt/' + $Matches[1].ToLowerInvariant() + '/' + $Matches[2].Replace('\','/')
}
$nativeDocker = Get-Command docker -CommandType Application -ErrorAction SilentlyContinue
$ErrorActionPreference = 'Continue'
if ($nativeDocker) {
    & $nativeDocker.Source compose --project-directory $projectRoot --env-file $envPath -p $ProjectName @actionArguments
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
        "$runtime/bin/docker",'compose','--project-directory',$linuxRoot,'--env-file',$linuxEnv,'-p',$ProjectName) + $actionArguments
    & wsl.exe @wslArguments
    $result = $LASTEXITCODE
}
$ErrorActionPreference = 'Stop'
if ($result -ne 0) { throw "Compose action '$Action' failed ($result)." }
