-- A monotonic per-profile cursor, independent of wall-clock time.
-- All old clients' writes also receive a cursor, including catalogue manifests.
-- Catalogues' immutable chunks are fetched by their content hash, not by delta.
ALTER TABLE kv_sync ADD COLUMN sync_seq INTEGER NOT NULL DEFAULT 0;
UPDATE kv_sync SET sync_seq=rowid WHERE category <> 'catalogues';
CREATE INDEX idx_kv_sync_cursor ON kv_sync(owner_id,profile_id,sync_seq);
CREATE TRIGGER kv_sync_cursor_insert AFTER INSERT ON kv_sync
WHEN NEW.category <> 'catalogues'
BEGIN
  UPDATE kv_sync SET sync_seq=(
    SELECT COALESCE(MAX(sync_seq),0)+1 FROM kv_sync
    WHERE owner_id=NEW.owner_id AND profile_id=NEW.profile_id
  ) WHERE rowid=NEW.rowid;
END;
CREATE TRIGGER kv_sync_cursor_update
AFTER UPDATE OF payload,revision,deleted,updated_at ON kv_sync
WHEN NEW.category <> 'catalogues'
BEGIN
  UPDATE kv_sync SET sync_seq=(
    SELECT COALESCE(MAX(sync_seq),0)+1 FROM kv_sync
    WHERE owner_id=NEW.owner_id AND profile_id=NEW.profile_id
  ) WHERE rowid=NEW.rowid;
END;
