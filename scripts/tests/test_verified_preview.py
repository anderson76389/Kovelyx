#!/usr/bin/env python3
"""Check publication provenance and artifact selection without network or writes."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

SPEC = importlib.util.spec_from_file_location(
    "preview", Path(__file__).resolve().parents[1] / "publish-verified-preview.py")
PREVIEW = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREVIEW)


class VerifiedPreviewTests(unittest.TestCase):
    def test_proofs_cannot_come_from_a_different_commit_repo_or_pull_request(self):
        correct = dict(name=PREVIEW.WINDOWS, head_sha="a" * 40, event="push", run_number=1,
                       head_repository={"full_name": PREVIEW.REPOSITORY})
        rejected = [dict(correct, head_sha="b" * 40, run_number=10),
                    dict(correct, event="pull_request", run_number=11),
                    dict(correct, head_repository={"full_name": "someone/else"}, run_number=12)]
        self.assertEqual(correct, PREVIEW.matching_run(rejected + [correct], PREVIEW.WINDOWS, "a" * 40))
        self.assertIsNone(PREVIEW.matching_run(rejected, PREVIEW.WINDOWS, "a" * 40))

    def test_a_missing_tag_is_created_and_verified_before_a_draft_release(self):
        sha = "a" * 40
        with patch.object(PREVIEW.subprocess, "run", return_value=SimpleNamespace(returncode=1)), \
             patch.object(PREVIEW, "gh") as command, \
             patch.object(PREVIEW, "api", return_value={"object": {"type": "commit", "sha": sha}}):
            PREVIEW.ensure_tag("v1.1.744-alpha", sha)
            self.assertIn("POST", command.call_args.args)
            self.assertIn("sha=" + sha, command.call_args.args)

    def test_an_existing_tag_cannot_be_repointed_to_different_code(self):
        with patch.object(PREVIEW.subprocess, "run", return_value=SimpleNamespace(returncode=0)), \
             patch.object(PREVIEW, "gh") as command, \
             patch.object(PREVIEW, "api", return_value={"object": {"type": "commit", "sha": "b" * 40}}):
            with self.assertRaises(RuntimeError):
                PREVIEW.ensure_tag("v1.1.744-alpha", "a" * 40)
            command.assert_not_called()

    def test_draft_release_is_found_without_the_public_tag_endpoint(self):
        draft = {"tag_name": "v1.1.748-alpha", "draft": True}
        with patch.object(PREVIEW, "api", return_value=[draft]) as request:
            self.assertEqual(PREVIEW.find_release("v1.1.748-alpha"), draft)
            request.assert_called_once_with("releases?per_page=100")

    def test_recovery_refuses_any_application_change(self):
        run = dict(name="Compiler Kovelyx (APK et diagnostic Windows)", head_sha="a" * 40,
                   event="push", head_branch="fix/cloudflare-network-status-and-settings",
                   head_repository={"full_name": PREVIEW.REPOSITORY}, id=1)
        jobs = [{"name": name, "conclusion": "success"} for name in
                ("Android TV - APK Full Debug", "Android mobile - APK Full Debug")]
        with patch.object(PREVIEW.subprocess, "run", return_value=SimpleNamespace(returncode=0)), \
             patch.object(PREVIEW.subprocess, "check_output", return_value="apps/tv/app/build.gradle.kts\n"), \
             patch.object(PREVIEW, "api", return_value={"workflow_runs": [run]}) as request:
            self.assertIsNone(PREVIEW.recoverable_source("b" * 40))
            self.assertEqual(request.call_count, 1)
        with patch.object(PREVIEW.subprocess, "run", return_value=SimpleNamespace(returncode=0)), \
             patch.object(PREVIEW.subprocess, "check_output", return_value="scripts/publish-verified-preview.py\n"), \
             patch.object(PREVIEW, "api", side_effect=[{"workflow_runs": [run]}, {"jobs": jobs}]):
            self.assertEqual(PREVIEW.recoverable_source("b" * 40), run)

    def test_windows_artifacts_are_mapped_from_commit_to_public_version(self):
        sha = "a" * 40
        self.assertEqual(list(PREVIEW.windows_package_names(sha, "v1.1.748-alpha")), [
            (f"Kovelyx-Windows-{sha}.msi", "Kovelyx-Windows-v1.1.748-alpha.msi"),
            (f"Kovelyx-Windows-Setup-{sha}.exe", "Kovelyx-Windows-Setup-v1.1.748-alpha.exe"),
            (f"Kovelyx-Windows-Portable-{sha}.zip", "Kovelyx-Windows-Portable-v1.1.748-alpha.zip"),
        ])

    def test_ambiguous_or_incomplete_packages_block_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(RuntimeError):
                PREVIEW.unique_file(root, "*.apk")
            (root / "a.apk").write_bytes(b"truncated")
            with self.assertRaises(RuntimeError):
                PREVIEW.unique_file(root, "*.apk")
            (root / "a.apk").write_bytes(b"a" * 1_000_001)
            self.assertEqual(root / "a.apk", PREVIEW.unique_file(root, "*.apk"))
            (root / "b.apk").write_bytes(b"a" * 1_000_001)
            with self.assertRaises(RuntimeError):
                PREVIEW.unique_file(root, "*.apk")


if __name__ == "__main__":
    unittest.main()
