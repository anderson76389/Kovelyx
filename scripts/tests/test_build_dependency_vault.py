import importlib.util
import tempfile
import unittest
from pathlib import Path
import tarfile

SCRIPT = Path(__file__).resolve().parents[1] / "build-dependency-vault.py"
SPEC = importlib.util.spec_from_file_location("vault", SCRIPT)
vault = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(vault)


class DependencyVaultTest(unittest.TestCase):
    def test_roundtrip_and_tampering(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            gradle = root / "gradle"
            maven = root / "maven"
            paths = [
                gradle / "caches/modules-2/files-2.1/group/module/1/hash/module.jar",
                gradle / "caches/modules-2/cache.lock",
                gradle / "caches/modules-2/gc.properties",
                gradle / "wrapper/dists/gradle-8.13-bin/hash/gradle-8.13/bin/gradle",
                gradle / "gradle.properties",
                maven / "org/robolectric/android-all-instrumented/1/android-all-instrumented-1.jar",
                maven / "private/other.jar",
            ]
            for p in paths:
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(b"fixture")
            bundle = root / "backup"
            package = root / "jdk.zip"
            package.write_bytes(b"offline-installer-fixture")
            vault.capture(bundle, gradle, maven, "test-commit", {"jdk": package})
            meta = vault.verify(bundle)
            self.assertEqual(meta["gradle_versions_found"], ["8.13"])
            self.assertEqual(meta["robolectric_jars"], 1)
            self.assertEqual(meta["toolchain_files"]["jdk"]["sha256"], vault.digest(package))
            self.assertFalse(meta["offline_build_verified"])
            restored = root / "restored"
            vault.restore(bundle, restored)
            self.assertTrue((restored / "gradle-home/caches/modules-2/files-2.1/group/module/1/hash/module.jar").is_file())
            self.assertTrue((restored / "robolectric-jars/android-all-instrumented-1.jar").is_file())
            self.assertFalse((restored / "gradle-home/gradle.properties").exists())
            self.assertFalse((restored / "gradle-home/caches/modules-2/cache.lock").exists())
            self.assertFalse((restored / "gradle-home/caches/modules-2/gc.properties").exists())
            self.assertFalse((restored / "maven-repository/private").exists())
            self.assertEqual((restored / "toolchains/jdk/jdk.zip").read_bytes(), package.read_bytes())
            with self.assertRaises(FileExistsError):
                vault.restore(bundle, restored)
            with (bundle / vault.ARCHIVE).open("ab") as f:
                f.write(b"tamper")
            with self.assertRaises(ValueError):
                vault.verify(bundle)

    def test_path_and_link_rejection(self):
        for name in ("../x", "/absolute", "gradle-home/caches/modules-2/../../evil", "settings.xml"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                vault.validate_member(tarfile.TarInfo(name))
        link = tarfile.TarInfo("gradle-home/caches/modules-2/symlink")
        link.type = tarfile.SYMTYPE
        with self.assertRaises(ValueError):
            vault.validate_member(link)

    def test_missing_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError):
                vault.capture(root / "backup", root / "missing", root / "maven", "test")
            self.assertFalse((root / "backup").exists())


if __name__ == "__main__":
    unittest.main()
