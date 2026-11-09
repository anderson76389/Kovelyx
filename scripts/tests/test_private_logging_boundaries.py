#!/usr/bin/env python3
"""Static privacy regressions for the three native Kovelyx clients.

Not a packet capture or proof that all third-party addons are privacy-safe.
Never prints URL literals, secrets, or source-code snippets.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TV = "apps/tv/app/src/main/java/com/kovelyx/tv"
MOBILE = "apps/mobile/composeApp/src/androidMain/kotlin/com/kovelyx/app"
DESKTOP_ANDROID = "apps/desktop/composeApp/src/androidMain/kotlin/com/kovelyx/app"


def source(path):
    return (ROOT / path).read_text(encoding="utf-8")


class DiagnosticPrivacy(unittest.TestCase):
    def test_no_addon_url_in_network_breadcrumbs(self):
        for prefix in (TV, MOBILE, DESKTOP_ANDROID):
            with self.subTest(platform=prefix):
                code = source(prefix + "/core/diagnostics/SentryNetworkBreadcrumbInterceptor.kt")
                self.assertIn("redacted.invalid", code)
                self.assertNotIn('breadcrumb.setData("host"', code)
                self.assertNotIn('breadcrumb.setData("path"', code)
                self.assertNotIn('breadcrumb.setData("error_message"', code)
                self.assertNotIn("url.newBuilder()", code)
                self.assertNotIn('url.encodedPath', code)

    def test_tv_diagnostics_dont_log_raw_response_or_throwable(self):
        code = source(TV + "/core/logging/LogDiagnostics.kt")
        self.assertIn("[content redacted]", code)
        self.assertIn("[url redacted]", code)
        self.assertIn("[body redacted]", code)
        self.assertNotIn('return this ?: "(null)"', code)
        self.assertNotIn("current.message", code)

    def test_tv_player_logs_do_not_include_stream_path(self):
        code = source(TV + "/ui/screens/player/LoggingDataSource.kt")
        self.assertNotIn("dataSpec.uri.path", code)
        self.assertNotIn("e.message", code)
        self.assertIn("elapsed", code)
        self.assertIn("e.javaClass.simpleName", code)

    def test_no_android_cloud_backup_of_private_app_storage(self):
        manifests = (
            "apps/tv/app/src/main/AndroidManifest.xml",
            "apps/mobile/androidApp/src/main/AndroidManifest.xml",
            "apps/desktop/androidApp/src/main/AndroidManifest.xml",
        )
        for path in manifests:
            with self.subTest(path=path):
                xml = ET.fromstring(source(path))
                android = "{http://schemas.android.com/apk/res/android}"
                self.assertEqual(xml.find("application").get(android + "allowBackup"), "false")

    def test_diagnostics_no_automatic_sentry_initialization(self):
        for path in (
            "apps/tv/app/src/main/AndroidManifest.xml",
            "apps/mobile/androidApp/src/main/AndroidManifest.xml",
            "apps/desktop/androidApp/src/main/AndroidManifest.xml",
        ):
            with self.subTest(path=path):
                code = source(path)
                self.assertIn('android:name="io.sentry.auto-init"', code)
                self.assertIn('android:value="false"', code)


if __name__ == "__main__":
    unittest.main()
