#!/usr/bin/env bash
# Long test ONLY on an ephemeral emulator. Never uses a production account/key.
set -Eeuo pipefail
DIR=/tmp/kovelyx-playback-soak
mkdir -p "$DIR"
diagnostics() {
  status=$?
  adb logcat -d -v threadtime > "$DIR/logcat.txt" 2>&1 || true
  adb shell dumpsys meminfo com.kovelyx.tv.debug > "$DIR/final-meminfo.txt" 2>&1 || true
  adb pull /sdcard/Android/data/com.kovelyx.tv.debug/files/playback-soak/report.json "$DIR/report.json" || true
  echo "Playback soak exit status: $status"
}
trap diagnostics EXIT
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
ABI="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
APK="$(find apps/tv/app/build/outputs/apk/full/debug -name "app-full-$ABI-debug.apk" -print -quit)"
[[ -n "$APK" ]] || APK="$(find apps/tv/app/build/outputs/apk/full/debug -name '*universal*debug.apk' -print -quit)"
TEST="$(find apps/tv/app/build/outputs/apk/androidTest/full/debug -name '*.apk' -print -quit)"
test -s "$APK"
test -s "$TEST"
sha256sum "$APK" "$TEST" > "$DIR/tested-apks.sha256"
adb install -r "$APK"
adb install -r "$TEST"
RUNNER="$(adb shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\([^ ]*\) (target=com\.kovelyx\.tv\.debug)$/\1/p' | head -n 1)"
[[ -n "$RUNNER" ]]
adb logcat -c
if ! timeout --foreground --signal=TERM --kill-after=20s 2400s \
  adb shell am instrument -w -r \
    -e class 'com.kovelyx.tv.ui.screens.player.Media3PlaybackSoakTest#thirtyMinutesWithPauseSeekAndEpisodeChanges' \
    -e soakSeconds 1800 "$RUNNER" > "$DIR/instrumentation.txt" 2>&1; then
  tail -n 80 "$DIR/instrumentation.txt"
  exit 1
fi
cat "$DIR/instrumentation.txt"
grep -Fq 'OK (1 test)' "$DIR/instrumentation.txt"
grep -Fq 'INSTRUMENTATION_CODE: -1' "$DIR/instrumentation.txt"
adb pull /sdcard/Android/data/com.kovelyx.tv.debug/files/playback-soak/report.json "$DIR/report.json"
python3 - <<'PY'
import json
p='/tmp/kovelyx-playback-soak/report.json'
r=json.load(open(p))
assert r['passed'] is True and r['elapsed_ms'] >= 1800000, r
assert len(r['samples']) >= 25 and r['decoded_frames'] > 6000, r
print(json.dumps(r, indent=2))
print('PASS: 30-minute Media3 native-buffer session; pause/resume, seek and episode transitions verified.')
PY
