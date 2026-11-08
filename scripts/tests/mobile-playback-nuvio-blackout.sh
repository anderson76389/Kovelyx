#!/usr/bin/env bash
# Emulator-side proof of upstream blocking, followed by real decoder playback.
set -Eeuo pipefail
adb wait-for-device
adb shell settings put global private_dns_mode off
# Android may expose DNS before its virtual network is actually ready.
# Verify public GitHub DNS first; do not confuse an offline emulator with
# successful Nuvio isolation.
OTHER_DNS=""
for attempt in {1..12}; do
  OTHER_DNS="$(adb shell ping -c 1 -W 2 api.github.com 2>&1 || true)"
  if grep -Eq '([1-9][0-9]*[.]){3}[0-9]+' <<< "$OTHER_DNS" &&
     ! grep -Eq '0[.]0[.]0[.]0|127[.]0[.]0[.]1' <<< "$OTHER_DNS"; then
    echo "Independent DNS available via api.github.com"
    break
  fi
  echo "Waiting for emulator network ($attempt/12): $OTHER_DNS"
  sleep 5
done
if ! grep -Eq '([1-9][0-9]*[.]){3}[0-9]+' <<< "$OTHER_DNS" ||
   grep -Eq '0[.]0[.]0[.]0|127[.]0[.]0[.]1' <<< "$OTHER_DNS"; then
  echo "::error::Public DNS unavailable: isolation test environment is invalid"
  exit 1
fi

# Use unique names to bypass Android negative DNS caching. A failed ping alone
# is not proof: the actual request must appear in our dnsmasq query log.
BLOCKED_PROVEN=false
for attempt in {1..12}; do
  PROBE="kv-mobile-playback-${GITHUB_RUN_ID:-local}-$attempt.nuvio.tv"
  BEFORE="$(grep -Fic "$PROBE" /tmp/kv-playback-nuvio-dns.log || true)"
  RES="$(adb shell ping -c 1 -W 2 "$PROBE" 2>&1 || true)"
  AFTER="$(grep -Fic "$PROBE" /tmp/kv-playback-nuvio-dns.log || true)"
  echo "DNS probe $attempt ($BEFORE -> $AFTER): $RES"
  if (( AFTER > BEFORE )) &&
     grep -Eqi '0[.]0[.]0[.]0|127[.]0[.]0[.]1|unknown host|not known|no address associated' <<< "$RES"; then
    BLOCKED_PROVEN=true
    break
  fi
  sleep 5
done
if [[ "$BLOCKED_PROVEN" != true ]]; then
  echo "::error::Filtered DNS was not actually observed; do not claim video independence"
  tail -n 45 /tmp/kv-playback-nuvio-dns.log || true
  adb shell getprop net.dns1 || true
  exit 1
fi
echo 'PASS: DNS Nuvio blocked on the emulator, run ExoPlayer+libmpv H264 instrumentation.'
chmod +x apps/mobile/gradlew
./apps/mobile/gradlew -p apps/mobile :androidApp:connectedFullDebugAndroidTest \
  -Pkovelyx.optimizedDistribution=true -Pkovelyx.optimizedInstrumentation=true \
  --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx5g -XX:MaxMetaspaceSize=768m" \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kovelyx.android.VideoPlaybackSmokeTest
