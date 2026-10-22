#!/usr/bin/env python3
"""Tests SQL locaux. Ne remplace pas un test Cloudflare D1 ni Better Auth réel."""
from pathlib import Path
import sqlite3
import unittest

migration = Path(__file__).parents[1] / "migrations/0001_accounts_and_sync.sql"
watched_migration = Path(__file__).parents[1] / "migrations/0002_watched_items.sql"


class CloudflareDatabase(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript(migration.read_text(encoding="utf-8"))
        self.db.executescript(watched_migration.read_text(encoding="utf-8"))
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
                         "rateLimit","kv_profiles","kv_sync","kv_watched_items"} <= tables)

    def test_owner_and_profile_foreign_keys(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                "VALUES ('bob','alice-profile','favorites','x','{}',1)"
            )

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

    def test_watched_isolated_and_kept_independently_from_progress(self):
        watched_sql = (
            "INSERT INTO kv_watched_items "
            "(owner_id,profile_id,item_key,payload,revision,updated_at) "
            "VALUES (?,?,?,?,1,?)"
        )
        self.db.execute(
            watched_sql,
            ("alice","alice-profile","film-123",
             '{"item":{"contentId":"film-123","watchedAt":10}}',10)
        )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(watched_sql,("bob","alice-profile","film-456","{}",11))
        change = self.db.execute(
            "UPDATE kv_watched_items SET deleted=1,revision=revision+1 "
            "WHERE owner_id=? AND profile_id=? AND item_key=? AND revision=?",
            ("alice","alice-profile","film-123",1)
        )
        self.assertEqual(change.rowcount,1)
        stale = self.db.execute(
            "UPDATE kv_watched_items SET deleted=0,revision=revision+1 "
            "WHERE owner_id=? AND profile_id=? AND item_key=? AND revision=?",
            ("alice","alice-profile","film-123",1)
        )
        self.assertEqual(stale.rowcount,0)
        # Une purge de la progression ne détruit pas le statut vu.
        self.db.execute(
            "DELETE FROM kv_sync WHERE owner_id=? AND profile_id=?",
            ("alice","alice-profile")
        )
        self.assertEqual(
            self.db.execute(
                "SELECT count(*) FROM kv_watched_items WHERE owner_id=? AND deleted=1",
                ("alice",)
            ).fetchone()[0],1
        )

    def test_no_credentials_category(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                "VALUES ('alice','alice-profile','passwords','x','{}',1)"
            )


if __name__ == "__main__":
    unittest.main()
