-- 0002: historique de visionnage distinct de la position de lecture.
-- Nouvelle table additive : ne modifie ni kv_sync ni les bibliothèques existantes.
CREATE TABLE IF NOT EXISTS kv_watched_items (
  owner_id TEXT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
  profile_id TEXT NOT NULL,
  item_key TEXT NOT NULL CHECK(length(item_key) BETWEEN 1 AND 200),
  payload TEXT NOT NULL CHECK(length(payload) <= 32768),
  revision INTEGER NOT NULL DEFAULT 1 CHECK(revision >= 1),
  deleted INTEGER NOT NULL DEFAULT 0 CHECK(deleted IN (0,1)),
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id, profile_id, item_key),
  FOREIGN KEY(owner_id, profile_id) REFERENCES kv_profiles(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_kv_watched_owner_profile
  ON kv_watched_items(owner_id, profile_id, updated_at);
