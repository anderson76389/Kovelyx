-- Kovelyx Cloudflare v1. Independent of Supabase and Nuvio.
-- Better Auth core SQL tables: Better Auth is responsible for password hashing and sessions.
-- All Kovelyx data is isolated with authenticated user IDs in every query.
CREATE TABLE IF NOT EXISTS "user" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "name" TEXT NOT NULL,
  "email" TEXT NOT NULL UNIQUE,
  "emailVerified" INTEGER NOT NULL DEFAULT 0,
  "image" TEXT,
  "createdAt" INTEGER NOT NULL,
  "updatedAt" INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS "session" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "userId" TEXT NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
  "token" TEXT NOT NULL UNIQUE,
  "expiresAt" INTEGER NOT NULL,
  "ipAddress" TEXT,
  "userAgent" TEXT,
  "createdAt" INTEGER NOT NULL,
  "updatedAt" INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_kv_session_user ON "session"("userId");
CREATE TABLE IF NOT EXISTS "account" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "userId" TEXT NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
  "accountId" TEXT NOT NULL,
  "providerId" TEXT NOT NULL,
  "accessToken" TEXT,
  "refreshToken" TEXT,
  "accessTokenExpiresAt" INTEGER,
  "refreshTokenExpiresAt" INTEGER,
  "scope" TEXT,
  "idToken" TEXT,
  "password" TEXT,
  "createdAt" INTEGER NOT NULL,
  "updatedAt" INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_kv_account_user ON "account"("userId");
CREATE TABLE IF NOT EXISTS "verification" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "identifier" TEXT NOT NULL,
  "value" TEXT NOT NULL,
  "expiresAt" INTEGER NOT NULL,
  "createdAt" INTEGER NOT NULL,
  "updatedAt" INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_kv_verification_identifier ON "verification"("identifier");

CREATE TABLE IF NOT EXISTS kv_profiles (
  id TEXT PRIMARY KEY NOT NULL,
  owner_id TEXT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL CHECK(length(display_name) BETWEEN 1 AND 64),
  avatar_path TEXT,
  background_path TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_kv_profiles_owner ON kv_profiles(owner_id);

CREATE TABLE IF NOT EXISTS kv_sync (
  owner_id TEXT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
  profile_id TEXT NOT NULL REFERENCES kv_profiles(id) ON DELETE CASCADE,
  category TEXT NOT NULL CHECK(category IN ('library','favorites','watch_progress','catalogues','preferences','profile_settings','addons','plugins')),
  item_key TEXT NOT NULL CHECK(length(item_key) BETWEEN 1 AND 200),
  payload TEXT NOT NULL CHECK(length(payload) <= 32768),
  revision INTEGER NOT NULL DEFAULT 1,
  deleted INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id, profile_id, category, item_key)
);
CREATE INDEX IF NOT EXISTS idx_kv_sync_by_owner ON kv_sync(owner_id, profile_id, updated_at);
