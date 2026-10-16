#!/usr/bin/env bash
# Test APK avec DNS Nuvio neutralisé à l'intérieur de l'émulateur.
# Périmètre : lancement, navigation, survie du processus, résolution DNS.
set -Eeuo pipefail
MODE="${1:?mode mobile ou tv obligatoire}"
case "$MODE" in
 mobile) PACKAGE="com.kovelyx.mobile.debug";;
 tv) PACKAGE="com.kovelyx.tv.debug";;
 *) echo "Mode invalide: $MODE" >&2; exit 2;;
esac
mkdir -p /tmp/kovelyx-blackout-proof
exec > >(tee "/tmp/kovelyx-blackout-proof/$MODE.txt") 2>&1
echo "=== Test Kovelyx $MODE avec Nuvio bloqué ==="
echo "Release: $KOVELYX_RELEASE_TAG"
adb wait-for-device
adb shell settings put global private_dns_mode off
OLD_DNS="$(adb shell ping -c 1 -W 2 api.nuvio.tv 2>&1 || true)"
echo "Nuvio DNS: $OLD_DNS"
if ! grep -q '0\.0\.0\.0' <<< "$OLD_DNS"; then
 echo "::error::DNS Nuvio PAS bloqué dans l'émulateur : test invalide"
 exit 1
fi
OTHER_DNS="$(adb shell ping -c 1 -W 2 api.github.com 2>&1 || true)"
echo "GitHub DNS: $OTHER_DNS"
if ! grep -Eq '[1-9][0-9]*\.[0-9]+\.[0-9]+\.[0-9]+' <<< "$OTHER_DNS"; then
 echo "::error::Résolution DNS des autres services absente"
 exit 1
fi
if grep -Eq '0\.0\.0\.0|127\.0\.0\.1' <<< "$OTHER_DNS"; then
 echo "::error::DNS tiers bloqué aussi ; test invalide"
 exit 1
fi
APK="$(find /tmp/kovelyx-runtime-apk -type f -name '*.apk' -print -quit)"
test -s "$APK"
sha256sum "$APK" | tee "/tmp/kovelyx-blackout-proof/$MODE.apk.sha256"
adb install -r "$APK"
adb logcat -c
if [[ "$MODE" == tv ]]; then
 adb shell monkey -p "$PACKAGE" -c android.intent.category.LEANBACK_LAUNCHER 1
else
 adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1
fi
sleep 25
adb shell pidof "$PACKAGE" || { echo "::error::Application arrêtée au démarrage"; exit 1; }
adb shell screencap -p "/sdcard/$MODE-first.png"
adb pull "/sdcard/$MODE-first.png" "/tmp/kovelyx-blackout-proof/$MODE-first.png"
if [[ "$MODE" == tv ]]; then
 for key in KEYCODE_DPAD_RIGHT KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER KEYCODE_BACK KEYCODE_DPAD_LEFT KEYCODE_DPAD_CENTER; do
  adb shell input keyevent "$key"
  sleep 1
 done
else
 adb shell input keyevent KEYCODE_DPAD_DOWN
 adb shell input keyevent KEYCODE_DPAD_RIGHT
 adb shell input keyevent KEYCODE_BACK
fi
sleep 8
adb shell pidof "$PACKAGE" || { echo "::error::Application arrêtée pendant la navigation"; exit 1; }
adb shell screencap -p "/sdcard/$MODE-nav.png"
adb pull "/sdcard/$MODE-nav.png" "/tmp/kovelyx-blackout-proof/$MODE-nav.png"
adb logcat -d -v time > "/tmp/kovelyx-blackout-proof/$MODE-logcat.txt"
if grep -A 22 -E 'FATAL EXCEPTION|Fatal signal|Process: com\.kovelyx\.' "/tmp/kovelyx-blackout-proof/$MODE-logcat.txt" | grep -F -e "Process: $PACKAGE" -e ">>> $PACKAGE <<<" >/dev/null; then
 echo "::error::Crash de Kovelyx détecté dans les logs Android"
 exit 1
fi
echo "PASS : Nuvio DNS bloqué, DNS GitHub disponible, APK installé, navigation de base sans crash."
echo "LIMITES : tests P2P, addons et API avec clés réelles toujours distincts."
