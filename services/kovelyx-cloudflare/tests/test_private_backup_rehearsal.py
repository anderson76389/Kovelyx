"""Prove that encrypted D1 backups can be privately decrypted and restored.

This test uses a disposable RSA certificate. It NEVER accesses production.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from backup_validation import validate_export

spec = importlib.util.spec_from_file_location(
    "private_backup_check", ROOT / "scripts" / "verify-private-backup.py"
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


@unittest.skipUnless(shutil.which("openssl"), "OpenSSL requis pour ce test")
class PrivateBackupRehearsal(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="kovelyx-private-backup-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.key = self.root / "throwaway-private.pem"
        self.cert = self.root / "throwaway-public.pem"
        subprocess.run([
            "openssl", "req", "-x509", "-newkey", "rsa:2048",
            "-keyout", str(self.key), "-out", str(self.cert),
            "-nodes", "-days", "1", "-subj", "/CN=KovelyxTestOnly",
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=45)
        self.directory = self.root / "encrypted"
        self.directory.mkdir()
        plain = self.root / "fixture.sql"
        with sqlite3.connect(":memory:") as db:
            for sql in sorted((ROOT / "migrations").glob("*.sql")):
                db.executescript(sql.read_text(encoding="utf-8"))
            db.execute('INSERT INTO "user" (id,name,email,createdAt,updatedAt) VALUES (?,?,?,?,?)',
                       ("a", "Fixture", "fixture@example.invalid", 1, 1))
            db.execute(
                "INSERT INTO kv_profiles (id,owner_id,display_name,created_at,updated_at) "
                "VALUES (?,?,?,?,?)", ("p", "a", "Principal", 1, 1)
            )
            db.execute(
                "INSERT INTO kv_profile_pins(owner_id,profile_id,salt,pin_hash,updated_at) "
                "VALUES (?,?,?,?,?)", ("a", "p", "a" * 32, "b" * 64, 1)
            )
            db.execute(
                "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                "VALUES (?,?,?,?,?,?)",
                ("a", "p", "library", "movie:fixture",
                 '{"id":"fixture","type":"movie"}', 1)
            )
            plain.write_text("\n".join(db.iterdump()) + "\n", encoding="utf-8")
        encrypted = self.directory / "kovelyx-d1.sql.cms"
        subprocess.run([
            "openssl", "cms", "-encrypt", "-binary", "-aes-256-gcm",
            "-in", str(plain), "-out", str(encrypted), "-outform", "DER",
            str(self.cert),
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=45)
        manifest = {
            "format": "CMS-AES-256-GCM-RSA4096",
            "validation_status": "passed",
            "sql_sha256": sha(plain),
            "encrypted_sha256": sha(encrypted),
            "certificate_sha256": sha(self.cert),
            "counts": validate_export(plain),
        }
        self.manifest_file = self.directory / "manifest.json"
        self.manifest_file.write_text(json.dumps(manifest), encoding="utf-8")

    def test_decrypt_restore_and_compare_every_table(self):
        result = module.verify_artifact(self.directory, self.key, self.cert)
        self.assertTrue(result["restored"])
        self.assertGreaterEqual(result["tables_verified"], 12)

    def test_corrupted_ciphertext_is_rejected(self):
        encrypted = self.directory / "kovelyx-d1.sql.cms"
        encrypted.write_bytes(encrypted.read_bytes()[:-1] + b"X")
        with self.assertRaises(ValueError):
            module.verify_artifact(self.directory, self.key, self.cert)

    def test_missing_pin_in_manifest_is_rejected(self):
        manifest = json.loads(self.manifest_file.read_text())
        manifest["counts"]["tables"].pop("kv_profile_pins")
        self.manifest_file.write_text(json.dumps(manifest))
        with self.assertRaises(ValueError):
            module.verify_artifact(self.directory, self.key, self.cert)

    def test_missing_private_key_is_rejected(self):
        with self.assertRaises(FileNotFoundError):
            module.verify_artifact(self.directory, self.root / "absent.pem", self.cert)


if __name__ == "__main__":
    unittest.main()
