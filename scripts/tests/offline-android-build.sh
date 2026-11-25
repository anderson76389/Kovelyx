#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="${KOVELYX_JAVA21_HOME:?missing Java 21 selected by setup-java}"
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/java" -version 2>&1 | grep -E 'version "21([."]|$)' >/dev/null
cd "${OFFLINE_SOURCE:?missing clean source}/apps/mobile"
test -d "${GRADLE_USER_HOME:?missing restored Gradle home}/caches/modules-2"
test "$(ip -o link show | wc -l)" -eq 1
./gradlew -I "$OFFLINE_SOURCE/scripts/tests/offline-maven-mirror.init.gradle" :androidApp:assembleFullDebug :composeApp:testAndroidHostTest \
  -Dorg.gradle.java.home="$JAVA_HOME" -Porg.gradle.java.installations.paths="$JAVA_HOME" \
  -Porg.gradle.java.installations.auto-detect=false -Porg.gradle.java.installations.auto-download=false \
  -Pnuvio.android.distribution=full --offline --no-daemon --max-workers=2 --no-build-cache --no-configuration-cache --rerun-tasks \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=768m"
