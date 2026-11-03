import hashlib
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from backup_validation import validate_export


class BackupValidation(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        for migration in sorted((ROOT / 'migrations').glob('*.sql')):
            self.db.executescript(migration.read_text())
        self.db.execute('INSERT INTO "user" (id,name,email,createdAt,updatedAt) VALUES (?,?,?,?,?)', ('test', 'Test', 'test@example.invalid', 1, 1))
        self.db.execute('INSERT INTO kv_profiles (id,owner_id,display_name,created_at,updated_at) VALUES (?,?,?,?,?)', ('profile', 'test', 'Test', 1, 1))
        image = b'GIF89a' + bytes(range(256)) * 3000
        self.db.execute('INSERT INTO kv_profile_media VALUES (?,?,?,?,?,?,?)', ('test', 'profile', 'avatar', hashlib.sha256(image).hexdigest(), 'image/gif', len(image), 1))
        for index, offset in enumerate(range(0, len(image), 524288)):
            self.db.execute('INSERT INTO kv_profile_media_chunks VALUES (?,?,?,?,?)', ('test', 'profile', 'avatar', index, image[offset:offset + 524288]))
        text = '{"name":"Collection \U0001f600","items":[1,2]}'
        data = text.encode()
        self.catalogue_hash = hashlib.sha256(data).hexdigest()
        prefix = 'snapshot:collections:' + self.catalogue_hash + ':'
        # Deliberately split an emoji between UTF-16 code units.
        encoded = text.encode('utf-16-le')
        position = encoded.index(b'=\xd8') + 2
        fragments = [encoded[:position].decode('utf-16-le', 'surrogatepass'), encoded[position:].decode('utf-16-le', 'surrogatepass')]
        for i, fragment in enumerate(fragments):
            self.insert('catalogues', prefix + f'{i:06d}', {'index': i, 'text': fragment})
        self.insert('preferences', 'catalogue_collections', {'schema': 1, 'sha256': self.catalogue_hash, 'parts': 2, 'utf8Bytes': len(data)})

    def tearDown(self):
        self.db.close()

    def insert(self, category, key, payload):
        self.db.execute('INSERT INTO kv_sync VALUES (?,?,?,?,?,?,?,?)', ('test', 'profile', category, key, json.dumps(payload), 1, 0, 1))

    def validate(self):
        with tempfile.TemporaryDirectory() as directory:
            sql = Path(directory) / 'restore.sql'
            sql.write_text('\n'.join(self.db.iterdump()), encoding='utf-8')
            return validate_export(sql)

    def test_full_restore_checks_gif_bytes_and_unicode_catalogue(self):
        result = self.validate()
        self.assertEqual(result['verified_media'], 1)
        self.assertEqual(result['verified_catalogues'], 1)
        self.assertEqual(result['tables']['user'], 1)

    def test_missing_image_chunk_refuses_backup(self):
        self.db.execute('DELETE FROM kv_profile_media_chunks WHERE position=1')
        with self.assertRaises(RuntimeError):
            self.validate()

    def test_same_size_changed_image_refuses_backup(self):
        self.db.execute('UPDATE kv_profile_media_chunks SET data=zeroblob(length(data)) WHERE position=0')
        with self.assertRaises(RuntimeError):
            self.validate()

    def test_missing_published_catalogue_chunk_refuses_backup(self):
        self.db.execute("DELETE FROM kv_sync WHERE category='catalogues' AND item_key LIKE '%000001'")
        with self.assertRaises(RuntimeError):
            self.validate()

    def test_changed_catalogue_payload_refuses_backup(self):
        self.db.execute("UPDATE kv_sync SET payload=? WHERE category='catalogues' AND item_key LIKE '%000000'", (json.dumps({'index': 0, 'text': 'changed'}),))
        with self.assertRaises((RuntimeError, UnicodeError)):
            self.validate()

    def test_incomplete_unpublished_upload_does_not_hide_valid_media(self):
        self.db.execute('INSERT INTO kv_profile_media_uploads VALUES (?,?,?,?,?)', ('pending', 'test', 'profile', 'background', 1))
        self.db.execute('INSERT INTO kv_profile_media_upload_chunks VALUES (?,?,?)', ('pending', 0, b'pending'))
        self.assertEqual(self.validate()['verified_media'], 1)


if __name__ == '__main__':
    unittest.main()
