#!/usr/bin/env bash
# Runs inside an already booted Android emulator. Does not uninstall or clear app data.
set -euo pipefail

previous_apk="${1:?Existing stable APK required}"
candidate_apk="${2:?New PR APK required}"
package="${3:?Package name required}"
category="${4:?Launcher category required}"

test -s "$previous_apk"
test -s "$candidate_apk"
[[ "$package" =~ ^com\.kovelyx\.(mobile|tv)\.debug$ ]]
[[ "$category" == 'android.intent.category.LAUNCHER' || "$category" == 'android.intent.category.LEANBACK_LAUNCHER' ]]

adb install -r "$previous_apk"
adb shell monkey -p "$package" -c "$category" 1
sleep 5

first_install_before="$(adb shell dumpsys package "$package" | grep -m1 'firstInstallTime=' | tr -d '\r')"
[[ -n "$first_install_before" ]] || { echo "::error::Date d'installation initiale non récupérable"; exit 1; }
adb shell am force-stop "$package"

# Android's in-place replacement must NOT use -d, -t, pm clear or uninstall:
# the installed app signature, package and user-data identity must be preserved.
adb install -r "$candidate_apk"
first_install_after="$(adb shell dumpsys package "$package" | grep -m1 'firstInstallTime=' | tr -d '\r')"
[[ "$first_install_before" == "$first_install_after" ]] || {
  echo "::error::Réinstallation inattendue au lieu de la mise à jour en place"
  exit 1
}

adb logcat -c
adb shell monkey -p "$package" -c "$category" 1
sleep 10
adb shell pidof "$package" >/dev/null || {
  echo "::error::Kovelyx ne redémarre pas après une mise à jour en place"
  exit 1
}
echo "PASS: installation signée, upgrade adb install -r, identité et date de première installation conservées."
echo "LIMIT: l'émulateur ne prouve pas une session Cloudflare sur TV physique ni les données d'un profil réel."
