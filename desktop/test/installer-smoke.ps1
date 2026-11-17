param([Parameter(Mandatory=$true)][string]$Installer,[string]$UpgradeInstaller,[switch]$BusinessRegression)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$boundary = [IO.Path]::GetFullPath((Join-Path $workspace '.runtime')) + [IO.Path]::DirectorySeparatorChar
$package = (Resolve-Path -LiteralPath $Installer).Path
if (-not $package.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase)) { throw 'Use a workspace test installer.' }
$existing = @(Get-ChildItem 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Uninstall' | Get-ItemProperty | Where-Object { $_.DisplayName -eq 'Scarlet Nexus' -or $_.DisplayName -like 'Scarlet Nexus *' })
if ($existing.Count) { throw 'An existing Scarlet Nexus installation must not be overwritten by acceptance tests.' }
$desktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Scarlet Nexus.lnk'
$menuShortcut = Join-Path ([Environment]::GetFolderPath('Programs')) 'Scarlet Nexus.lnk'
if ((Test-Path -LiteralPath $desktopShortcut) -or (Test-Path -LiteralPath $menuShortcut)) { throw 'Preserve existing Scarlet Nexus shortcuts.' }
$case = Join-Path $boundary ('desktop-d3-install-' + [guid]::NewGuid().ToString('N'))
$upgrade = if ($UpgradeInstaller) { (Resolve-Path -LiteralPath $UpgradeInstaller).Path } else { $null }
if ($upgrade -and -not $upgrade.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase)) { throw 'Use a workspace upgrade installer.' }
$installation = Join-Path $case '中文安装'
$profile = Join-Path $case '独立用户'
[IO.Directory]::CreateDirectory($case) | Out-Null
$env:DESKTOP_TEST_INSTALLER = $package
$env:DESKTOP_TEST_EXE = Join-Path $installation 'Scarlet Nexus.exe'
$env:DESKTOP_TEST_PROFILE = $profile
$env:Path = (Join-Path $workspace '.tools/node-v24.22.3-win-x64') + ';' + $env:Path
function Install-TestPackage {
    # /D is the final NSIS argument; unlike other arguments it must not be quoted.
    $process = Start-Process -FilePath $package -ArgumentList ('/S /D=' + $installation) -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw ('Installer exit code: ' + $process.ExitCode) }
    if (-not (Test-Path -LiteralPath $env:DESKTOP_TEST_EXE)) { throw 'Installed executable missing.' }
    $shell = New-Object -ComObject WScript.Shell
    foreach ($shortcut in @($desktopShortcut,$menuShortcut)) {
        if (-not (Test-Path -LiteralPath $shortcut)) { throw 'Product shortcut missing.' }
        if ($shell.CreateShortcut($shortcut).TargetPath -ne $env:DESKTOP_TEST_EXE) { throw 'Shortcut points to another application.' }
    }
}
function Run-InstalledCase([string]$phase) {
    $env:DESKTOP_TEST_PHASE = $phase
    & node (Join-Path $PSScriptRoot 'installed-smoke.cjs')
    if ($LASTEXITCODE -ne 0) { throw ('Packaged application acceptance failed: ' + $phase) }
}
function Uninstall-TestPackage {
    $target = [IO.Path]::GetFullPath($installation)
    if (-not $target.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe uninstall target.' }
    $uninstaller = Join-Path $target 'Uninstall Scarlet Nexus.exe'
    $process = Start-Process -FilePath $uninstaller -ArgumentList '/S' -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw ('Uninstaller exit code: ' + $process.ExitCode) }
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    while (Test-Path -LiteralPath $env:DESKTOP_TEST_EXE) {
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Installed executable remained after uninstall.' }
        Start-Sleep -Milliseconds 200
    }
    if ((Test-Path -LiteralPath $desktopShortcut) -or (Test-Path -LiteralPath $menuShortcut)) { throw 'Product shortcuts remained after uninstall.' }
    if (-not (Test-Path -LiteralPath (Join-Path $profile 'learning/secrets/database.json'))) { throw 'Uninstall removed persistent credentials.' }
}
$protected = Join-Path $case 'protected-learning'
[IO.Directory]::CreateDirectory($protected) | Out-Null
$marker = Join-Path $protected 'owner.json'
[IO.File]::WriteAllText($marker,'directory-guard-sentinel')
$guardPackage = if ($upgrade) { $upgrade } else { $package }
$probe = Start-Process -FilePath $guardPackage -ArgumentList ('/S /D=' + (Join-Path $protected 'nested-install')) -WindowStyle Hidden -Wait -PassThru
if ($probe.ExitCode -ne 2 -or [IO.File]::ReadAllText($marker) -ne 'directory-guard-sentinel') { throw 'Installer did not refuse an owned learning directory.' }
Write-Output 'learning_directory_install_refused=true'
Install-TestPackage
Run-InstalledCase 'initial'
$credential = Join-Path $profile 'learning/secrets/database.json'
$credentialHash = (Get-FileHash -LiteralPath $credential -Algorithm SHA256).Hash
Run-InstalledCase 'restart'
if ($upgrade) { $package = $upgrade; $env:DESKTOP_TEST_INSTALLER = $package }
Install-TestPackage
Run-InstalledCase 'overwrite'
if ($upgrade) {
    $backups = @(Get-ChildItem -LiteralPath (Join-Path $profile 'learning/backups') -Filter manifest.json -Recurse -File)
    if (-not $backups.Count) { throw 'Cross-version upgrade did not produce its protected backup.' }
    Write-Output 'cross_version_upgrade_backup=true'
}
if ($BusinessRegression) {
    & node (Join-Path $PSScriptRoot 'business-smoke.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Installed business regression failed.' }
    Write-Output 'installed_business_regression=passed'
}
Uninstall-TestPackage
if ((Get-FileHash -LiteralPath $credential -Algorithm SHA256).Hash -ne $credentialHash) { throw 'Uninstall changed persistent credentials.' }
Install-TestPackage
Run-InstalledCase 'reinstall'
Uninstall-TestPackage
Write-Output ('installer_roundtrip=passed; persistent_data=' + $profile)
