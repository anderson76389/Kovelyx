#!/usr/bin/env python3
"""Check publication provenance and artifact selection without network or writes."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

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
