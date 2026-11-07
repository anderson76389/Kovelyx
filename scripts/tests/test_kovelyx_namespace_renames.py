#!/usr/bin/env python3
"""Fail CI if old Nuvio Java/Kotlin application namespaces return.

The engine's binary ABI and preserved deeplink/cache identifiers are out of
scope; this guard only enforces first-party source and APK identity.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEGACY_DOTS = ("com.nuvio.app", "com.nuvio.android", "com.nuvio.tv")
LEGACY_PATHS = ("com/nuvio/app", "com/nuvio/android", "com/nuvio/tv")
SUFFIXES = (".kt", ".kts", ".java", ".xml", ".pro", ".properties", ".gradle")
APP_ROOTS = ("apps/tv/", "apps/mobile/", "apps/desktop/")


def main() -> None:
    files = [path.decode() for path in
             subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).split(b"\0")
             if path]
    errors: list[str] = []
    for path in files:
        if not path.startswith(APP_ROOTS):
            continue
        if any(old in path for old in LEGACY_PATHS):
            errors.append(f"Historical source package filename: {path}")
        if not path.endswith(SUFFIXES):
            continue
        payload = (ROOT / path).read_bytes()
        if b"\0" in payload:
            continue
        text = payload.decode("utf-8", errors="replace")
        if path.endswith("/KovelyxUpgradeWorkerFactory.kt"):
            # Persisted WorkManager class strings are essential for in-place upgrades.
            # Permit only these two exact historical names, not arbitrary old imports.
            text = text.replace("com.nuvio.app.features.downloads.DownloadsTransferWorker", "")
            text = text.replace("com.nuvio.app.features.notifications.EpisodeReleaseNotificationWorker", "")
        for old in LEGACY_DOTS + LEGACY_PATHS:
            if old in text:
                errors.append(f"Historical Java/Kotlin package reference: {path}: {old}")

    # These first-party Kotlin files live under a directory called "build",
    # which Git would silently ignore at a NEW package path without git add -f.
    required = (
        "apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/build/AppFeaturePolicy.kt",
        "apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/build/KovelyxIntegrationCredentials.kt",
        "apps/mobile/composeApp/src/androidPlaystore/kotlin/com/kovelyx/app/core/build/AppFeaturePolicy.android.kt",
        "apps/mobile/composeApp/src/androidFull/kotlin/com/kovelyx/app/core/build/AppFeaturePolicy.android.kt",
        "apps/desktop/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/build/AppFeaturePolicy.kt",
        "apps/desktop/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/build/AppVersionPolicy.kt",
        "apps/desktop/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/build/KovelyxIntegrationCredentials.kt",
        "apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/core/build/AppFeaturePolicy.desktop.kt",
    )
    for required_path in required:
        if required_path not in files or not (ROOT / required_path).is_file():
            errors.append(f"Missing essential versioned Kotlin policy source: {required_path}")

    # Installed WorkManager jobs store historic qualified class names. Keep the
    # app-specific factory active, or queued downloads/reminders will fail.
    app_entries = (
        ("apps/mobile", "apps/mobile/composeApp/src/androidMain/kotlin/com/kovelyx/app/KovelyxApplication.kt"),
        ("apps/desktop", "apps/desktop/composeApp/src/androidMain/kotlin/com/kovelyx/app/KovelyxApplication.kt"),
    )
    for app_root, application in app_entries:
        app_source = (ROOT / application).read_text()
        if "Configuration.Provider" not in app_source or "KovelyxUpgradeWorkerFactory()" not in app_source:
            errors.append(f"Missing custom WorkManager configuration in {application}")
        if "WorkManager.getInstance(this)" not in app_source:
            errors.append(f"Missing queued-job recovery at application startup: {application}")
        manifest = (ROOT / app_root / "androidApp/src/main/AndroidManifest.xml").read_text()
        if 'android:name="androidx.work.WorkManagerInitializer"' not in manifest or 'tools:node="remove"' not in manifest:
            errors.append(f"WorkManager auto-init may override custom worker factory: {app_root}")
        factory_path = ROOT / app_root / "composeApp/src/androidMain/kotlin/com/kovelyx/app/core/compat/KovelyxUpgradeWorkerFactory.kt"
        factory_source = factory_path.read_text()
        for worker in (
            "com.nuvio.app.features.downloads.DownloadsTransferWorker",
            "com.nuvio.app.features.notifications.EpisodeReleaseNotificationWorker",
        ):
            if worker not in factory_source:
                errors.append(f"Queued worker compatibility missing in {factory_path}: {worker}")

    tv_manifest = (ROOT / "apps/tv/app/src/main/AndroidManifest.xml").read_text()
    if 'android:name=".KovelyxApplication"' not in tv_manifest:
        errors.append("Renamed Android TV Application class missing in manifest")

    mobile = (ROOT / "apps/mobile/androidApp/build.gradle.kts").read_text()
    tv = (ROOT / "apps/tv/app/build.gradle.kts").read_text()
    if 'applicationId = "com.kovelyx.mobile"' not in mobile:
        errors.append("Mobile applicationId unexpectedly changed")
    if 'applicationId = "com.kovelyx.tv"' not in tv:
        errors.append("TV applicationId unexpectedly changed")
    if errors:
        raise SystemExit("\n".join(errors[:100]))
    print("OK: no old application source packages; Android app identity preserved.")


if __name__ == "__main__":
    main()
