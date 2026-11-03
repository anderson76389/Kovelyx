#!/usr/bin/env python3
"""Exercise APK discovery and replacement without a real signing key or SDK."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "sign-ci-debug-apk.sh"


class ApkSigningTests(unittest.TestCase):
    def test_bash_syntax(self):
        subprocess.run(["bash", "-n", str(SCRIPT)], check=True, capture_output=True)

    def check_signing(self, platform, filenames):
        with tempfile.TemporaryDirectory(prefix="kovelyx-signing-") as directory:
            root = Path(directory)
            relative = ("apps/tv/app" if platform == "tv" else "apps/mobile/androidApp")
            output = root / relative / "build/outputs/apk/full/debug"
            output.mkdir(parents=True)
            for name in filenames:
                (output / name).write_text("original fixture", encoding="utf-8")
            tools = root / "sdk with spaces/build-tools/36.0.0"
            tools.mkdir(parents=True)
            signer = tools / "apksigner"
            signer.write_text('''#!/usr/bin/env python3
import sys
from pathlib import Path
args = sys.argv[1:]
if args[0] == "sign":
    assert args[args.index("--ks-pass") + 1] == "env:KOVELYX_RELEASE_STORE_PASSWORD"
    assert args[args.index("--key-pass") + 1] == "env:KOVELYX_RELEASE_KEY_PASSWORD"
    assert Path(args[-1]).read_text() == "original fixture"
    Path(args[args.index("--out") + 1]).write_text("signed fixture")
elif args[0] == "verify":
    assert Path(args[-1]).read_text() == "signed fixture"
else:
    raise SystemExit(1)
''', encoding="utf-8")
            signer.chmod(0o755)
            app_id = f"com.kovelyx.{platform}.debug"
            aapt = tools / "aapt"
            aapt.write_text(
                "#!/usr/bin/env python3\nprint(" + repr(f"package: name='{app_id}'") + ")\n",
                encoding="utf-8",
            )
            aapt.chmod(0o755)
            environment = os.environ.copy()
            environment.update(
                ANDROID_HOME=str(root / "sdk with spaces"), RUNNER_TEMP=directory,
                GITHUB_STEP_SUMMARY=str(root / "summary"),
                KOVELYX_RELEASE_STORE_FILE=str(root / "fake.jks"),
                KOVELYX_RELEASE_STORE_PASSWORD="fixture-only-store",
                KOVELYX_RELEASE_KEY_PASSWORD="fixture-only-key",
                KOVELYX_RELEASE_KEY_ALIAS="fixture",
            )
            result = subprocess.run(
                ["bash", str(SCRIPT), platform], cwd=root, env=environment,
                text=True, capture_output=True,
            )
            if not filenames:
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Aucun APK", result.stdout)
                return
            self.assertEqual(0, result.returncode, result.stderr)
            for name in filenames:
                self.assertEqual("signed fixture", (output / name).read_text())
            self.assertEqual(len(filenames), len((root / "summary").read_text().splitlines()))
            self.assertNotIn("fixture-only-store", result.stdout + result.stderr)
            self.assertNotIn("fixture-only-key", result.stdout + result.stderr)

    def test_tv_signs_universal_and_all_four_abi_apks(self):
        self.check_signing("tv", [
            "app-full-universal-debug.apk", "app-full-arm64-v8a-debug.apk",
            "app-full-armeabi-v7a-debug.apk", "app-full-x86-debug.apk",
            "app full x86_64 debug.apk",
        ])

    def test_mobile_signs_single_apk(self):
        self.check_signing("mobile", ["app-full-debug.apk"])

    def test_missing_apks_fail_instead_of_claiming_success(self):
        self.check_signing("tv", [])


if __name__ == "__main__":
    unittest.main()
