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
    def test_linux_release_requires_three_real_versioned_packages(self):
        import hashlib
        tag = "v1.1.1148"
        names = PREVIEW.linux_package_names(tag)
        self.assertEqual(names, (
            "Kovelyx-Linux-x86_64-v1.1.1148.AppImage",
            "Kovelyx-Linux-amd64-v1.1.1148.deb",
            "Kovelyx-Linux-x86_64-v1.1.1148.rpm",
        ))
        with tempfile.TemporaryDirectory() as folder:
            dest = Path(folder)
            payloads = [
                b"\x7fELF", b"!<arch>\n", b"\xed\xab\xee\xdb"
            ]
            for name, header in zip(names, payloads):
                payload = header + b"k" * 1_000_001
                (dest / name).write_bytes(payload)
                (dest / (name + ".sha256")).write_text(
                    hashlib.sha256(payload).hexdigest() + "  " + name + "\n",
                    encoding="utf-8")
            actual = PREVIEW.verify_linux_artifacts(dest, tag)
            self.assertEqual([path.name for path in actual], sorted(names))
            (dest / (names[0] + ".sha256")).write_text("0" * 64 + "  " + names[0] + "\n")
            with self.assertRaisesRegex(RuntimeError, "Empreinte Linux"):
                PREVIEW.verify_linux_artifacts(dest, tag)
            (dest / (names[0] + ".sha256")).write_text(
                hashlib.sha256((dest / names[0]).read_bytes()).hexdigest() + "  " + names[0] + "\n")
            (dest / "unexpected.bin").write_bytes(b"rogue")
            with self.assertRaisesRegex(RuntimeError, "incomplets ou inattendus"):
                PREVIEW.verify_linux_artifacts(dest, tag)

    def test_linux_ci_identity_matches_publishing_gate(self):
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/linux.yml").read_text()
        self.assertIn("name: " + PREVIEW.LINUX, workflow)
        self.assertIn("name: " + PREVIEW.LINUX_JOB, workflow)
        self.assertIn("name: kovelyx-linux-packages", workflow)
        self.assertIn("packageReleaseAppImage", workflow)
        self.assertIn("packageReleaseDeb", workflow)
        self.assertIn("packageReleaseRpm", workflow)
        publisher = (Path(__file__).resolve().parents[1] / "publish-verified-preview.py").read_text()
        self.assertIn("(WINDOWS, LINUX, FUNCTIONAL, FRENCH)", publisher)
        self.assertIn('verify_linux_artifacts(root / "linux", tag)', publisher)

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
            with (patch.object(PREVIEW, "api", return_value=[release]),
                  patch.object(PREVIEW, "gh", side_effect=mock_gh),
                  patch.object(PREVIEW.subprocess, "check_output", return_value=fingerprint + "\n")):
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


    def test_compact_release_requires_published_and_complete_bridge(self):
        tag = "v1.1.1167"
        names = PREVIEW.expected_public_assets(tag)
        valid = {
            "tag_name": tag, "draft": False,
            "assets": [{
                "name": name, "state": "uploaded", "size": 1024,
                "digest": "sha256:" + "a" * 64,
            } for name in names],
        }
        PREVIEW.require_legacy_bridge(valid)
        for invalid in (None, dict(valid, draft=True),
                        dict(valid, assets=valid["assets"][1:]),
                        dict(valid, assets=[dict(valid["assets"][0], digest="")])):
            with self.assertRaises(RuntimeError):
                PREVIEW.require_legacy_bridge(invalid)

    def test_bridge_release_then_four_platform_bundles(self):
        self.assertFalse(PREVIEW.is_bundle_tag("v1.1.1166"))
        self.assertFalse(PREVIEW.is_bundle_tag("v1.1.1167"))
        self.assertTrue(PREVIEW.is_bundle_tag("v1.1.1168"))
        self.assertEqual(len(PREVIEW.expected_public_assets("v1.1.1167")), 16)
        self.assertEqual(PREVIEW.expected_public_assets("v1.1.1168"), {
            "Kovelyx-Android-v1.1.1168.zip",
            "Kovelyx-TV-v1.1.1168.zip",
            "Kovelyx-Linux-v1.1.1168.zip",
            "Kovelyx-Windows-v1.1.1168.zip",
        })

    def test_compact_platform_archives_embed_verifiable_checksums(self):
        import hashlib
        import zipfile
        tag = "v1.1.1168"
        sha = "a" * 40
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            installers = root / "installers"
            installers.mkdir()
            for index, name in enumerate(sorted({
                n for group in PREVIEW.platform_bundle_contents(tag).values() for n in group
            })):
                (installers / name).write_bytes(bytes([index + 1]) * 1_000_001)
            published = PREVIEW.build_platform_bundles(installers, root / "bundles", tag)
            bundles = sorted(published.iterdir())
            self.assertEqual(len(bundles), 4)
            for bundle in bundles:
                self.assertGreater(bundle.stat().st_size, len(PREVIEW.platform_bundle_contents(tag)[bundle.name]) * 1_000_000)
                PREVIEW.verify_platform_bundle(
                    bundle, PREVIEW.platform_bundle_contents(tag)[bundle.name]
                )

            def release():
                return {
                    "tag_name": tag,
                    "target_commitish": sha,
                    "draft": True,
                    "assets": [{
                        "name": p.name,
                        "state": "uploaded",
                        "size": p.stat().st_size,
                        "digest": "sha256:" + PREVIEW.digest(p),
                    } for p in bundles],
                }

            PREVIEW.verify_uploaded_release_inventory(tag, sha, published, release())
            incorrect = release()
            incorrect["assets"][1]["digest"] = "sha256:" + "0" * 64
            with self.assertRaisesRegex(RuntimeError, "Empreinte GitHub"):
                PREVIEW.verify_uploaded_release_inventory(tag, sha, published, incorrect)
            changed = bundles[0]
            with zipfile.ZipFile(changed, "a") as zipped:
                zipped.writestr("unexpected.bin", b"not an installer")
            with self.assertRaisesRegex(RuntimeError, "Contenu archive"):
                PREVIEW.verify_platform_bundle(
                    changed, PREVIEW.platform_bundle_contents(tag)[changed.name]
                )

    def test_compact_publisher_does_not_drop_unknown_or_missing_installers(self):
        tag = "v1.1.1168"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            for name in sorted({
                n for group in PREVIEW.platform_bundle_contents(tag).values() for n in group
            })[:-1]:
                (source / name).write_bytes(b"test" * 300_000)
            with self.assertRaisesRegex(RuntimeError, "absent ou inattendu"):
                PREVIEW.build_platform_bundles(source, root / "output", tag)

    def test_final_release_requires_all_eight_packages_and_checksum_files(self):
        tag = "v1.1.1164"
        names = PREVIEW.expected_public_assets(tag)
        self.assertEqual(len(names), 16)
        self.assertEqual(len([n for n in names if n.endswith(".sha256")]), 8)
        self.assertIn("Kovelyx-Windows-Portable-v1.1.1164.zip", names)
        self.assertIn("Kovelyx-Linux-x86_64-v1.1.1164.AppImage", names)
        self.assertIn("Kovelyx-TV-v1.1.1164-universal.apk.sha256", names)

    def test_publication_fails_closed_on_missing_corrupt_or_wrong_commit_assets(self):
        import hashlib
        tag = "v1.1.1164"
        sha = "a" * 40
        names = PREVIEW.expected_public_assets(tag)
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            packages = sorted(n for n in names if not n.endswith(".sha256"))
            for name in packages:
                payload = (name + ":verified-by-test").encode("utf-8")
                (output / name).write_bytes(payload)
                (output / (name + ".sha256")).write_text(
                    hashlib.sha256(payload).hexdigest() + "  " + name + "\n",
                    encoding="utf-8")
            def draft():
                return {
                    "tag_name": tag, "draft": True, "target_commitish": sha,
                    "assets": [{
                        "name": p.name, "state": "uploaded",
                        "size": p.stat().st_size, "digest": "sha256:" + PREVIEW.digest(p),
                    } for p in sorted(output.iterdir())],
                }

            PREVIEW.verify_uploaded_release_inventory(tag, sha, output, draft())

            missing = draft()
            missing["assets"].pop()
            with self.assertRaisesRegex(RuntimeError, "Inventaire"):
                PREVIEW.verify_uploaded_release_inventory(tag, sha, output, missing)

            wrong_commit = draft()
            wrong_commit["target_commitish"] = "b" * 40
            with self.assertRaisesRegex(RuntimeError, "commit"):
                PREVIEW.verify_uploaded_release_inventory(tag, sha, output, wrong_commit)

            already_public = draft()
            already_public["draft"] = False
            with self.assertRaisesRegex(RuntimeError, "Brouillon"):
                PREVIEW.verify_uploaded_release_inventory(tag, sha, output, already_public)

            wrong_digest = draft()
            wrong_digest["assets"][0]["digest"] = "sha256:" + "0" * 64
            with self.assertRaisesRegex(RuntimeError, "Empreinte GitHub"):
                PREVIEW.verify_uploaded_release_inventory(tag, sha, output, wrong_digest)

            wrong_size = draft()
            wrong_size["assets"][0]["size"] += 1
            with self.assertRaisesRegex(RuntimeError, "Taille"):
                PREVIEW.verify_uploaded_release_inventory(tag, sha, output, wrong_size)

            wrong_manifest_path = output / (packages[0] + ".sha256")
            wrong_manifest_path.write_text("0" * 64 + "  " + packages[0] + "\n")
            manifest_file = draft()
            with self.assertRaisesRegex(RuntimeError, "mal associée"):
                PREVIEW.verify_uploaded_release_inventory(tag, sha, output, manifest_file)



if __name__ == "__main__":
    unittest.main()
