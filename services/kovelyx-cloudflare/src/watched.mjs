import { jsonResponse } from "./contract.mjs";

const UUID = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i;
const MAX_ITEMS_PER_PAGE = 100;

// L'historique vu est indépendant de watch_progress : terminer un film/épisode
// ne doit pas dépendre de la conservation de sa position de lecture.
export function watchedKey(item) {
  if (!item || typeof item.contentId !== "string") return null;
  const id = item.contentId;
  if (!id || id.length > 150 || /\s|\?|#|\/|\\/.test(id)) return null;
  const hasSeason = Number.isSafeInteger(item.season) && item.season >= 0;
  const hasEpisode = Number.isSafeInteger(item.episode) && item.episode >= 0;
  if (item.season != null && !hasSeason || item.episode != null && !hasEpisode ||
      hasSeason !== hasEpisode) return null;
  const key = hasSeason ? `${id}_s${item.season}e${item.episode}` : id;
  return key.length <= 200 ? key : null;
}

export function validWatchedMutation(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return false;
  if (typeof body.profileId !== "string" || !UUID.test(body.profileId)) return false;
  if (typeof body.itemKey !== "string" || body.itemKey.length < 1 || body.itemKey.length > 200) return false;
  if (!Number.isSafeInteger(body.revision) || body.revision < 0) return false;
  if (typeof body.deleted !== "boolean") return false;
  if (!body.payload || typeof body.payload !== "object" || Array.isArray(body.payload)) return false;
  if (JSON.stringify(body.payload).length > 32000) return false;
  if (body.deleted) return Object.keys(body.payload).length === 0;
  if (Object.keys(body.payload).length !== 1) return false;
  const item = body.payload.item;
  if (!item || typeof item !== "object" || Array.isArray(item)) return false;
  if (Object.keys(item).some(key =>
    !["contentId","contentType","title","season","episode","watchedAt"].includes(key))) return false;
  if (!["movie","series","tv"].includes(item.contentType)) return false;
  if (typeof item.title !== "string" || item.title.trim().length < 1 ||
      item.title.length > 300) return false;
  if (!Number.isSafeInteger(item.watchedAt) || item.watchedAt <= 0) return false;
  return watchedKey(item) === body.itemKey;
}

async function belongsToUser(env, userId, profileId) {
  return env.DB.prepare("SELECT id FROM kv_profiles WHERE owner_id=? AND id=?")
    .bind(userId,profileId).first();
}

export async function handleWatched(request, env, user, url) {
  const profileId = request.method === "GET"
    ? url.searchParams.get("profileId") : null;
  if (request.method === "GET") {
    if (!profileId || !UUID.test(profileId) ||
        !await belongsToUser(env,user.id,profileId))
      return jsonResponse({error:"Profil introuvable"},404);
    const cursor = url.searchParams.get("after") || "";
    const requestedLimit = Number(url.searchParams.get("limit") || "100");
    if (cursor.length > 200 || !Number.isSafeInteger(requestedLimit) ||
        requestedLimit < 1 || requestedLimit > MAX_ITEMS_PER_PAGE)
      return jsonResponse({error:"Pagination invalide"},400);
    const rows = await env.DB.prepare(
      "SELECT item_key AS itemKey,payload,revision,deleted,updated_at AS updatedAt "+
      "FROM kv_watched_items WHERE owner_id=? AND profile_id=? AND item_key>? "+
      "ORDER BY item_key LIMIT ?"
    ).bind(user.id,profileId,cursor,requestedLimit+1).all();
    const results = rows.results || [];
    const page = results.slice(0,requestedLimit);
    return jsonResponse({
      items:page.map(item=>({
        ...item,payload:JSON.parse(item.payload),deleted:item.deleted===1
      })),
      partial:results.length>requestedLimit,
      limit:requestedLimit,
      nextCursor:results.length>requestedLimit ? page[page.length-1].itemKey : null
    });
  }
  if (request.method !== "POST") return jsonResponse({error:"Méthode interdite"},405);
  const raw = await request.text();
  if (raw.length > 35000) return jsonResponse({error:"Données trop volumineuses"},400);
  let body;
  try { body = JSON.parse(raw); } catch { return jsonResponse({error:"JSON invalide"},400); }
  if (!validWatchedMutation(body)) return jsonResponse({error:"Historique de visionnage invalide"},400);
  if (!await belongsToUser(env,user.id,body.profileId))
    return jsonResponse({error:"Profil introuvable"},404);
  const now = Date.now();
  const payload = JSON.stringify(body.payload);
  if (body.revision === 0) {
    const result = await env.DB.prepare(
      "INSERT OR IGNORE INTO kv_watched_items "+
      "(owner_id,profile_id,item_key,payload,revision,deleted,updated_at) "+
      "VALUES(?,?,?,?,1,?,?)"
    ).bind(user.id,body.profileId,body.itemKey,payload,body.deleted?1:0,now).run();
    if (result.meta?.changes === 1)
      return jsonResponse({revision:1,updatedAt:now},201);
  } else {
    const result = await env.DB.prepare(
      "UPDATE kv_watched_items SET payload=?,revision=revision+1,deleted=?,updated_at=? "+
      "WHERE owner_id=? AND profile_id=? AND item_key=? AND revision=?"
    ).bind(payload,body.deleted?1:0,now,user.id,body.profileId,body.itemKey,body.revision).run();
    if (result.meta?.changes === 1)
      return jsonResponse({revision:body.revision+1,updatedAt:now});
  }
  const current = await env.DB.prepare(
    "SELECT revision,updated_at AS updatedAt FROM kv_watched_items "+
    "WHERE owner_id=? AND profile_id=? AND item_key=?"
  ).bind(user.id,body.profileId,body.itemKey).first();
  return jsonResponse({error:"Conflit de synchronisation",current},409);
}
