#Requires -Version 5.1
<#
.SYNOPSIS
  Reset ONLY the disposable AI-TV Google TV device after a corrupt E: disconnect.
.DESCRIPTION
  Requires -Reset. Removes this one AVD's on-drive state and recreates its
  configuration via Configure-GoogleTV.ps1. Does not delete or alter other AVDs,
  source files, installed Google TV system images or their SDK junction.
  Any fal.ai login state in this disposable virtual device will be lost.
#>
[CmdletBinding()]
param(
    [string]$StorageRoot = 'E:\AI-TV-Emulator',
    [switch]$Reset
)
$ErrorActionPreference = 'Stop'
if (-not $Reset) {
    throw 'This deletes disposable AI-TV virtual-device data. Rerun with -Reset if you want a fresh device.'
}
$Name = 'AI_TV_GoogleTV_API36'
$StorageRoot = [IO.Path]::GetFullPath($StorageRoot).TrimEnd('\')
$DeviceDir = Join-Path $StorageRoot ("avd\" + $Name + ".avd")
$Image = Join-Path $StorageRoot 'system-images\android-36\google-tv\x86_64\system.img'

if (-not (Test-Path -LiteralPath $Image -PathType Leaf)) { throw "Storage/image missing: $Image" }
$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -in @('emulator.exe', 'qemu-system-x86_64.exe', 'qemu-system-i386.exe') -and
    $_.CommandLine -match [regex]::Escape($Name)
})
if ($running.Count -gt 0) {
    throw "The AI-TV emulator appears to be running (PID $($running[0].ProcessId)); stop it normally before a reset."
}
if (Test-Path -LiteralPath $DeviceDir) {
    $device = Get-Item -LiteralPath $DeviceDir -Force
    if (($device.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "AVD directory is unexpectedly a reparse point. Refusing to delete: $DeviceDir"
    }
    Remove-Item -LiteralPath $DeviceDir -Recurse -Force
}
$ConfigScript = Join-Path $PSScriptRoot 'Configure-GoogleTV.ps1'
& $ConfigScript -StorageRoot $StorageRoot
if (-not (Test-Path -LiteralPath (Join-Path $DeviceDir 'config.ini'))) {
    throw 'Reset attempted but configuration was not recreated.'
}
Write-Output "Reset completed for $Name. No other AVD was altered."
