[CmdletBinding()]
param(
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.toread\signing'),
    [string]$KeyAlias = 'toread-release'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function New-UrlSafeSecret {
    param([int]$ByteCount = 32)

    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$keytool = Get-Command keytool.exe -ErrorAction Stop
$keyStorePath = Join-Path $SigningDirectory 'toread-release.jks'
$propertiesPath = Join-Path $SigningDirectory 'release.properties'
$certificatePath = Join-Path $SigningDirectory 'toread-release.cer'
$filingInfoPath = Join-Path $SigningDirectory 'filing-info.txt'
$targetPaths = @($keyStorePath, $propertiesPath, $certificatePath, $filingInfoPath)
$existingPaths = @($targetPaths | Where-Object { Test-Path -LiteralPath $_ })

if ($existingPaths.Count -gt 0) {
    throw "Release signing material already exists. Refusing to replace: $($existingPaths -join ', ')"
}

$null = New-Item -ItemType Directory -Path $SigningDirectory -Force
$storePassword = New-UrlSafeSecret
$keyPassword = New-UrlSafeSecret

& $keytool.Source `
    -genkeypair `
    -noprompt `
    -keystore $keyStorePath `
    -storetype JKS `
    -storepass $storePassword `
    -alias $KeyAlias `
    -keypass $keyPassword `
    -keyalg RSA `
    -keysize 4096 `
    -sigalg SHA256withRSA `
    -validity 36500 `
    -dname 'CN=Toread Android Release,O=Toread'
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed to generate the Toread release key (exit $LASTEXITCODE)."
}

$normalizedKeyStorePath = $keyStorePath.Replace('\', '/')
$propertiesLines = @(
    "storeFile=$normalizedKeyStorePath",
    "storePassword=$storePassword",
    "keyAlias=$KeyAlias",
    "keyPassword=$keyPassword"
)
$utf8WithoutBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($propertiesPath, $propertiesLines, $utf8WithoutBom)

& $keytool.Source `
    -exportcert `
    -keystore $keyStorePath `
    -storepass $storePassword `
    -alias $KeyAlias `
    -file $certificatePath
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed to export the Toread release certificate (exit $LASTEXITCODE)."
}

$certificate = [Security.Cryptography.X509Certificates.X509Certificate2]::new($certificatePath)
$md5 = [Security.Cryptography.MD5]::Create()
$sha256 = [Security.Cryptography.SHA256]::Create()
$rsa = [Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPublicKey($certificate)
try {
    $md5Fingerprint = (($md5.ComputeHash($certificate.RawData) | ForEach-Object { $_.ToString('X2') }) -join '')
    $sha256Fingerprint = (($sha256.ComputeHash($certificate.RawData) | ForEach-Object { $_.ToString('X2') }) -join '')
    $rsaModulus = (($rsa.ExportParameters($false).Modulus | ForEach-Object { $_.ToString('X2') }) -join '')
} finally {
    $rsa.Dispose()
    $md5.Dispose()
    $sha256.Dispose()
    $certificate.Dispose()
}

$filingLines = @(
    'Application ID=cn.toreadapp.reader',
    "Key alias=$KeyAlias",
    "RSA modulus (public key, hexadecimal)=$rsaModulus",
    "Certificate MD5 (hexadecimal)=$md5Fingerprint",
    "Certificate SHA-256 (hexadecimal)=$sha256Fingerprint"
)
[IO.File]::WriteAllLines($filingInfoPath, $filingLines, $utf8WithoutBom)

if ($env:OS -eq 'Windows_NT') {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $SigningDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to restrict permissions on $SigningDirectory."
    }
    foreach ($targetPath in $targetPaths) {
        & icacls.exe $targetPath /inheritance:r /grant:r "${identity}:F" 'SYSTEM:F' | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to restrict permissions on $targetPath."
        }
    }
}

Write-Output 'Toread release signing material created without printing any password.'
Write-Output "Keystore: $keyStorePath"
Write-Output "Gradle properties: $propertiesPath"
Write-Output "Filing information: $filingInfoPath"
Write-Output 'Before filing or publishing, copy the keystore and properties file to two separate encrypted offline backups.'
