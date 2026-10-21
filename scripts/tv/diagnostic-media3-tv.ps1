# Kovelyx TV / Media3 — hardware diagnostics without changing the device
# Windows PowerShell 5.1+; requires platform-tools (adb) in PATH.
# Does NOT install/reinstall APKs, erase application data, clear logcat or change settings.
[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDir = "",
    [switch]$SkipPlayback
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb est introuvable. Installer Android SDK Platform-Tools et ajouter adb au PATH."
}
$deviceRows = @(adb devices -l | Select-Object -Skip 1 | Where-Object { $_ -match '^([^\s]+)\s+device\s' })
if ($LASTEXITCODE -ne 0) { throw "Impossible de communiquer avec ADB." }
if (-not $Serial) {
    if ($deviceRows.Count -ne 1) {
        throw "Brancher une seule Android TV en ADB Wi-Fi (ou spécifier -Serial). Appareils prêts : $($deviceRows.Count)."
    }
    $Serial = ([regex]::Match($deviceRows[0], '^([^\s]+)')).Groups[1].Value
}
if ($Serial -notmatch '^[\w.:-]+$') { throw "Identifiant ADB invalide." }
& adb -s $Serial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Android TV ADB non joignable." }
$leanback = @(& adb -s $Serial shell pm list features | Select-String -Pattern 'android\.software\.leanback|android\.hardware\.type\.television')
if (-not $leanback) { throw "L'appareil ne déclare pas être une Android TV / Google TV. Aucune modification effectuée." }
$packages = @(& adb -s $Serial shell pm list packages 'com.kovelyx.tv.debug')
if (-not ($packages -match 'package:com\.kovelyx\.tv\.debug')) {
    throw "Kovelyx TV Debug n'est pas installé. Installer l'APK de la Release correspondant à la signature actuelle SANS désinstaller l'application existante."
}
if (-not $OutputDir) {
    $OutputDir = Join-Path (Get-Location).Path ("Kovelyx-TV-Diagnostic-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
"Serial=$Serial" | Set-Content (Join-Path $OutputDir "device.txt") -Encoding UTF8
"Date=$(Get-Date -Format o)" | Add-Content (Join-Path $OutputDir "device.txt")
& adb -s $Serial shell getprop ro.build.version.release | Add-Content (Join-Path $OutputDir "device.txt")
& adb -s $Serial shell getprop ro.product.model | Add-Content (Join-Path $OutputDir "device.txt")
& adb -s $Serial shell dumpsys package com.kovelyx.tv.debug |
    Select-String -Pattern 'versionName=|versionCode=|firstInstallTime=|lastUpdateTime=' |
    Add-Content (Join-Path $OutputDir "device.txt")
Write-Host "Diagnostic : $OutputDir"
Write-Host "1. Ouvrir Kovelyx sur la TV."
Write-Host "2. Lire une vidéo de test que vous êtes autorisé à regarder, puis utiliser pause/reprise et revenir au menu."
if (-not $SkipPlayback) {
    Read-Host "Quand le test TV est terminé, appuyer sur Entrée pour enregistrer les journaux" | Out-Null
}
& adb -s $Serial logcat -d -v threadtime -t 4000 -s 'ExoPlayer:*' 'MediaCodec:*' 'Media3:*' 'ExoPerformance:*' 'AndroidRuntime:E' '*:S' |
    Out-File (Join-Path $OutputDir "media3-logcat.txt") -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw "Échec de lecture des journaux ADB." }
"Résultat ressenti : noter démarrage, gel d'image, son, recherche, sortie et crash." |
    Set-Content (Join-Path $OutputDir "observations.txt") -Encoding UTF8
Write-Host "Terminé. Aucun fichier du téléphone/TV ni compte utilisateur n'a été modifié."
Write-Warning "Les logs peuvent contenir des informations personnelles (URLs ou identifiants). Relire et masquer avant partage. AUCUN envoi automatique."
