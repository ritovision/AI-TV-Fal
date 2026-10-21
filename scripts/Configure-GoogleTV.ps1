#Requires -Version 5.1
<#
.SYNOPSIS
  Provision or repair the AI-TV Google TV emulator without touching other AVDs.
.DESCRIPTION
  Expects the Google TV image to be present in StorageRoot (on a disposable drive).
  Keeps the Android SDK and AVD registry on Windows C: while locating the 8+ GB
  system image and all virtual device storage on StorageRoot.
  Neither this script nor the launch script automatically wipes an existing AVD.
.EXAMPLE
  powershell.exe -ExecutionPolicy Bypass -File Configure-GoogleTV.ps1
.EXAMPLE
  powershell.exe -ExecutionPolicy Bypass -File Configure-GoogleTV.ps1 -StorageRoot "X:\AI-TV-Emulator" -Repoint
#>
[CmdletBinding()]
param(
    [string]$StorageRoot = 'E:\AI-TV-Emulator',
    [switch]$Repoint
)
$ErrorActionPreference = 'Stop'

$AvdName = 'AI_TV_GoogleTV_API36'
$ImagePackage = 'system-images;android-36;google-tv;x86_64'
$ImageRelative = 'system-images\android-36\google-tv\x86_64'
$StorageRoot = [IO.Path]::GetFullPath($StorageRoot).TrimEnd('\')
$ImageOnDrive = Join-Path $StorageRoot $ImageRelative
$AvdOnDrive = Join-Path $StorageRoot ("avd\" + $AvdName + ".avd")
$SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$SdkImage = Join-Path $SdkRoot $ImageRelative
$SdkImageParent = Split-Path -Parent $SdkImage
$AvdHome = Join-Path $env:USERPROFILE '.android\avd'
$AvdIni = Join-Path $AvdHome ($AvdName + '.ini')
$AvdManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$Emulator = Join-Path $SdkRoot 'emulator\emulator.exe'

function Norm([string]$Value) {
    return [IO.Path]::GetFullPath($Value).TrimEnd('\')
}
function Update-AvdSetting([string]$ConfigFile, [string]$Name, [string]$Value) {
    $lines = @(Get-Content -LiteralPath $ConfigFile)
    $pattern = '^' + [regex]::Escape($Name) + '='
    $lines = @($lines | Where-Object { $_ -notmatch $pattern })
    $lines += "$Name=$Value"
    Set-Content -LiteralPath $ConfigFile -Value $lines -Encoding ASCII
}

# Never silently create a virtual disk on C: or allow an absent E: to look like
# an empty directory. The disk must be online with a fully installed image.
foreach ($name in @('system.img', 'vendor.img', 'source.properties', 'package.xml')) {
    if (-not (Test-Path -LiteralPath (Join-Path $ImageOnDrive $name) -PathType Leaf)) {
        throw "Google TV image is missing: $(Join-Path $ImageOnDrive $name). Restore the image on an available drive first."
    }
}
if ((Get-Item -LiteralPath (Join-Path $ImageOnDrive 'system.img')).Length -lt 5000000000) {
    throw 'Google TV system image appears truncated. Do not use this drive copy.'
}
if (-not (Test-Path -LiteralPath $AvdManager)) { throw "Missing Windows Android SDK avdmanager: $AvdManager" }
if (-not (Test-Path -LiteralPath $Emulator)) { throw "Missing Windows emulator: $Emulator" }

# The SDK link is only for AI-TV's Google TV package. Never delete a real
# directory or any unrelated SDK image. Repointing requires explicit approval.
$entry = @(Get-ChildItem -LiteralPath $SdkImageParent -Force | Where-Object { $_.Name -eq 'x86_64' } | Select-Object -First 1)
if ($entry.Count -gt 0) {
    $existing = $entry[0]
    if (($existing.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) {
        throw "SDK image path is a REAL folder: $SdkImage. Do not overwrite it. Migrate that directory and validate the copy first."
    }
    $linkTarget = @($existing.Target)[0]
    if ([string]::IsNullOrWhiteSpace($linkTarget)) {
        throw "Could not read existing junction target: $SdkImage"
    }
    if ((Norm $linkTarget) -ine (Norm $ImageOnDrive)) {
        if (-not $Repoint) {
            throw "SDK junction points to $linkTarget. To switch drives after restoring the image, rerun with -Repoint."
        }
        # rmdir on a Windows junction removes ONLY the junction, not target data.
        & cmd.exe /d /c ('rmdir "' + $SdkImage + '"')
        if ($LASTEXITCODE -ne 0) { throw "Unable to remove the stale SDK junction: $SdkImage" }
        New-Item -ItemType Junction -Path $SdkImage -Target $ImageOnDrive | Out-Null
    }
} else {
    New-Item -ItemType Junction -Path $SdkImage -Target $ImageOnDrive | Out-Null
}

# Keep only the tiny .ini AVD index in the Windows user profile on C:.
# The config.ini, userdata and snapshots live on StorageRoot, never on C:.
New-Item -ItemType Directory -Force -Path $AvdHome | Out-Null
$expectedConfig = Join-Path $AvdOnDrive 'config.ini'
$needsCreate = -not (Test-Path -LiteralPath $expectedConfig -PathType Leaf)

if (Test-Path -LiteralPath $AvdIni -PathType Leaf) {
    $indexLine = Get-Content -LiteralPath $AvdIni | Where-Object { $_ -like 'path=*' } | Select-Object -First 1
    if ($indexLine) {
        $indexed = $indexLine.Substring(5).Replace('\\', '\')
        if ((Norm $indexed) -ine (Norm $AvdOnDrive)) {
            if (-not $Repoint) {
                throw "The AI-TV AVD points to $indexed. Rerun with -Repoint to switch its index. Old data will be left alone."
            }
            $needsCreate = $needsCreate -or -not (Test-Path -LiteralPath $expectedConfig)
        }
    }
}

if ($needsCreate) {
    $env:ANDROID_SDK_ROOT = $SdkRoot
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_AVD_HOME = $AvdHome
    $StudioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $StudioJdk) { $env:JAVA_HOME = $StudioJdk }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $AvdOnDrive) | Out-Null

    Write-Output "Creating new disposable Google TV AVD at $AvdOnDrive"
    'no' | & $AvdManager create avd --name $AvdName --package $ImagePackage --path $AvdOnDrive --device tv_1080p --force
    if ($LASTEXITCODE -ne 0) { throw "Android avdmanager failed ($LASTEXITCODE)" }
    if (-not (Test-Path -LiteralPath $expectedConfig)) { throw "avdmanager did not create $expectedConfig" }
} elseif ((-not (Test-Path -LiteralPath $AvdIni -PathType Leaf)) -or $Repoint) {
    # Recreate the small, portable index on C: without touching the device data.
    $escaped = $AvdOnDrive.Replace('\', '\\')
    @('avd.ini.encoding=UTF-8', "path=$escaped", 'target=android-36') |
        Set-Content -LiteralPath $AvdIni -Encoding ASCII
}

# Bounded default footprint and no Quick Boot dependence on flaky media.
Update-AvdSetting $expectedConfig 'hw.ramSize' '2048'
Update-AvdSetting $expectedConfig 'disk.dataPartition.size' '4096M'
Update-AvdSetting $expectedConfig 'hw.keyboard' 'yes'
Update-AvdSetting $expectedConfig 'hw.initialOrientation' 'landscape'
Update-AvdSetting $expectedConfig 'firstboot.bootFromDownloadableSnapshot' 'no'
Update-AvdSetting $expectedConfig 'firstboot.bootFromLocalSnapshot' 'no'
Update-AvdSetting $expectedConfig 'firstboot.saveToLocalSnapshot' 'no'
Update-AvdSetting $expectedConfig 'fastboot.forceColdBoot' 'yes'
Update-AvdSetting $expectedConfig 'fastboot.forceFastBoot' 'no'

$detected = @(& $Emulator -list-avds 2>&1)
if (($detected | Where-Object { $_.Trim() -eq $AvdName }).Count -eq 0) {
    throw "The Windows emulator cannot see the AI-TV AVD. Output: $($detected -join '; ')"
}
Write-Output "READY: $AvdName"
Write-Output "Image: $ImageOnDrive"
Write-Output "AVD data: $AvdOnDrive"
Write-Output "Persistent AVD pointer: $AvdIni"
Write-Output 'No emulator was launched; no unrelated emulator was changed.'
