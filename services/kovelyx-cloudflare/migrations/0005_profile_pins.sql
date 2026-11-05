-- Profile PINs are private Better Auth / Cloudflare D1 data.
-- Never put PIN material in kv_sync, which is downloadable by account devices.
-- Hashes use server-secret HMAC-SHA256 plus a random per-profile salt.
CREATE TABLE IF NOT EXISTS kv_profile_pins (
  owner_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  salt TEXT NOT NULL CHECK(length(salt) = 32),
  pin_hash TEXT NOT NULL CHECK(length(pin_hash) = 64),
  failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK(failed_attempts >= 0),
  blocked_until INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id, profile_id),
  FOREIGN KEY(owner_id, profile_id)
    REFERENCES kv_profiles(owner_id, id) ON DELETE CASCADE
);
