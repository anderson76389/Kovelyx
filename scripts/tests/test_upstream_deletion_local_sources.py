#!/usr/bin/env python3
"""Prove that legacy one-time import is NOT used by current Kovelyx builds.

The fixture blocks git at process level and keeps three local source folders.
It never contacts Nuvio or modifies a real user checkout.
"""
from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class UpstreamGoneTests(unittest.TestCase):
    def test_all_three_apps_and_native_engine_are_locally_preserved(self) -> None:
        for app in ("tv", "mobile", "desktop"):
            root = ROOT / "apps" / app
            self.assertTrue((root / "LICENSE").is_file(), app)
            self.assertTrue((root / ".kovelyx-source.txt").is_file(), app)
            sources = list(root.rglob("*.kt"))
            self.assertGreater(len(sources), 50, f"Missing locally vendored sources for {app}")
        for relative in (
            "vendor/engine/LICENSE",
            "vendor/engine/CMakeLists.txt",
            "vendor/engine/src/core/engine.cpp",
            "apps/desktop/MPVKit/LICENSE",
            "apps/mobile/MPVKit/LICENSE",
        ):
            self.assertTrue((ROOT / relative).is_file(), f"Missing local source: {relative}")

    def test_existing_importer_never_calls_nuvio_or_git_for_complete_tree(self) -> None:
        with tempfile.TemporaryDirectory(prefix="kovelyx-upstream-removed-") as tmp:
            work = Path(tmp)
            (work / "scripts").mkdir()
            shutil.copyfile(ROOT / "scripts/import-upstream.sh", work / "scripts/import-upstream.sh")
            for app in ("tv", "mobile", "desktop"):
                folder = work / "apps" / app
                folder.mkdir(parents=True)
                (folder / "source-marker.txt").write_text(f"preserved-{app}", encoding="utf-8")
            bin_dir = work / "bin"
            bin_dir.mkdir()
            marker = work / "unexpected-git-call.txt"
            git = bin_dir / "git"
            git.write_text(
                '#!/bin/sh\nprintf "unexpected remote git: %s\\n" "$*" >> "$KOVELYX_UPSTREAM_GIT_LOG"\nexit 91\n',
                encoding="utf-8",
            )
            git.chmod(0o755)
            env = os.environ.copy()
            env["KOVELYX_UPSTREAM_GIT_LOG"] = str(marker)
            env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
            run = subprocess.run(
                ["bash", str(work / "scripts/import-upstream.sh")],
                cwd=work, env=env, capture_output=True, text=True, timeout=30,
            )
            self.assertEqual(0, run.returncode, f"{run.stdout}\n{run.stderr}")
            self.assertFalse(marker.exists(), "Importer called git even with sources present")
            for app in ("tv", "mobile", "desktop"):
                self.assertEqual(
                    f"preserved-{app}",
                    (work / "apps" / app / "source-marker.txt").read_text(encoding="utf-8"),
                )
            self.assertIn("aucun écrasement", run.stdout)

    def test_build_scripts_do_not_fetch_nuviomedia(self) -> None:
        paths = [
            ROOT / "apps" / name / "settings.gradle.kts"
            for name in ("tv", "mobile", "desktop")
        ]
        paths += [
            ROOT / "apps" / name / "composeApp" / "build.gradle.kts"
            for name in ("mobile", "desktop")
        ]
        paths += [ROOT / "apps/tv/app/build.gradle.kts"]
        for path in paths:
            self.assertTrue(path.is_file(), str(path))
            self.assertNotIn("NuvioMedia", path.read_text(encoding="utf-8"), str(path))


if __name__ == "__main__":
    unittest.main()
