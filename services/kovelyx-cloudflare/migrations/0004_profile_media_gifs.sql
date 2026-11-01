-- Keep every existing image and chunk while raising the private-file limit.
-- D1 executes each migration transactionally. Accounts and profile rows are unchanged.
CREATE TABLE kv_profile_media_v4 (
  owner_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('avatar','background')),
  version TEXT NOT NULL,
  mime TEXT NOT NULL CHECK(mime IN ('image/jpeg','image/png','image/webp','image/gif')),
  size INTEGER NOT NULL CHECK(size BETWEEN 1 AND 18000000),
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id,profile_id,kind),
  FOREIGN KEY(owner_id,profile_id) REFERENCES kv_profiles(owner_id,id) ON DELETE CASCADE
);
CREATE TABLE kv_profile_media_chunks_v4 (
  owner_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  kind TEXT NOT NULL,
  position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 34),
  data BLOB NOT NULL CHECK(length(data) BETWEEN 1 AND 524288),
  PRIMARY KEY(owner_id,profile_id,kind,position),
  FOREIGN KEY(owner_id,profile_id,kind) REFERENCES kv_profile_media_v4(owner_id,profile_id,kind) ON DELETE CASCADE
);
INSERT INTO kv_profile_media_v4 SELECT * FROM kv_profile_media;
INSERT INTO kv_profile_media_chunks_v4 SELECT * FROM kv_profile_media_chunks;
DROP TABLE kv_profile_media_chunks;
DROP TABLE kv_profile_media;
ALTER TABLE kv_profile_media_v4 RENAME TO kv_profile_media;
ALTER TABLE kv_profile_media_chunks_v4 RENAME TO kv_profile_media_chunks;

-- Upload in small batches, then publish the complete file atomically.
CREATE TABLE kv_profile_media_uploads (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('avatar','background')),
  updated_at INTEGER NOT NULL,
  FOREIGN KEY(owner_id,profile_id) REFERENCES kv_profiles(owner_id,id) ON DELETE CASCADE
);
CREATE TABLE kv_profile_media_upload_chunks (
  upload_id TEXT NOT NULL,
  position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 34),
  data BLOB NOT NULL CHECK(length(data) BETWEEN 1 AND 524288),
  PRIMARY KEY(upload_id,position),
  FOREIGN KEY(upload_id) REFERENCES kv_profile_media_uploads(id) ON DELETE CASCADE
);
