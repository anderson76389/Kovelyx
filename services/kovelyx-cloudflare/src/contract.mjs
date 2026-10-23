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
export function validSyncInput(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return false;
  if (!validProfileId(body.profileId)) return false;
  if (!CATEGORIES.has(body.category)) return false;
  if (typeof body.itemKey !== "string" || body.itemKey.length < 1 || body.itemKey.length > 200) return false;
  if (typeof body.revision !== "number" || !Number.isSafeInteger(body.revision) || body.revision < 0) return false;
  if (typeof body.deleted !== "boolean") return false;
  if (!body.payload || typeof body.payload !== "object" || Array.isArray(body.payload)) return false;
  if (JSON.stringify(body.payload).length > 32000) return false;
  // External provider credentials are not synchronized without explicit encryption and consent.
  const serialized = JSON.stringify(body.payload);
  if (/(?:\"(?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|client[_-]?secret|private[_-]?key)\"\s*:)/i.test(serialized)) return false;
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
