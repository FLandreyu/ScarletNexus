param(
    [ValidateSet('Host','Guest')][string]$Mode = 'Host',
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$target = [IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $target) { throw 'Choose a new report file; existing evidence is preserved.' }
if (-not (Test-Path -LiteralPath (Split-Path -Parent $target))) { throw 'Create the evidence parent directory first.' }
$os = Get-CimInstance Win32_OperatingSystem
$computer = Get-CimInstance Win32_ComputerSystem
$processors = @(Get-CimInstance Win32_Processor)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
$administratorGroupPresent = @($identity.Groups | Where-Object { $_.Value -eq 'S-1-5-32-544' }).Count -gt 0
$elevated = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$tools = @('java','javac','node','npm','psql','postgres','docker') | ForEach-Object {
    $command = Get-Command $_ -ErrorAction SilentlyContinue | Select-Object -First 1
    [ordered]@{name=$_; detected=($null -ne $command)}
}
$installedTools = @()
foreach ($registry in @('HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall','HKLM:/Software/WOW6432Node/Microsoft/Windows/CurrentVersion/Uninstall','HKCU:/Software/Microsoft/Windows/CurrentVersion/Uninstall')) {
    if (Test-Path -LiteralPath $registry) {
        $installedTools += @(Get-ChildItem -LiteralPath $registry | Get-ItemProperty | Where-Object { $_.DisplayName -match '(?i)Java|JDK|OpenJDK|Temurin|PostgreSQL|Node\.js|Docker|Visual C\+\+.*Redistributable|Guest Additions' } | ForEach-Object { $_.DisplayName })
    }
}
$adapters = @(Get-NetAdapter -ErrorAction Stop | Where-Object { $_.Status -eq 'Up' })
$profile = Split-Path -Leaf $env:USERPROFILE
$edition = (Get-ItemProperty 'HKLM:/SOFTWARE/Microsoft/Windows NT/CurrentVersion').EditionID
$report = [ordered]@{
    schema='scarlet-desktop-environment/v1'; mode=$Mode; recordedUtc=[DateTime]::UtcNow.ToString('o')
    validationStatus='ENVIRONMENT_OBSERVATION_ONLY'; installationAndBusinessGates='NOT_RUN'
    os=[ordered]@{caption=$os.Caption; version=$os.Version; editionId=$edition; architecture=$os.OSArchitecture}
    hardware=[ordered]@{memoryBytes=$computer.TotalPhysicalMemory; hypervisorPresent=$computer.HypervisorPresent; processors=@($processors | Select-Object Name,NumberOfLogicalProcessors,VirtualizationFirmwareEnabled,SecondLevelAddressTranslationExtensions)}
    account=[ordered]@{administratorGroupPresent=$administratorGroupPresent; elevated=$elevated; profileContainsNonAscii=($profile -match '[^\x00-\x7f]'); profileContainsSpace=$profile.Contains(' ')}
    globalTools=@($tools); installedRuntimeOrGuestPackages=@($installedTools | Sort-Object -Unique)
    networking=[ordered]@{upAdapterCount=$adapters.Count; allAdaptersDisconnected=($adapters.Count -eq 0)}
    drives=@(Get-PSDrive -PSProvider FileSystem | Select-Object Name,Free,Used)
    note='This report cannot establish a pristine VM, installation success or D3 acceptance. Use VM snapshot provenance and the manual evidence matrix. Hypervisor presence can mask individual CPU virtualization flags.'
}
$json = $report | ConvertTo-Json -Depth 8
$stream = [IO.File]::Open($target,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try { $bytes=[Text.UTF8Encoding]::new($false).GetBytes($json); $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
Write-Output ('Read-only environment report: '+$target)
