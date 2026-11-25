import hashlib
import importlib.util
import tempfile
import subprocess
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / "prefetch-robolectric.py"
spec = importlib.util.spec_from_file_location("prefetch", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PrefetchTest(unittest.TestCase):
    def test_download_validation_and_cache_recovery(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            jar = root / "fixture.jar"
            with zipfile.ZipFile(jar, "w") as z:
                z.writestr("test", "sample")
            payload = jar.read_bytes()
            checksum = hashlib.sha512(payload).hexdigest()
            def download(url, dest):
                dest.write_bytes(checksum.encode() if url.endswith(".sha512") else payload)
            version = module.VERSIONS[0]
            with patch.object(module, "download", side_effect=download) as mock:
                module.prefetch(root, version)
                target = next((root / "org").rglob("*.jar"))
                self.assertEqual(target.read_bytes(), payload)
                self.assertEqual(mock.call_count, 2)
                module.prefetch(root, version)
                self.assertEqual(mock.call_count, 2)
                target.write_bytes(b"corrupt")
                module.prefetch(root, version)
                self.assertEqual(mock.call_count, 4)
                self.assertEqual(target.read_bytes(), payload)

    def test_corrupt_download_not_installed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def download(url, dest):
                dest.write_bytes(b"0"*128 if url.endswith(".sha512") else b"corrupt")
            with patch.object(module, "download", side_effect=download):
                with self.assertRaises(ValueError):
                    module.prefetch(root, module.VERSIONS[0])
            self.assertFalse(list(root.rglob("*.jar")))

    def test_mirror_failure_falls_back(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            jar = root / "fixture.jar"
            with zipfile.ZipFile(jar, "w") as z:
                z.writestr("test", "fallback")
            payload = jar.read_bytes()
            checksum = hashlib.sha512(payload).hexdigest()
            def download(url, dest):
                if url.startswith(module.BASES[0]):
                    raise subprocess.CalledProcessError(22, ["curl"])
                dest.write_bytes(checksum.encode() if url.endswith(".sha512") else payload)
            with patch.object(module, "download", side_effect=download) as mock:
                module.prefetch(root, module.VERSIONS[0])
                self.assertEqual(mock.call_count, 3)
                self.assertEqual(next((root / "org").rglob("*.jar")).read_bytes(), payload)
