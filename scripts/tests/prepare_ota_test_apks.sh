#!/usr/bin/env bash
# PR-only mechanical upgrade rehearsal. Do NOT use this signer for releases.
# GitHub does not expose KOVELYX_ANDROID_SIGNING_BUNDLE to pull_request builds.
# The REAL certificate-continuity gate runs during trusted main publication.
set -euo pipefail
previous="${1:?Previous official APK}"
candidate="${2:?Pull-request APK}"
destination="${3:?Temporary output directory}"
tools="${4:?Android SDK build tools directory}"
[[ -s "$previous" && -s "$candidate" ]]
[[ "$destination" == /tmp/* || "$destination" == "$RUNNER_TEMP"/* ]] || {
  echo "::error::Only ephemeral runner directories are allowed"
  exit 1
}
mkdir -p "$destination"
key="$destination/temporary-ota-only.jks"
password="$(openssl rand -hex 20)"
keytool -genkeypair -noprompt -alias ota-test -keystore "$key" \
  -storetype PKCS12 -storepass "$password" -keypass "$password" \
  -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Kovelyx temporary CI test only" \
  >/dev/null 2>&1
for item in previous candidate; do
  if [[ "$item" == previous ]]; then input="$previous"; else input="$candidate"; fi
  "$tools/apksigner" sign --ks "$key" --ks-key-alias ota-test \
    --ks-pass "pass:$password" --key-pass "pass:$password" \
    --out "$destination/$item.apk" "$input"
  "$tools/apksigner" verify --verbose "$destination/$item.apk" >/dev/null
done
rm -f "$key"
old_cert="$("$tools/apksigner" verify --print-certs "$destination/previous.apk" | grep -m1 'certificate SHA-256 digest:')"
new_cert="$("$tools/apksigner" verify --print-certs "$destination/candidate.apk" | grep -m1 'certificate SHA-256 digest:')"
[[ -n "$old_cert" && "$old_cert" == "$new_cert" ]] || {
  echo "::error::Synthetic OTA test signing failed"
  exit 1
}
echo "PASS: ephemeral PR-only signature prepared for upgrade smoke; no release signing key used."
