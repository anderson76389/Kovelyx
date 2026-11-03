"""Restore SQL in isolation and verify published media/catalogue bytes."""
import hashlib
import json
import sqlite3

BASE_TABLES = ('user', 'session', 'account', 'verification', 'rateLimit', 'kv_profiles', 'kv_sync')
MEDIA_TABLES = ('kv_profile_media', 'kv_profile_media_chunks', 'kv_profile_media_uploads', 'kv_profile_media_upload_chunks')


def validate_export(path):
    with sqlite3.connect(':memory:') as db:
        def authorize(action, first, second, *_):
            if action in (sqlite3.SQLITE_ATTACH, sqlite3.SQLITE_DETACH):
                return sqlite3.SQLITE_DENY
            if action == sqlite3.SQLITE_FUNCTION and str(second).lower() in ('load_extension', 'readfile', 'writefile'):
                return sqlite3.SQLITE_DENY
            return sqlite3.SQLITE_OK
        db.set_authorizer(authorize)
        db.executescript(path.read_text(encoding='utf-8'))
        if db.execute('PRAGMA integrity_check').fetchone() != ('ok',):
            raise RuntimeError('Intégrité de la sauvegarde non confirmée.')
        tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if not set(BASE_TABLES + MEDIA_TABLES).issubset(tables):
            raise RuntimeError('Tables de comptes, synchronisation ou médias manquantes.')
        schema = db.execute("SELECT sql FROM sqlite_master WHERE name='kv_profile_media'").fetchone()[0]
        if '18000000' not in schema:
            raise RuntimeError('Le schéma de médias 0004 attendu est absent.')
        if db.execute('PRAGMA foreign_key_check').fetchone() is not None:
            raise RuntimeError('Référence de compte ou de profil invalide.')
        verified_media = 0
        for owner, profile, kind, version, size in db.execute('SELECT owner_id,profile_id,kind,version,size FROM kv_profile_media'):
            chunks = list(db.execute('SELECT position,data FROM kv_profile_media_chunks WHERE owner_id=? AND profile_id=? AND kind=? ORDER BY position', (owner, profile, kind)))
            if not chunks or any(position != i for i, (position, _) in enumerate(chunks)):
                raise RuntimeError('Image ou GIF incomplet dans la sauvegarde.')
            data = b''.join(bytes(chunk) for _, chunk in chunks)
            if len(data) != size or hashlib.sha256(data).hexdigest() != version:
                raise RuntimeError('Les octets sauvegardés d’une image ou d’un GIF ne correspondent pas à son empreinte.')
            verified_media += 1
        verified_catalogues = 0
        legacy_catalogues = 0
        heads = db.execute("SELECT owner_id,profile_id,item_key,payload FROM kv_sync WHERE category='preferences' AND deleted=0 AND item_key IN ('catalogue_collections','catalogue_layout')")
        for owner, profile, key, payload in heads:
            manifest = json.loads(payload)
            if not isinstance(manifest, dict) or manifest.get('schema') != 1:
                legacy_catalogues += 1
                continue
            parts = manifest.get('parts')
            fingerprint = manifest.get('sha256')
            if not isinstance(parts, int) or not 1 <= parts <= 700 or not isinstance(fingerprint, str) or len(fingerprint) != 64:
                raise RuntimeError('Manifeste de catalogue invalide.')
            prefix = 'snapshot:' + key.removeprefix('catalogue_') + ':' + fingerprint + ':'
            fragments = []
            for index in range(parts):
                row = db.execute("SELECT payload FROM kv_sync WHERE owner_id=? AND profile_id=? AND category='catalogues' AND item_key=? AND deleted=0", (owner, profile, prefix + f'{index:06d}')).fetchone()
                if row is None:
                    raise RuntimeError('Un catalogue publié ne possède pas tous ses blocs.')
                chunk = json.loads(row[0])
                if chunk.get('index') != index or not isinstance(chunk.get('text'), str):
                    raise RuntimeError('Bloc de catalogue invalide.')
                fragments.append(chunk['text'])
            # JSON/JS/Kotlin chunks can split a UTF-16 pair across two parts.
            data = ''.join(fragments).encode('utf-16-le', 'surrogatepass').decode('utf-16-le').encode('utf-8')
            if len(data) != manifest.get('utf8Bytes') or hashlib.sha256(data).hexdigest() != fingerprint:
                raise RuntimeError('Le catalogue reconstitué ne correspond pas à son empreinte.')
            json.loads(data)
            verified_catalogues += 1
        counts = {table: db.execute('SELECT count(*) FROM "' + table + '"').fetchone()[0] for table in BASE_TABLES + MEDIA_TABLES}
        return {'tables': counts, 'verified_media': verified_media, 'verified_catalogues': verified_catalogues,
                'legacy_inline_catalogues': legacy_catalogues, 'validation': 'isolated-sqlite-and-sha256'}
