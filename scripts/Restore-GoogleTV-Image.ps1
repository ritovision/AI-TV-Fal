#Requires -Version 5.1
<#
.SYNOPSIS
  Explicitly redownload the Google TV system image to a selected storage drive.
.DESCRIPTION
  Only needed when the existing E: image is permanently inaccessible/corrupted
  and another drive with ~10GB+ of room becomes available.
  Never stores the 8GB image back on C: by default.
.EXAMPLE
  powershell.exe -ExecutionPolicy Bypass -File Restore-GoogleTV-Image.ps1 -StorageRoot "F:\AI-TV-Emulator" -Download
#>
[CmdletBinding()]
param(
    [string]$StorageRoot = 'E:\AI-TV-Emulator',
    [switch]$Download
)
$ErrorActionPreference = 'Stop'
$StorageRoot = [IO.Path]::GetFullPath($StorageRoot).TrimEnd('\')
$ImageDir = Join-Path $StorageRoot 'system-images\android-36\google-tv\x86_64'
$Image = Join-Path $ImageDir 'system.img'
$SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$SdkManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'

if ((Test-Path -LiteralPath $Image -PathType Leaf) -and
    (Get-Item -LiteralPath $Image).Length -gt 5000000000 -and
    (Test-Path -LiteralPath (Join-Path $ImageDir 'source.properties'))) {
    Write-Output "Google TV image already present at $ImageDir"
    Write-Output 'For integrity verification, compare hashes with docs/google-tv-image.sha256.'
    exit 0
}
if (-not $Download) {
    throw "Image missing or incomplete at $ImageDir. Pass -Download to explicitly fetch the 8.2 GB image."
}
$driveLetter = [IO.Path]::GetPathRoot($StorageRoot).Substring(0,1)
$drive = Get-PSDrive -Name $driveLetter -ErrorAction Stop
if ($drive.Free -lt 11000000000) {
    throw "Drive $driveLetter has less than 11 GB free. Choose a larger temporary drive."
}
if (-not (Test-Path -LiteralPath $SdkManager)) { throw "Android SDK manager missing on C: $SdkManager" }
New-Item -ItemType Directory -Force -Path $StorageRoot | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $StorageRoot 'licenses') | Out-Null

# Reuse accepted Android SDK licenses; otherwise SDK Manager may prompt.
$licenseSource = Join-Path $SdkRoot 'licenses'
if (Test-Path -LiteralPath $licenseSource) {
    Copy-Item -Path (Join-Path $licenseSource '*') -Destination (Join-Path $StorageRoot 'licenses') -Force
}
$StudioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
if (Test-Path -LiteralPath $StudioJdk) { $env:JAVA_HOME = $StudioJdk }

Write-Output "Downloading Google TV Android 36 x86_64 to $StorageRoot"
& $SdkManager "--sdk_root=$StorageRoot" 'system-images;android-36;google-tv;x86_64'
if ($LASTEXITCODE -ne 0) { throw "SDK download failed with exit code $LASTEXITCODE" }
if (-not (Test-Path -LiteralPath $Image -PathType Leaf)) { throw "System image missing after download: $Image" }
Write-Output "Download finished at $ImageDir"
Write-Output 'Next: run Configure-GoogleTV.ps1 -StorageRoot <same directory> -Repoint.'
