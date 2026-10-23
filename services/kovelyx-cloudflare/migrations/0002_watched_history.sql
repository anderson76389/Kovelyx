-- Extends the sync category whitelist without deleting existing user data.
-- D1/SQLite supports this transactional table replacement; no existing rows lost.
CREATE TABLE kv_sync_v2 (
  owner_id TEXT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
  profile_id TEXT NOT NULL,
  category TEXT NOT NULL CHECK(category IN
    ('library','favorites','watch_progress','watched_items','catalogues',
     'preferences','profile_settings','addons','plugins')),
  item_key TEXT NOT NULL CHECK(length(item_key) BETWEEN 1 AND 200),
  payload TEXT NOT NULL CHECK(length(payload) <= 32768),
  revision INTEGER NOT NULL DEFAULT 1,
  deleted INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id, profile_id, category, item_key),
  FOREIGN KEY(owner_id, profile_id) REFERENCES kv_profiles(owner_id, id) ON DELETE CASCADE
);
INSERT INTO kv_sync_v2 (
 owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at
)
SELECT owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at
FROM kv_sync;
DROP TABLE kv_sync;
ALTER TABLE kv_sync_v2 RENAME TO kv_sync;
CREATE INDEX idx_kv_sync_by_owner ON kv_sync(owner_id, profile_id, updated_at);
