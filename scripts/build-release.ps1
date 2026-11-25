param(
    [ValidatePattern('^$|^[A-Za-z0-9-]+/Konlyut-Currency-Converter$')]
    [string]$Repository = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$keyFile = 'D:\Android\Signing\konlyut-release.p12'
$alias = 'konlyut-release'
$javaHomeForRelease = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\Android\jdk\jdk-21.0.13.1+2' }
$keytool = Join-Path $javaHomeForRelease 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'JDK 21 keytool not found. Set JAVA_HOME.' }

if (-not (Test-Path -LiteralPath $keyFile)) {
    New-Item -ItemType Directory -Path (Split-Path $keyFile) -Force | Out-Null
    Write-Host 'Creating the permanent Release key. Enter its password only at the hidden keytool prompt.'
    & $keytool -genkeypair -storetype PKCS12 -keystore $keyFile -alias $alias -keyalg RSA -keysize 3072 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw 'Key creation failed.' }
}

$secret = Read-Host 'Release keystore password (hidden input)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:KONLYUT_SIGNING_STORE_FILE = $keyFile
    $env:KONLYUT_SIGNING_KEY_ALIAS = $alias
    $env:KONLYUT_SIGNING_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:KONLYUT_SIGNING_KEY_PASSWORD = $env:KONLYUT_SIGNING_STORE_PASSWORD
    $arguments = @('assembleRelease', '--console=plain')
    if ($Repository) { $arguments += "-Pkonlyut.updateRepository=$Repository" }
    & (Join-Path $projectRoot 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    Write-Host "Signed APK: $projectRoot\app\build\outputs\apk\release\app-release.apk"
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    Remove-Item Env:KONLYUT_SIGNING_STORE_PASSWORD, Env:KONLYUT_SIGNING_KEY_PASSWORD, Env:KONLYUT_SIGNING_STORE_FILE, Env:KONLYUT_SIGNING_KEY_ALIAS -ErrorAction SilentlyContinue
}
