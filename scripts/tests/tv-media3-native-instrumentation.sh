#!/usr/bin/env bash
# Run REAL Media3 Android instrumentation, but avoid an unbounded Gradle
# connectedAndroidTest wait that previously consumed the 90-minute job limit.
set -Eeuo pipefail

mkdir -p /tmp/kovelyx-tv-media3-diagnostics
DIR=/tmp/kovelyx-tv-media3-diagnostics

diagnostics() {
  status=$?
  adb logcat -d -v threadtime > "$DIR/logcat.txt" 2>&1 || true
  adb shell dumpsys activity processes > "$DIR/activity-processes.txt" 2>&1 || true
  adb shell dumpsys meminfo com.kovelyx.tv.debug > "$DIR/meminfo.txt" 2>&1 || true
  adb shell pm list instrumentation > "$DIR/instrumentation-list.txt" 2>&1 || true
  echo "Media3 instrumentation exit status: $status"
}
trap diagnostics EXIT

adb wait-for-device
adb shell getprop sys.boot_completed | grep -Fx '1'
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

chmod +x apps/tv/gradlew
# Instrumentation requires the full AndroidJUnitRunner and tracing classes.
# The optimizedDistribution build deliberately turns R8 on for *debug* and
# also shrinks the androidTest APK; that stripped androidx.tracing.Trace from
# AndroidJUnitRunner and made adb instrumentation hang for 180s before tests.
# Use regular unminified Debug for this isolated native device test; patched
# Media3 AARs, JNI and H264 decoding remain exactly the same source code.
# Distribution/production build optimization stays unchanged.
./apps/tv/gradlew -p apps/tv :app:assembleFullDebug :app:assembleFullDebugAndroidTest \
  --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx5g -XX:MaxMetaspaceSize=768m"

# Android TV builds several ABI-split APKs; the first file is often ARM64,
# while Google's emulator in this job is x86. Installing ARM64 on x86 yields
# INSTALL_FAILED_NO_MATCHING_ABIS before any test can actually run.
ABI="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
case "$ABI" in
  arm64-v8a|armeabi-v7a|x86|x86_64) ;;
  *) echo "::error::Unknown Android emulator ABI: $ABI"; exit 1 ;;
esac
APK_DIR=apps/tv/app/build/outputs/apk/full/debug
APP_APK="$(find "$APK_DIR" -type f -name "app-full-$ABI-debug.apk" -print -quit)"
if [[ -z "$APP_APK" ]]; then
  APP_APK="$(find "$APK_DIR" -type f -name '*universal*debug.apk' -print -quit)"
fi
TEST_APK="$(find apps/tv/app/build/outputs/apk/androidTest/full/debug -type f -name '*.apk' -print -quit)"
if [[ -z "$APP_APK" || -z "$TEST_APK" ]]; then
  echo "::error::Missing APK for TV emulator ABI=$ABI"
  find apps/tv/app/build/outputs/apk -type f -name '*.apk' -print || true
  exit 1
fi
test -s "$APP_APK"
test -s "$TEST_APK"
echo "Emulator ABI: $ABI"
echo "Tested app APK: $APP_APK"
echo "Test runner APK: $TEST_APK"
sha256sum "$APP_APK" "$TEST_APK" > "$DIR/tested-apks.sha256"
adb install -r "$APP_APK"
adb install -r "$TEST_APK"

RUNNER="$(adb shell pm list instrumentation | tr -d '\r' |
  sed -n 's/^instrumentation:\([^ ]*\) (target=com\.kovelyx\.tv\.debug)$/\1/p' | head -n 1)"
if [[ -z "$RUNNER" ]]; then
  echo "::error::Test runner for com.kovelyx.tv.debug is absent after installation"
  adb shell pm list instrumentation
  exit 1
fi
echo "Android instrumentation runner: $RUNNER"

# Each selected native test must actually run, return OK (1 test), and
# complete in under 3 minutes. 'No test discovered' is a failure too.
CASES=(
  'androidx.media3.exoplayer.upstream.DefaultAllocatorNativeLeakTest#testDefaultAllocatorLateReleaseLeak'
  'androidx.media3.exoplayer.upstream.DefaultAllocatorNativeTest#testAllocationAndReleaseLifecycle'
  'androidx.media3.exoplayer.source.SampleDataQueueNativeTest#testCopyFromArray'
  'androidx.media3.exoplayer.source.SampleDataQueueNativeTest#testCopyToArray'
  'androidx.media3.exoplayer.source.SampleDataQueueNativeTest#testCopyBetweenDirectBuffers'
  'com.kovelyx.tv.ui.screens.player.Media3VideoDecodeSmokeTest#h264Mp4ReachesEndWithoutDecoderErrors'
  'com.kovelyx.tv.ui.screens.player.Media3VideoDecodeSmokeTest#h264ReplayAndReleaseSixTimes'
)
for i in "${!CASES[@]}"; do
  case_name="${CASES[$i]}"
  log="$DIR/instrument-$((i+1)).txt"
  echo "=== Media3 device case $((i+1))/${#CASES[@]}: $case_name ==="
  if ! timeout --foreground --signal=TERM --kill-after=15s 180s \
    adb shell am instrument -w -r -e class "$case_name" "$RUNNER" \
    > "$log" 2>&1; then
    echo "::error::Instrumentation exited nonzero or exceeded 180 seconds for $case_name"
    tail -n 80 "$log" || true
    exit 1
  fi
  cat "$log"
  if ! grep -Fq 'OK (1 test)' "$log" || ! grep -Fq 'INSTRUMENTATION_CODE: -1' "$log"; then
    echo "::error::Instrumentation did not confirm one successful native test: $case_name"
    exit 1
  fi
done
echo "PASS: seven real Android TV Media3 instrumentation cases, including repeated H264 decode and release."
