# Creates the release signing key and keystore.properties in one step.
#
# Asks for the keystore password (hidden while typing), then writes it into keystore.properties
# (gitignored) so release builds can sign without asking again. The password is never printed.
#
# Back up BOTH files afterwards (password manager + one offline copy): every future update of the
# app must be signed with this exact key, and a lost key means shipping as a new app again.
#
# Usage, from the project root:
#   powershell -ExecutionPolicy Bypass -File tools/create-release-key.ps1

$ErrorActionPreference = "Stop"

$keyDir = Join-Path $env:USERPROFILE "keys"
$keystore = Join-Path $keyDir "ondevice-studio-release.jks"
$alias = "ondevice-studio"
$propsFile = Join-Path (Split-Path $PSScriptRoot -Parent) "keystore.properties"

if (Test-Path $keystore) {
    Write-Host "A key already exists at $keystore. Not overwriting it." -ForegroundColor Yellow
    exit 1
}

$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    Write-Host "keytool not found. Install a JDK (17+) or add its bin folder to PATH." -ForegroundColor Red
    exit 1
}

function Read-Password([string]$prompt) {
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

while ($true) {
    $password = Read-Password "Choose a keystore password (6+ characters)"
    if ($password.Length -lt 6) { Write-Host "Too short; Android needs at least 6 characters." -ForegroundColor Yellow; continue }
    # keystore.properties is a Java properties file, where a backslash starts an escape sequence.
    if ($password.Contains('\')) { Write-Host "Please avoid backslashes (\) in the password." -ForegroundColor Yellow; continue }
    if ($password -ne (Read-Password "Type it again")) { Write-Host "Passwords didn't match; try again." -ForegroundColor Yellow; continue }
    break
}

New-Item -ItemType Directory -Force -Path $keyDir | Out-Null

& $keytool -genkeypair -keystore $keystore -alias $alias `
    -keyalg RSA -keysize 4096 -validity 36500 `
    -dname "CN=OnDevice Studio, O=OnDevice Studio" `
    -storepass $password -keypass $password | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "keytool failed; no key was created." -ForegroundColor Red
    exit 1
}

$storePath = $keystore.Replace('\', '/')
@"
# Release signing for OnDevice Studio. Gitignored - never commit this file.
storeFile=$storePath
storePassword=$password
keyAlias=$alias
keyPassword=$password
"@ | Set-Content -Path $propsFile -Encoding ascii

Write-Host "Created $keystore"
Write-Host "Saved its password in $propsFile"
Write-Host ""
Write-Host "Now back up both files (e.g. attach them to a password-manager entry, plus one offline copy)." -ForegroundColor Yellow
