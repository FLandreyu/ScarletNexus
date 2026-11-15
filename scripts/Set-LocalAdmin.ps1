param(
    [Parameter(Mandatory=$true)][string]$Email,
    [Security.SecureString]$Password,
    [ValidatePattern('^[A-Za-z0-9._-]{1,80}$')][string]$FileName = 'admin.password'
)
$ErrorActionPreference = 'Stop'
$normalizedEmail = $Email.Trim().ToLowerInvariant()
$address = [System.Net.Mail.MailAddress]::new($normalizedEmail)
if ($address.Address -ne $normalizedEmail -or $normalizedEmail.Length -gt 254 -or $normalizedEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'Provide a valid email address.'
}
if (-not $Password) { $Password = Read-Host 'Administrator password (12+ characters, up to 72 UTF-8 bytes)' -AsSecureString }
$directory = Join-Path (Split-Path -Parent $PSScriptRoot) '.runtime/dev-secrets'
[IO.Directory]::CreateDirectory($directory) | Out-Null
$target = Join-Path $directory $FileName
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
try {
    $plaintext = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ($plaintext.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($plaintext) -gt 72 -or $plaintext -ne $plaintext.Trim() -or $plaintext.Contains("`n") -or $plaintext.Contains("`r")) {
        throw 'Password must be one line, 12+ characters, at most 72 UTF-8 bytes, without surrounding whitespace.'
    }
    $stream = [IO.File]::Open($target, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($plaintext)
        $stream.Write($bytes, 0, $bytes.Length)
        [Array]::Clear($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    Remove-Variable plaintext -ErrorAction SilentlyContinue
}
Write-Host "Created a local bootstrap password file: $target"
Write-Host 'Set these non-secret values in .env, preserving its existing database password:'
Write-Host "ADMIN_EMAIL=$normalizedEmail"
Write-Host "ADMIN_PASSWORD_FILE=/run/secrets/$FileName"
Write-Host 'An existing administrator will never be reset by this bootstrap configuration.'
