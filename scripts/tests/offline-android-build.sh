#!/usr/bin/env bash
set -euo pipefail
cd "${OFFLINE_SOURCE:?missing clean source}/apps/mobile"
test -d "${GRADLE_USER_HOME:?missing restored Gradle home}/caches/modules-2"
test "$(ip -o link show | wc -l)" -eq 1
./gradlew -I "$OFFLINE_SOURCE/scripts/tests/offline-maven-mirror.init.gradle" :androidApp:assembleFullDebug :composeApp:testAndroidHostTest \
  -Pnuvio.android.distribution=full --offline --no-daemon --max-workers=2 --no-build-cache --no-configuration-cache --rerun-tasks \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=768m"
