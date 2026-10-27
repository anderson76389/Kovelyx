#!/usr/bin/env python3
"""Non-regression: Kovelyx Cloudflare must not trigger legacy profile RPCs.

Static source checks, NOT a substitute for executing the apps on real devices.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
PLATFORMS = ("desktop", "mobile")


class CloudflareLegacyBoundaryTests(unittest.TestCase):
    def test_member_background_catalog_is_never_started_in_cloudflare_mode(self):
        for platform in PLATFORMS:
            with self.subTest(platform=platform):
                source = (
                    ROOT / f"apps/{platform}/composeApp/src/commonMain/kotlin/"
                    "com/nuvio/app/features/membership/ProfileBackgroundRepository.kt"
                ).read_text(encoding="utf-8")
                self.assertIn("import com.nuvio.app.core.cloud.CloudflareAccountClient", source)
                self.assertRegex(
                    source,
                    r"fun ensureLoaded\(\)\s*\{[\s\S]{0,280}"
                    r"if \(CloudflareAccountClient\.enabled\)\s*\{\s*invalidate\(\)",
                )
                for function in ("loadSelectedAndPreload", "preloadLandscapeImages"):
                    match = re.search(
                        rf"fun {function}\([^)]*\)\s*\{{([\s\S]{{0,160}})",
                        source,
                    )
                    self.assertIsNotNone(match, f"{platform}: {function} missing")
                    self.assertIn("if (CloudflareAccountClient.enabled) return", match.group(1))

    def test_oauth_defaults_do_not_use_the_old_app_scheme(self):
        for platform in PLATFORMS:
            with self.subTest(platform=platform):
                source = (ROOT / f"apps/{platform}/composeApp/build.gradle.kts").read_text(
                    encoding="utf-8"
                )
                self.assertNotIn('"nuvio://auth/trakt"', source)
                self.assertNotIn('"nuvio://auth/simkl"', source)
                self.assertIn('"kovelyx://auth/trakt"', source)

    def test_app_identifies_itself_as_kovelyx_in_custom_server_requests(self):
        for platform in PLATFORMS:
            with self.subTest(platform=platform):
                source = (
                    ROOT / f"apps/{platform}/composeApp/src/commonMain/kotlin/"
                    "com/nuvio/app/core/network/ServerDiscovery.kt"
                ).read_text(encoding="utf-8")
                self.assertRegex(source, r'"User-Agent"\s+to\s+"Kovelyx')


if __name__ == "__main__":
    unittest.main()
