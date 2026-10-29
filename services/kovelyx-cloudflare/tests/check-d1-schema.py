#!/usr/bin/env python3
"""Tests SQL locaux. Ne remplace pas un test Cloudflare D1 ni Better Auth réel."""
from pathlib import Path
import sqlite3
import unittest

migrations = sorted((Path(__file__).parents[1] / "migrations").glob("*.sql"))


class CloudflareDatabase(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        for migration in migrations:
            self.db.executescript(migration.read_text(encoding="utf-8"))
        for user in ("alice", "bob"):
            self.db.execute(
                'INSERT INTO "user" (id,name,email,emailVerified,createdAt,updatedAt) VALUES (?,?,?,?,?,?)',
                (user,user,f"{user}@example.invalid",1,1,1),
            )
            self.db.execute(
                "INSERT INTO kv_profiles (id,owner_id,display_name,created_at,updated_at) VALUES (?,?,?,?,?)",
                (f"{user}-profile",user,"Profil",1,1),
            )

    def tearDown(self):
        self.db.close()

    def test_required_tables(self):
        tables = {x[0] for x in self.db.execute(
            "SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertTrue({"user","session","account","verification",
                         "rateLimit","kv_profiles","kv_sync"} <= tables)

    def test_owner_and_profile_foreign_keys(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                "VALUES ('bob','alice-profile','favorites','x','{}',1)"
            )

    def test_private_media_is_owned_chunked_and_cascades(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO kv_profile_media VALUES (?,?,?,?,?,?,?)", ("bob", "alice-profile", "avatar", "v", "image/png", 3, 1))
        self.db.execute("INSERT INTO kv_profile_media VALUES (?,?,?,?,?,?,?)", ("alice", "alice-profile", "avatar", "v", "image/png", 3, 1))
        self.db.execute("INSERT INTO kv_profile_media_chunks VALUES (?,?,?,?,?)", ("alice", "alice-profile", "avatar", 0, b"abc"))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO kv_profile_media_chunks VALUES (?,?,?,?,?)", ("alice", "alice-profile", "avatar", 1, b"a" * 524289))
        self.db.execute("DELETE FROM kv_profiles WHERE id='alice-profile'")
        self.assertEqual(self.db.execute("SELECT count(*) FROM kv_profile_media").fetchone()[0], 0)
        self.assertEqual(self.db.execute("SELECT count(*) FROM kv_profile_media_chunks").fetchone()[0], 0)

    def test_revision_conflict_query(self):
        self.db.execute(
            "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,revision,updated_at) "
            "VALUES ('alice','alice-profile','favorites','item','{}',1,1)"
        )
        success = self.db.execute(
            "UPDATE kv_sync SET revision=revision+1,payload='{}' "
            "WHERE owner_id=? AND profile_id=? AND category=? AND item_key=? AND revision=?",
            ("alice","alice-profile","favorites","item",1)
        )
        self.assertEqual(success.rowcount,1)
        stale = self.db.execute(
            "UPDATE kv_sync SET revision=revision+1,payload='{}' "
            "WHERE owner_id=? AND profile_id=? AND category=? AND item_key=? AND revision=?",
            ("alice","alice-profile","favorites","item",1)
        )
        self.assertEqual(stale.rowcount,0)

    def test_watched_history_category_and_parent_cascade(self):
        self.db.execute(
            "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
            "VALUES ('alice','alice-profile','watched_items','series:100:1:2','{}',1)"
        )
        self.db.execute("DELETE FROM kv_profiles WHERE owner_id='alice'")
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM kv_sync WHERE owner_id='alice'").fetchone()[0], 0
        )

    def test_migration_preserves_existing_rows(self):
        self.db.execute(
            "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,revision,updated_at) "
            "VALUES ('alice','alice-profile','library','movie:one','{}',4,1)"
        )
        self.assertEqual(
            self.db.execute(
                "SELECT revision FROM kv_sync WHERE owner_id='alice' AND category='library'"
            ).fetchone()[0], 4
        )

    def test_no_credentials_category(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                "VALUES ('alice','alice-profile','passwords','x','{}',1)"
            )


if __name__ == "__main__":
    unittest.main()
