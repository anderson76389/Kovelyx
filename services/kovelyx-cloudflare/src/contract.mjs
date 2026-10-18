export const CATEGORIES = new Set([
  "library", "favorites", "watch_progress", "catalogues",
  "preferences", "profile_settings", "addons", "plugins"
]);
export function validProfileName(value) {
  return typeof value === "string" && value.trim().length >= 1 &&
    value.trim().length <= 64;
}
export function validSyncInput(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return false;
  if (typeof body.profileId !== "string" || !/^[a-f0-9-]{36}$/i.test(body.profileId)) return false;
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
export function isAllowedOrigin(origin) {
  return !origin || origin === "https://anderson76389.github.io";
}
export function jsonResponse(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store", "x-content-type-options": "nosniff", ...extraHeaders }
  });
}
