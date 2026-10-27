#!/usr/bin/env bash
# Signe chaque APK de préversion (.debug) avec la MEME clé GitHub permanente.
# Les APK déjà installés avec une ancienne clé Android Debug nécessitent une
# réinstallation unique. Les versions suivantes conservent le même certificat.
set -euo pipefail
platform="${1:?Plateforme mobile ou tv requise}"
case "$platform" in
  mobile)
    output="apps/mobile/androidApp/build/outputs/apk/full/debug"
    app_id="com.kovelyx.mobile.debug"
    ;;
  tv)
    output="apps/tv/app/build/outputs/apk/full/debug"
    app_id="com.kovelyx.tv.debug"
    ;;
  *)
    echo "::error::Plateforme non reconnue"
    exit 1
    ;;
esac

: "${KOVELYX_RELEASE_STORE_FILE:?Clé permanente non restaurée}"
: "${KOVELYX_RELEASE_STORE_PASSWORD:?Mot de passe de la clé manquant}"
: "${KOVELYX_RELEASE_KEY_PASSWORD:?Mot de passe de l'alias manquant}"
: "${KOVELYX_RELEASE_KEY_ALIAS:?Alias de signature manquant}"

mapfile -d '' apks < <(find "$output" -maxdepth 1 -type f -name '*.apk' -print0)
if [[ "${#apks[@]}" -ne 1 ]]; then
  echo "::error::Un seul APK attendu avant signature Kovelyx : ${#apks[@]}"
  exit 1
fi
apk="${apks[0]}"
tools="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
tmp="$RUNNER_TEMP/kovelyx-${platform}-signed-debug.apk"
"$tools/apksigner" sign \
  --ks "$KOVELYX_RELEASE_STORE_FILE" \
  --ks-key-alias "$KOVELYX_RELEASE_KEY_ALIAS" \
  --ks-pass env:KOVELYX_RELEASE_STORE_PASSWORD \
  --key-pass env:KOVELYX_RELEASE_KEY_PASSWORD \
  --out "$tmp" "$apk"
"$tools/apksigner" verify --verbose "$tmp" >/dev/null
"$tools/aapt" dump badging "$tmp" | grep -F "package: name='$app_id'"
mv -f "$tmp" "$apk"
echo "APK de préversion ${platform} : certificat Kovelyx permanent, identité $app_id." >> "$GITHUB_STEP_SUMMARY"
