param([string]$OutputRoot = '.runtime/desktop-bundle-d2')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$destination = [IO.Path]::GetFullPath((Join-Path $workspace $OutputRoot))
$allowed = [IO.Path]::GetFullPath((Join-Path $workspace '.runtime')) + [IO.Path]::DirectorySeparatorChar
if (-not $destination.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $destination)) { throw 'Choose a new directory inside workspace .runtime.' }
$spec = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'runtime.lock.json') -Raw | ConvertFrom-Json
if ($spec.platform -ne 'win32-x64' -or -not [Environment]::Is64BitOperatingSystem) { throw 'Windows x64 runtime required.' }
foreach ($item in @($spec.java,$spec.postgres)) {
    $archive = Join-Path $workspace $item.cache
    if (-not (Test-Path -LiteralPath $archive)) {
        [IO.Directory]::CreateDirectory((Split-Path -Parent $archive)) | Out-Null
        $temporary = $archive + '.' + [guid]::NewGuid().ToString('N') + '.download'
        $options = @{ Uri=$item.url; OutFile=$temporary; UseBasicParsing=$true; TimeoutSec=300 }
        if ($env:HTTPS_PROXY) { $options.Proxy=$env:HTTPS_PROXY }
        Invoke-WebRequest @options
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) { throw 'Downloaded runtime checksum differs.' }
        Move-Item -LiteralPath $temporary -Destination $archive
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) { throw 'Cached runtime checksum differs.' }
}
$jar = Join-Path $workspace 'backend/target/scarlet-nexus-0.1.0-SNAPSHOT.jar'
$assets = Join-Path $workspace 'frontend/dist'
if (-not (Test-Path -LiteralPath $jar) -or -not (Test-Path -LiteralPath (Join-Path $assets 'index.html'))) { throw 'Build the real backend JAR and frontend first.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$backendArchive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    if (-not $backendArchive.GetEntry('BOOT-INF/classes/dev/scarletnexus/desktop/DesktopApplication.class')) { throw 'Build the backend desktop control entry first.' }
} finally { $backendArchive.Dispose() }
[IO.Directory]::CreateDirectory($destination) | Out-Null
Expand-Archive -LiteralPath (Join-Path $workspace $spec.java.cache) -DestinationPath (Join-Path $destination 'java-archive')
$javaRoot = @(Get-ChildItem -LiteralPath (Join-Path $destination 'java-archive') -Directory)
if ($javaRoot.Count -ne 1) { throw 'Unexpected Java archive layout.' }
$javaSource = [IO.Path]::GetFullPath($javaRoot[0].FullName)
$javaDestination = [IO.Path]::GetFullPath((Join-Path $destination 'java'))
if (-not $javaSource.StartsWith($destination + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    -not $javaDestination.StartsWith($destination + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Java staging move must stay inside the new runtime directory.' }
Move-Item -LiteralPath $javaSource -Destination $javaDestination
$pgRoot = Join-Path $destination 'postgres'
[IO.Directory]::CreateDirectory($pgRoot) | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $workspace $spec.postgres.cache))
try {
    foreach ($entry in $zip.Entries) {
        # Keep the server/client runtime and its documentation/licences; omit
        # unrelated pgAdmin, StackBuilder and development headers.
        if ($entry.FullName -notmatch '^pgsql/(bin|lib|share|doc)/' -and $entry.FullName -notmatch '^pgsql/[^/]+$') { continue }
        if ($entry.FullName.EndsWith('/')) { continue }
        $relative = $entry.FullName.Substring('pgsql/'.Length)
        $target = [IO.Path]::GetFullPath((Join-Path $pgRoot $relative))
        if (-not $target.StartsWith($pgRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe runtime archive path.' }
        [IO.Directory]::CreateDirectory((Split-Path -Parent $target)) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$false)
    }
} finally { $zip.Dispose() }
if (-not (Test-Path -LiteralPath (Join-Path $pgRoot 'bin/initdb.exe'))) { throw 'Unexpected PostgreSQL archive layout.' }
Copy-Item -LiteralPath $jar -Destination (Join-Path $destination 'backend.jar')
Copy-Item -LiteralPath $assets -Destination (Join-Path $destination 'assets') -Recurse
Copy-Item -LiteralPath (Join-Path $workspace 'scripts/provision-database-roles.sql') -Destination (Join-Path $destination 'roles.sql')
$files = [ordered]@{}
Get-ChildItem -LiteralPath $destination -File -Recurse | Sort-Object FullName | ForEach-Object {
    $name = $_.FullName.Substring($destination.Length+1).Replace('\','/')
    $files[$name] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
$schemaVersion = (Get-ChildItem -LiteralPath (Join-Path $workspace 'backend/src/main/resources/db/migration') -File | Where-Object {$_.Name -match '^V([0-9]+)__'} | ForEach-Object {[int]([regex]::Match($_.Name,'^V([0-9]+)__').Groups[1].Value)} | Measure-Object -Maximum).Maximum
$manifest = @{schema=1; platform='win32-x64'; release='0.1.0-d2'; postgresMajor='17'; schemaVersion=[string]$schemaVersion; files=$files}
[IO.File]::WriteAllText((Join-Path $destination 'manifest.json'),($manifest | ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
Write-Output ('Assembled verified desktop runtime: '+$destination)
