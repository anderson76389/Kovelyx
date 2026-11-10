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
    def test_real_optimized_workflow_jobs_match_the_publication_gate(self):
        import re
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/build.yml").read_text()
        names = re.findall(r"^    name: (Android (?:TV|mobile) - APK .+)$", workflow, re.MULTILINE)
        self.assertEqual(len(names), 2)
        jobs = [{"name": name, "conclusion": "success"} for name in names]
        self.assertTrue(PREVIEW.android_jobs_succeeded(jobs))
        self.assertFalse(PREVIEW.android_jobs_succeeded(jobs[:1]))
        jobs[1]["conclusion"] = "failure"
        self.assertFalse(PREVIEW.android_jobs_succeeded(jobs))


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

    def test_new_draft_is_retried_until_visible(self):
        draft = {"tag_name": "v1.1.752-alpha", "draft": True}
        with patch.object(PREVIEW, "find_release", side_effect=[None, None, draft]), \
             patch.object(PREVIEW.time, "sleep") as pause:
            self.assertEqual(PREVIEW.wait_for_release(draft["tag_name"]), draft)
            self.assertEqual(pause.call_count, 2)
        with patch.object(PREVIEW, "find_release", return_value=None), \
             patch.object(PREVIEW.time, "sleep") as pause:
            with self.assertRaises(RuntimeError):
                PREVIEW.wait_for_release("absent")
            self.assertEqual(pause.call_count, 12)

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

    def test_continuity_gate_selects_latest_complete_stable_release(self):
        def official(tag, **kwargs):
            return {"tag_name": tag, "assets": [
                {"name": f"Kovelyx-Android-{tag}.apk"},
                {"name": f"Kovelyx-TV-{tag}-universal.apk"},
            ], **kwargs}
        releases = [
            official("v1.1.1078"), official("v1.1.1071"),
            official("v1.1.1099", draft=True),
            official("v1.1.1092", prerelease=True),
            {"tag_name": "v1.1.1094", "assets": [
                {"name": "Kovelyx-Android-v1.1.1094.apk"}]},
        ]
        selected = PREVIEW.previous_signed_public_release(releases, "v1.1.1095")
        self.assertEqual(selected["tag_name"], "v1.1.1078")
        with self.assertRaises(RuntimeError):
            PREVIEW.previous_signed_public_release(releases, "v1.1.1000")
        with self.assertRaises(RuntimeError):
            PREVIEW.previous_signed_public_release([], "v1.1.1095")

    def test_previous_signer_gate_accepts_matching_apks_and_rejects_mismatch(self):
        import hashlib
        tag = "v1.1.1078"
        names = [f"Kovelyx-Android-{tag}.apk", f"Kovelyx-TV-{tag}-universal.apk"]
        release = {"tag_name": tag, "assets": [{"name": n} for n in names]}
        fingerprint = "Signer #1 certificate SHA-256 digest: " + ("b" * 64)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def mock_gh(*args):
                # The *download* routine is substituted by local harmless fixtures.
                self.assertEqual(args[:2], ("release", "download"))
                name = args[args.index("--pattern") + 1]
                self.assertIn(name, names)
                folder = Path(args[args.index("--dir") + 1])
                folder.mkdir(exist_ok=True)
                payload = (name + "-fixture").encode("ascii")
                (folder / name).write_bytes(payload)
                (folder / (name + ".sha256")).write_text(
                    hashlib.sha256(payload).hexdigest() + "  " + name + "\n")
            with patch.object(PREVIEW, "api", return_value=[release]), \\
                 patch.object(PREVIEW, "gh", side_effect=mock_gh), \\
                 patch.object(PREVIEW.subprocess, "check_output", return_value=fingerprint + "\n"):
                PREVIEW.verify_previous_public_signatures(
                    root, root, "v1.1.1095", [[fingerprint], [fingerprint]])
                with tempfile.TemporaryDirectory() as other:
                    with self.assertRaisesRegex(RuntimeError, "Signature APK modifiée"):
                        PREVIEW.verify_previous_public_signatures(
                            Path(other), root, "v1.1.1095",
                            [[fingerprint], ["Signer #1 certificate SHA-256 digest: " + "c" * 64]])

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
