#Requires -Version 5.1
<#
.SYNOPSIS
  Start ONLY the AI-TV Google TV virtual device. Existing emulators are untouched.
.DESCRIPTION
  Checks the disposable storage drive before launch, disables Quick Boot
  snapshots, and does NOT wipe app data unless you explicitly choose to later.
#>
[CmdletBinding()]
param(
    [string]$StorageRoot = 'E:\AI-TV-Emulator',
    [switch]$Headless
)
$ErrorActionPreference = 'Stop'
$AvdName = 'AI_TV_GoogleTV_API36'
$Image = Join-Path $StorageRoot 'system-images\android-36\google-tv\x86_64\system.img'
$AvdFile = Join-Path $StorageRoot ("avd\" + $AvdName + '.avd\config.ini')
$Emulator = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
$AvdIndex = Join-Path $env:USERPROFILE ('.android\avd\' + $AvdName + '.ini')

if (-not (Test-Path -LiteralPath $Image -PathType Leaf)) {
    throw "Google TV image missing or storage drive offline: $Image. Nothing launched."
}
if (-not (Test-Path -LiteralPath $AvdFile -PathType Leaf)) {
    throw "Google TV virtual device not provisioned or storage drive offline: $AvdFile"
}
if (-not (Test-Path -LiteralPath $AvdIndex -PathType Leaf)) {
    throw "Missing AVD index on C:. Run Configure-GoogleTV.ps1 first."
}
if (-not (Test-Path -LiteralPath $Emulator -PathType Leaf)) {
    throw "Windows emulator missing: $Emulator"
}

# No wipe-data. On E: disconnect, this instance can crash/corrupt the
# expendable device state, but our Git repo on C: is not affected.
$argsList = @('-avd', $AvdName, '-no-snapshot', '-no-boot-anim', '-gpu', 'auto')
if ($Headless) { $argsList += '-no-window' }

$process = Start-Process -FilePath $Emulator -ArgumentList $argsList -PassThru
Write-Output "Started $AvdName (process ID $($process.Id))."
Write-Output "Storage is on $StorageRoot; save important outputs to C: before E: disconnects."
