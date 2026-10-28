export const CATEGORIES = new Set([
  "library", "favorites", "watch_progress", "catalogues",
  "preferences", "profile_settings", "addons", "plugins", "watched_items"
]);
export function validProfileId(value) {
  return typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
export function validSyncOffset(value) {
  return typeof value === "string" && /^(0|[1-9][0-9]{0,5})$/.test(value) &&
    Number(value) <= 999999;
}
export function validProfileName(value) {
  return typeof value === "string" && value.trim().length >= 1 &&
    value.trim().length <= 64;
}
// Check nested object keys AND JSON-serialized strings (older profile settings
// can contain a stringified Trakt/debrid config). Fail closed for excessive nesting.
const sensitiveField = /(apikey|accesstoken|refreshtoken|password|secret|authorization|privatekey|bearertoken|sessiontoken)/i;
function hasUnencryptedCredential(value, depth = 0) {
  if (depth > 12) return true;
  if (Array.isArray(value)) return value.some(item => hasUnencryptedCredential(item, depth + 1));
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      try { return hasUnencryptedCredential(JSON.parse(trimmed), depth + 1); }
      catch { return false; }
    }
    return false;
  }
  if (!value || typeof value !== "object") return false;
  return Object.entries(value).some(([key, data]) =>
    sensitiveField.test(key.replace(/[^a-z0-9]/gi, "")) ||
      hasUnencryptedCredential(data, depth + 1));
}
export function validSyncInput(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return false;
  if (!validProfileId(body.profileId)) return false;
  if (!CATEGORIES.has(body.category)) return false;
  if (typeof body.itemKey !== "string" || body.itemKey.length < 1 || body.itemKey.length > 200) return false;
  if (typeof body.revision !== "number" || !Number.isSafeInteger(body.revision) || body.revision < 0) return false;
  if (typeof body.deleted !== "boolean") return false;
  if (!body.payload || typeof body.payload !== "object" || Array.isArray(body.payload)) return false;
  if (JSON.stringify(body.payload).length > 32000) return false;
  // Private provider credentials must never enter D1 without E2E encryption.
  if (hasUnencryptedCredential(body.payload)) return false;
  return true;
}
/**
 * Native Android/TV clients may send the Worker's own Origin, while the website
 * uses GitHub Pages. An arbitrary third-party Origin must never be trusted.
 * A missing Origin is allowed for native HTTP clients (not browser CORS).
 */
export function isAllowedOrigin(origin, workerOrigin = "") {
  return !origin ||
    origin === "https://anderson76389.github.io" ||
    ((workerOrigin.startsWith("https://") ||
      /^http:\/\/(?:127\.0\.0\.1|localhost)(?::[0-9]{2,5})?$/.test(workerOrigin)) &&
      origin === workerOrigin);
}
export function jsonResponse(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store", "x-content-type-options": "nosniff", ...extraHeaders }
  });
}
