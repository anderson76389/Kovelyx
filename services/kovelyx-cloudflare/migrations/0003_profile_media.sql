-- Private, small profile pictures only. No public URLs and no external Nuvio storage.
CREATE TABLE IF NOT EXISTS kv_profile_media (
  owner_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('avatar','background')),
  version TEXT NOT NULL,
  mime TEXT NOT NULL CHECK(mime IN ('image/jpeg','image/png','image/webp','image/gif')),
  size INTEGER NOT NULL CHECK(size BETWEEN 1 AND 2097152),
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id,profile_id,kind),
  FOREIGN KEY(owner_id,profile_id) REFERENCES kv_profiles(owner_id,id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS kv_profile_media_chunks (
  owner_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  kind TEXT NOT NULL,
  position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 3),
  data BLOB NOT NULL CHECK(length(data) BETWEEN 1 AND 524288),
  PRIMARY KEY(owner_id,profile_id,kind,position),
  FOREIGN KEY(owner_id,profile_id,kind) REFERENCES kv_profile_media(owner_id,profile_id,kind) ON DELETE CASCADE
);
