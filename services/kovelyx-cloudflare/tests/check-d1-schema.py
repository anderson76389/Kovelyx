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
                         "rateLimit","kv_profiles","kv_sync","kv_profile_pins"} <= tables)

    def test_gif_upgrade_preserves_existing_images_bytes_and_foreign_keys(self):
        with sqlite3.connect(":memory:") as db:
            db.execute("PRAGMA foreign_keys=ON")
            for migration in migrations:
                if migration.name.startswith("0004_"):
                    break
                db.executescript(migration.read_text(encoding="utf-8"))
            db.execute('INSERT INTO "user" (id,name,email,emailVerified,createdAt,updatedAt) VALUES (?,?,?,?,?,?)',
                       ('existing','Existing','existing@example.invalid',1,1,1))
            db.execute('INSERT INTO kv_profiles (id,owner_id,display_name,created_at,updated_at) VALUES (?,?,?,?,?)',
                       ('existing-profile','existing','Profil',1,1))
            for kind in ('avatar','background'):
                db.execute('INSERT INTO kv_profile_media VALUES (?,?,?,?,?,?,?)',
                           ('existing','existing-profile',kind,'version-'+kind,'image/gif',3,1))
                db.execute('INSERT INTO kv_profile_media_chunks VALUES (?,?,?,?,?)',
                           ('existing','existing-profile',kind,0,b'GIF'))
            before = {table:list(db.execute('SELECT * FROM '+table)) for table in ('user','kv_profiles','kv_profile_media','kv_profile_media_chunks')}
            db.executescript(next(m for m in migrations if m.name.startswith('0004_')).read_text(encoding='utf-8'))
            for table, rows in before.items():
                self.assertEqual(list(db.execute('SELECT * FROM '+table)), rows)
            self.assertEqual(list(db.execute('PRAGMA foreign_key_check')), [])
            db.execute('UPDATE kv_profile_media SET size=18000000')
            db.execute('INSERT INTO kv_profile_media_chunks VALUES (?,?,?,?,?)',
                       ('existing','existing-profile','avatar',34,b'last-chunk'))
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute('UPDATE kv_profile_media SET size=18000001')
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute('INSERT INTO kv_profile_media_chunks VALUES (?,?,?,?,?)',
                           ('existing','existing-profile','avatar',35,b'overflow'))
            db.execute("DELETE FROM kv_profiles WHERE id='existing-profile'")
            self.assertEqual(db.execute('SELECT count(*) FROM kv_profile_media_chunks').fetchone()[0], 0)

    def test_pin_upgrade_preserves_existing_account_sync_and_media(self):
        # Simulate the real 0004 -> 0005 upgrade of an existing account.
        # This does not touch or authenticate against the production D1.
        with sqlite3.connect(":memory:") as db:
            db.execute("PRAGMA foreign_keys=ON")
            for migration in migrations:
                if migration.name.startswith("0005_"):
                    break
                db.executescript(migration.read_text(encoding="utf-8"))
            db.execute(
                'INSERT INTO "user" (id,name,email,emailVerified,createdAt,updatedAt) VALUES (?,?,?,?,?,?)',
                ("existing", "Owner", "existing@example.invalid", 1, 1, 1),
            )
            db.execute(
                "INSERT INTO kv_profiles (id,owner_id,display_name,created_at,updated_at) VALUES (?,?,?,?,?)",
                ("existing-profile", "existing", "Principal", 1, 1),
            )
            db.execute(
                "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,revision,updated_at) "
                "VALUES (?,?,?,?,?,?,?)",
                ("existing", "existing-profile", "library", "movie:keep", '{"id":"keep"}', 7, 1),
            )
            db.execute(
                "INSERT INTO kv_profile_media VALUES (?,?,?,?,?,?,?)",
                ("existing", "existing-profile", "avatar", "v1", "image/png", 3, 1),
            )
            db.execute(
                "INSERT INTO kv_profile_media_chunks VALUES (?,?,?,?,?)",
                ("existing", "existing-profile", "avatar", 0, b"PNG"),
            )
            before = {
                table: list(db.execute("SELECT * FROM " + table))
                for table in ("user", "kv_profiles", "kv_sync", "kv_profile_media",
                              "kv_profile_media_chunks")
            }
            db.executescript(
                next(m for m in migrations if m.name.startswith("0005_"))
                .read_text(encoding="utf-8")
            )
            for table, rows in before.items():
                self.assertEqual(list(db.execute("SELECT * FROM " + table)), rows,
                                 "Migration 0005 must not alter " + table)
            self.assertEqual(list(db.execute("PRAGMA foreign_key_check")), [])
            db.execute(
                "INSERT INTO kv_profile_pins(owner_id,profile_id,salt,pin_hash,updated_at) VALUES (?,?,?,?,?)",
                ("existing", "existing-profile", "a" * 32, "b" * 64, 2),
            )
            self.assertEqual(
                db.execute("SELECT count(*) FROM kv_profile_pins").fetchone()[0], 1
            )

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

    def test_delta_migration_backfills_without_touching_old_records(self):
        with sqlite3.connect(":memory:") as db:
            for migration in migrations:
                if migration.name.startswith("0006_"):
                    break
                db.executescript(migration.read_text(encoding="utf-8"))
            db.execute('INSERT INTO "user" (id,name,email,createdAt,updatedAt) VALUES (?,?,?,?,?)',
                       ("old","Old","old@example.test",1,1))
            db.execute("INSERT INTO kv_profiles (id,owner_id,display_name,created_at,updated_at) VALUES (?,?,?,?,?)",
                       ("profile","old","Main",1,1))
            db.execute("INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at) "
                       "VALUES ('old','profile','watched_items','series:keep:1:1','{}',7,1,123)")
            before = list(db.execute("SELECT owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at FROM kv_sync"))
            db.executescript(next(m for m in migrations if m.name.startswith("0006_")).read_text(encoding="utf-8"))
            self.assertEqual(before, list(db.execute("SELECT owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at FROM kv_sync")))
            self.assertGreater(db.execute("SELECT sync_seq FROM kv_sync").fetchone()[0],0)
            self.assertEqual(list(db.execute("PRAGMA foreign_key_check")),[])

    def test_delta_cursor_is_monotonic_even_for_same_clock_and_legacy_writes(self):
        for category, key in (("watched_items","a"),("watch_progress","b"),("preferences","head")):
            self.db.execute("INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                            "VALUES (?,?,?,?,?,?)", ("alice","alice-profile",category,key,"{}",10))
        high = self.db.execute("SELECT max(sync_seq) FROM kv_sync WHERE owner_id='alice'").fetchone()[0]
        self.db.execute("UPDATE kv_sync SET deleted=1,revision=revision+1,updated_at=10 WHERE owner_id='alice' AND item_key='a'")
        delta = list(self.db.execute("SELECT item_key,deleted,sync_seq FROM kv_sync WHERE owner_id='alice' AND profile_id='alice-profile' AND sync_seq>? ORDER BY sync_seq",(high,)))
        self.assertEqual(delta,[("a",1,high+1)])
        self.db.execute("INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                        "VALUES ('alice','alice-profile','catalogues','chunk','{}',10)")
        self.assertEqual(self.db.execute("SELECT sync_seq FROM kv_sync WHERE item_key='chunk'").fetchone()[0],0)
        self.db.execute("INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                        "VALUES ('bob','bob-profile','library','b','{}',10)")
        self.assertEqual(self.db.execute("SELECT sync_seq FROM kv_sync WHERE owner_id='bob'").fetchone()[0],1)
        self.assertEqual(self.db.execute("SELECT max(sync_seq) FROM kv_sync WHERE owner_id='alice'").fetchone()[0],high+1)

    def test_private_pin_table_is_owned_and_cascades(self):
        self.db.execute(
            "INSERT INTO kv_profile_pins(owner_id,profile_id,salt,pin_hash,updated_at) "
            "VALUES (?,?,?,?,?)",
            ("alice","alice-profile","a"*32,"b"*64,1),
        )
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO kv_profile_pins(owner_id,profile_id,salt,pin_hash,updated_at) "
                "VALUES (?,?,?,?,?)",
                ("bob","alice-profile","a"*32,"b"*64,1),
            )
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM kv_profile_pins").fetchone()[0], 1
        )
        self.db.execute("DELETE FROM kv_profiles WHERE owner_id='alice'")
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM kv_profile_pins").fetchone()[0], 0
        )

    def test_no_credentials_category(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                "INSERT INTO kv_sync(owner_id,profile_id,category,item_key,payload,updated_at) "
                "VALUES ('alice','alice-profile','passwords','x','{}',1)"
            )


if __name__ == "__main__":
    unittest.main()
