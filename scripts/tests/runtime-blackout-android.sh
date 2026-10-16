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
if grep -Eq '0\.0\.0\.0|127\.0\.0\.1|::1' <<< "$OLD_DNS" && grep -qi 'api.nuvio.tv' /tmp/kovelyx-dns.log; then
 echo "DNS Nuvio repond avec une adresse locale de blocage ; requete presente dans les journaux."
elif grep -Eqi 'unknown host|not known|no address associated|Name or service not known' <<< "$OLD_DNS" && grep -qi 'api.nuvio.tv' /tmp/kovelyx-dns.log; then
 echo "DNS Nuvio rejete dans l'emulateur ; requete prouvee dans les journaux du DNS filtrant."
else
 echo "::error::DNS Nuvio non confirme dans l'emulateur : test invalide"
 exit 1
fi
# L'AVD peut annoncer son boot avant que sa pile reseau soit disponible.
OTHER_DNS=""
for attempt in 1 2 3 4 5 6 7; do
 OTHER_DNS="$(adb shell ping -4 -c 1 -W 2 api.github.com 2>&1 || true)"
 if grep -Eq '[1-9][0-9]*[.][0-9]+[.][0-9]+[.][0-9]+' <<< "$OTHER_DNS" &&
    ! grep -Eq '0[.]0[.]0[.]0|127[.]0[.]0[.]1' <<< "$OTHER_DNS"; then
  echo "GitHub DNS: $OTHER_DNS"
  break
 fi
 echo "Attente de la resolution DNS des services tiers ($attempt) : $OTHER_DNS"
 sleep 8
done
if ! grep -Eq '[1-9][0-9]*[.][0-9]+[.][0-9]+[.][0-9]+' <<< "$OTHER_DNS" ||
   grep -Eq '0[.]0[.]0[.]0|127[.]0[.]0[.]1' <<< "$OTHER_DNS"; then
 echo "::error::DNS tiers indisponible : test invalide, Nuvio seul ne peut pas etre isole"
 echo "=== Journal DNS de filtrage (dernières requêtes) ==="
 tail -n 60 /tmp/kovelyx-dns.log || true
 adb shell getprop net.dns1 || true
 adb shell getprop net.dns2 || true
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
