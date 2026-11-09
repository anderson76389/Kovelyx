#!/usr/bin/env python3
"""Static rollout guards and deterministic upload/download callback counters.

Numbers are algorithmic projections for a complete OTA file, NOT measured device
CPU, battery, heap or network traffic. Actual Kotlin unit and APK CI are separate.
"""
import math
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
BUILDS = {
    "android": ("apps/mobile/composeApp/src/androidFull/kotlin/com/kovelyx/app/features/updater/AndroidAppUpdaterPlatform.kt", 120_212_752),
    "android_shared": ("apps/desktop/composeApp/src/androidFull/kotlin/com/kovelyx/app/features/updater/AndroidAppUpdaterPlatform.kt", 120_212_752),
    "tv": ("apps/tv/app/src/full/java/com/kovelyx/tv/updater/ApkDownloader.kt", 133_753_569),
    "windows": ("apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/features/updater/AppUpdaterPlatform.desktop.kt", 186_126_604),
}


def projected_callbacks(size: int, buffer_size: int, step_size: int) -> int:
    emitted = 0
    last = 0
    downloaded = 0
    while downloaded < size:
        downloaded = min(size, downloaded + buffer_size)
        if downloaded - last >= step_size:
            last = downloaded
            emitted += 1
    if downloaded > last:
        emitted += 1
    return emitted


class UpdaterResourceContractTests(unittest.TestCase):
    def test_measured_algorithmic_workload_reduces_callbacks_by_over_99_percent(self):
        for platform, (_, size) in BUILDS.items():
            with self.subTest(platform=platform):
                before = math.ceil(size / 8192)
                after = projected_callbacks(size, 64 * 1024,
                                            max(1024 * 1024, size // 100))
                self.assertLessEqual(after, 101)
                self.assertLess(after, before * 0.01)
                self.assertLessEqual(math.ceil(size / (64 * 1024)),
                                     math.ceil(size / 8192) / 7.9)
                print(f"{platform}: {size} bytes, progress callbacks {before} -> {after}; "
                      f"read operations {math.ceil(size/8192)} -> {math.ceil(size/(64*1024))}")

    def test_platforms_keep_integrity_and_throttled_ui_progress(self):
        for platform, (relative, _) in BUILDS.items():
            with self.subTest(platform=platform):
                body = (ROOT / relative).read_text(encoding="utf-8")
                self.assertIn("ByteArray(64 * 1024)", body)
                self.assertIn("lastProgressBytes", body)
                self.assertIn("progressStepBytes", body)
                self.assertIn("1024L * 1024L", body)
                self.assertIn("SHA-256", body)
                self.assertIn(".sha256", body)
                self.assertIn("github.com", body)
                self.assertIn("onProgress(", body)

    def test_android_permission_exception_is_deny_not_allow(self):
        for platform in ("android", "android_shared"):
            body = (ROOT / BUILDS[platform][0]).read_text(encoding="utf-8")
            match = re.search(r"catch \(_:\s*SecurityException\) \{([^}]*)\}", body)
            self.assertIsNotNone(match)
            self.assertIn("false", match.group(1))
            self.assertNotIn("true", match.group(1))

    def test_windows_previous_verified_artifact_is_retained_until_new_checksum(self):
        body = (ROOT / BUILDS["windows"][0]).read_text(encoding="utf-8")
        self.assertNotIn("clearDir(updatesDir())", body)
        self.assertLess(body.find("checksum.equals(expectedSha256"),
                        body.find("pruneOldVerifiedInstallers(updatesDir()"))
        self.assertIn("file.isFile", body)
        self.assertIn('file.name.startsWith("Kovelyx-")', body)
        self.assertIn("file != keep", body)

    def test_mobile_and_tv_remove_only_old_apks_after_checksum(self):
        for platform in ("android", "android_shared", "tv"):
            body = (ROOT / BUILDS[platform][0]).read_text(encoding="utf-8")
            self.assertIn('old.name.endsWith(".apk", ignoreCase = true)', body)
            self.assertLess(body.find("equals(expectedSha256"), body.find("old.name.endsWith"))
            self.assertIn("old.isFile", body)

    def test_tv_background_posters_have_bounded_history_and_actual_network_concurrency(self):
        budget = (ROOT / "apps/tv/app/src/main/java/com/kovelyx/tv/core/image/TvRevalidationBudget.kt").read_text(encoding="utf-8")
        strategy = (ROOT / "apps/tv/app/src/main/java/com/kovelyx/tv/core/image/StaleWhileRevalidateCacheStrategy.kt").read_text(encoding="utf-8")
        self.assertIn("maxRememberedUrls: Int = 256", budget)
        self.assertIn("maxConcurrentRequests: Int = 8", budget)
        self.assertIn("inFlight.size >= maxConcurrentRequests", budget)
        self.assertIn("size > maxRememberedUrls", budget)
        self.assertIn("revalidationBudget.tryStart", strategy)
        self.assertIn("revalidationBudget.finish", strategy)
        self.assertNotIn("ConcurrentHashMap<String, Long>()", strategy)
        self.assertNotIn("url.take(80)", strategy)
        self.assertIn("cachedResponseCanRevalidate", strategy)


if __name__ == "__main__":
    unittest.main()
