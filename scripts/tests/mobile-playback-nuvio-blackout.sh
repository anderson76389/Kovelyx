#!/usr/bin/env bash
# Emulator-side proof of upstream blocking, followed by real decoder playback.
set -Eeuo pipefail
adb wait-for-device
adb shell settings put global private_dns_mode off
PROBE="kv-mobile-playback-${GITHUB_RUN_ID:-local}.nuvio.tv"
BEFORE="$(grep -Fic "$PROBE" /tmp/kv-playback-nuvio-dns.log || true)"
adb shell ping -c 1 -W 2 "$PROBE" >/tmp/kv-playback-probe.txt 2>&1 || true
AFTER="$(grep -Fic "$PROBE" /tmp/kv-playback-nuvio-dns.log || true)"
if (( AFTER <= BEFORE )); then
  echo "::error::Emulator did not use blocked upstream DNS: test cannot certify Nuvio isolation"
  cat /tmp/kv-playback-probe.txt
  exit 1
fi
if ! grep -Eq '0[.]0[.]0[.]0|127[.]0[.]0[.]1|unknown host|no address associated' /tmp/kv-playback-probe.txt; then
  echo "::error::Nuvio DNS not blocked on emulator"
  cat /tmp/kv-playback-probe.txt
  exit 1
fi
echo 'PASS: DNS Nuvio blocked on the emulator, run ExoPlayer+libmpv H264 instrumentation.'
chmod +x apps/mobile/gradlew
./apps/mobile/gradlew -p apps/mobile :androidApp:connectedFullDebugAndroidTest \
  -Pkovelyx.optimizedDistribution=true -Pkovelyx.optimizedInstrumentation=true \
  --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx5g -XX:MaxMetaspaceSize=768m" \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kovelyx.android.VideoPlaybackSmokeTest
