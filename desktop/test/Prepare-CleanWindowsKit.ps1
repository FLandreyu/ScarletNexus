param([string]$OutputRoot = '.runtime/desktop-clean-windows-kit',[switch]$CreateIso)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$boundary = [IO.Path]::GetFullPath((Join-Path $workspace '.runtime')) + [IO.Path]::DirectorySeparatorChar
$output = [IO.Path]::GetFullPath((Join-Path $workspace $OutputRoot))
if (-not $output.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)) { throw 'Choose a new workspace .runtime directory.' }
$spec = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'clean-windows-kit.lock.json') -Raw | ConvertFrom-Json
foreach ($item in $spec.installers) {
    $source = [IO.Path]::GetFullPath((Join-Path $workspace $item.path))
    if (-not $source.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase) -or (Get-Item -LiteralPath $source).Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe installer source.' }
    if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) { throw 'Installer source checksum differs.' }
}
$media = Join-Path $output 'media'
[IO.Directory]::CreateDirectory($media) | Out-Null
foreach ($item in $spec.installers) { Copy-Item -LiteralPath (Join-Path $workspace $item.path) -Destination $media }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Audit-WindowsEnvironment.ps1') -Destination $media
Copy-Item -LiteralPath (Join-Path $workspace 'docs/DESKTOP_TEST_ENVIRONMENT.md') -Destination (Join-Path $media 'TEST-ENVIRONMENT.txt')
Copy-Item -LiteralPath (Join-Path $workspace 'docs/DESKTOP_RECOVERY.md') -Destination (Join-Path $media 'RECOVERY.txt')
Copy-Item -LiteralPath (Join-Path $workspace 'docs/DESKTOP_GUEST_CHECKLIST.md') -Destination (Join-Path $media 'CHECKLIST.txt')
$files = [ordered]@{}
Get-ChildItem -LiteralPath $media -File | Sort-Object Name | ForEach-Object { $files[$_.Name]=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
$manifest = @{schema=1; purpose='manual clean Windows D3 acceptance'; validationStatus='NOT_RUN'; installers=$spec.installers; files=$files}
[IO.File]::WriteAllText((Join-Path $media 'manifest.json'),($manifest | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
$sums = @($files.GetEnumerator() | ForEach-Object { $_.Value+'  '+$_.Key }) -join "`n"
[IO.File]::WriteAllText((Join-Path $media 'SHA256SUMS.txt'),$sums+"`n",[Text.UTF8Encoding]::new($false))
if ($CreateIso) {
    # Uses Windows' existing IMAPI COM interface; installs no ISO tooling.
    if (-not ('ScarletIsoStream' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
public static class ScarletIsoStream {
 public static void Save(object input, string target) {
  IStream stream=(IStream)input; IntPtr count=Marshal.AllocHGlobal(4);
  try { using(FileStream output=new FileStream(target,FileMode.CreateNew,FileAccess.Write)) {
   byte[] buffer=new byte[65536];
   while(true) { stream.Read(buffer,buffer.Length,count); int length=Marshal.ReadInt32(count); if(length==0) break; output.Write(buffer,0,length); }
   output.Flush(true);
  }} finally { Marshal.FreeHGlobal(count); }
 }
}
'@
    }
    $image = New-Object -ComObject IMAPI2FS.MsftFileSystemImage
    $image.FileSystemsToCreate = 3
    $image.VolumeName = 'SCARLET_D3_TEST'
    $image.Root.AddTree($media,$false)
    $result = $image.CreateResultImage()
    $iso = Join-Path $output 'ScarletNexus-D3-offline-test.iso'
    [ScarletIsoStream]::Save($result.ImageStream,$iso)
    [IO.File]::WriteAllText((Join-Path $output 'ISO-SHA256SUMS.txt'),(Get-FileHash -LiteralPath $iso -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+[IO.Path]::GetFileName($iso)+"`n",[Text.UTF8Encoding]::new($false))
}
Write-Output ('Prepared test media only; no installation or D3 execution: '+$output)
