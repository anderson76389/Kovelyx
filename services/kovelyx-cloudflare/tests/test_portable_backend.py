"""Offline safety tests for the portable SQLite database preparer."""
import importlib.util
from pathlib import Path
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("kovelyx_portable_prepare",
                                               ROOT / "portable" / "prepare.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PortableDatabaseTests(unittest.TestCase):
    def test_create_restore_no_overwrite_or_data_loss(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            original = base / "first.sqlite"
            initial = module.create_database(original)
            self.assertIn("kv_profile_pins", initial)
            self.assertIn("kv_sync", initial)
            self.assertEqual(initial["user"], 0)
            with sqlite3.connect(original) as db:
                db.execute('INSERT INTO "user" (id,name,email,createdAt,updatedAt) VALUES (?,?,?,?,?)',
                           ("account-1", "Local", "offline@example.test", 1, 1))
                db.commit()
                export = base / "export.sql"
                export.write_text("\n".join(db.iterdump()), encoding="utf-8")
            restored = base / "restored.sqlite"
            result = module.create_database(restored, export)
            self.assertEqual(result["user"], 1)
            with sqlite3.connect(restored) as db:
                self.assertEqual(db.execute('SELECT email FROM "user"').fetchone()[0],
                                 "offline@example.test")
                self.assertEqual(db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
            with self.assertRaises(FileExistsError):
                module.create_database(restored)
            with sqlite3.connect(restored) as db:
                self.assertEqual(db.execute('SELECT count(*) FROM "user"').fetchone()[0], 1)

    def test_invalid_export_never_creates_database(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            bad = base / "invalid.sql"
            bad.write_text('CREATE TABLE "user" (id TEXT);', encoding="utf-8")
            target = base / "must-not-exist.sqlite"
            with self.assertRaises((RuntimeError, ValueError)):
                module.create_database(target, bad)
            self.assertFalse(target.exists())

    def test_database_file_is_private_on_posix(self):
        import os
        if os.name == "nt":
            self.skipTest("Windows uses file ACLs")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "local.sqlite"
            module.create_database(path)
            self.assertEqual(path.stat().st_mode & 0o077, 0)


if __name__ == "__main__":
    unittest.main()
