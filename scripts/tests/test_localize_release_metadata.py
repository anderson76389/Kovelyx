"""Contrôles de la francisation sans toucher aux téléchargements."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localize_release_metadata import localize_release


class ReleaseMetadataFrenchTests(unittest.TestCase):
    def test_legacy_windows_notes_preserve_android_sections_and_identifiers(self):
        release = {
            "tag_name": "v1.1.575-alpha",
            "name": "Kovelyx Windows v1.1.575-alpha — test",
            "body": (
                "## Kovelyx Windows — preview v1.1.575-alpha\n\n"
                "**Recommended download:** Kovelyx-Windows-Setup-v1.1.575-alpha.exe (one file to INSTALL).\n"
                "The MSI is an alternative installer. The portable ZIP must retain the complete app and Java runtime folders.\n"
                "- Source Windows CI: https://github.com/example/actions/runs/42\n"
                "- Source commit: abcdef123456\n"
                "- SHA-256 checksums are provided for every file.\n"
                "- This build is **experimental, unsigned and not certified on real hardware**.\n"
                "- Playback, extensions, tracking sign-in, and updates still need real-PC verification.\n"
                "- License and dependency notices: https://github.com/example/docs/PROVENANCE.md\n"
                "This is NOT a stable release. Do not ignore Windows security warnings.\n\n"
                "## Android mobile et Android TV (préversions de test)\n"
                "- Test TV : https://example.com/123\n"
            ),
        }
        title, body = localize_release(release)
        self.assertEqual(title, "Kovelyx Windows v1.1.575-alpha — préversion expérimentale")
        self.assertIn("Téléchargement conseillé", body)
        self.assertIn("une version stable", body)
        self.assertNotIn("Recommended download", body)
        self.assertIn("Kovelyx-Windows-Setup-v1.1.575-alpha.exe", body)
        self.assertIn("https://github.com/example/actions/runs/42", body)
        self.assertIn("Commit source : abcdef123456", body)
        self.assertIn("## Android mobile et Android TV (préversions de test)", body)
        self.assertIn("- Test TV : https://example.com/123", body)
        self.assertEqual(localize_release({"tag_name": release["tag_name"], "name": title, "body": body}), (title, body))

    def test_stable_and_unknown_releases_unchanged(self):
        for tag in ["v1.1.575", "android-preview-abc", "v1.1.575-custom"]:
            source = {"tag_name": tag, "name": "Original", "body": "## English notes"}
            self.assertEqual(localize_release(source), ("Original", "## English notes"))

    def test_only_recognized_english_prefix_is_changed(self):
        source = {"tag_name": "v1.1.575-alpha", "name": "Kovelyx FR", "body": "## Version test : déjà traduite\n\nInformations FR."}
        self.assertEqual(localize_release(source), ("Kovelyx FR", source["body"]))


    def test_android_preview_title_and_build_label_in_french(self):
        data = {
            "tag_name": "android-preview-012345abcdef",
            "name": "Kovelyx Android experimental - android-preview-012345abcdef",
            "body": "## Kovelyx Android - préversion expérimentale\\n\\nBuild : https://example.com/42",
        }
        # Use real newline delimiters as in the published GitHub notes.
        data["body"] = data["body"].replace("\\n", "\n")
        title, body = localize_release(data)
        self.assertEqual(title, "Kovelyx Android — préversion expérimentale · android-preview-012345abcdef")
        self.assertIn("Compilation : https://example.com/42", body)
        self.assertEqual(localize_release({"tag_name": data["tag_name"], "name": title, "body": body}), (title, body))

    def test_tv_preview_preserves_existing_french(self):
        data = {
            "tag_name": "tv-preview-012345abcdef",
            "name": "Kovelyx TV — préversion tv-preview-012345abcdef",
            "body": "# Kovelyx TV - APK universel expérimental\n\nBuild TV : https://example.com/tv",
        }
        title, body = localize_release(data)
        self.assertEqual(title, data["name"])
        self.assertIn("Compilation TV : https://example.com/tv", body)
        self.assertNotIn("Build TV :", body)


if __name__ == "__main__":
    unittest.main()
