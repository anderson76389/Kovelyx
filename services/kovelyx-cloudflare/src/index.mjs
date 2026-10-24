import { betterAuth } from "better-auth";
import { bearer } from "better-auth/plugins";
import { jsonResponse, validProfileName, validProfileId, validSyncOffset, validSyncInput, isAllowedOrigin } from "./contract.mjs";

function corsHeaders(origin) {
  return origin === "https://anderson76389.github.io"
    ? { "access-control-allow-origin": origin,
        "access-control-allow-headers": "content-type, authorization",
        "access-control-allow-methods": "GET, POST, OPTIONS",
        "access-control-expose-headers": "set-auth-token",
        "vary": "Origin" }
    : {};
}
async function readJson(request) {
  if (Number(request.headers.get("content-length") || 0) > 35000) return null;
  const text = await request.text();
  if (text.length > 35000) return null;
  try { return JSON.parse(text); } catch { return null; }
}
function makeAuth(request, env) {
  if (!env.BETTER_AUTH_SECRET || env.BETTER_AUTH_SECRET.length < 32)
    throw new Error("BETTER_AUTH_SECRET missing or too short");
  return betterAuth({
    database: env.DB,
    baseURL: new URL(request.url).origin,
    secret: env.BETTER_AUTH_SECRET,
    trustedOrigins: ["https://anderson76389.github.io", new URL(request.url).origin],
    emailAndPassword: {
      enabled: true,
      requireEmailVerification: false,
      minPasswordLength: 12,
      maxPasswordLength: 128
    },
    rateLimit: {
      enabled: true,
      window: 60,
      max: 30,
      storage: "database",
      customRules: {
        "/sign-in/email": {window: 60, max: 5},
        "/sign-up/email": {window: 600, max: 3}
      }
    },
    plugins: [bearer()]
  });
}
async function authenticatedUser(auth, request) {
  const bearer = request.headers.get("authorization") || "";
  if (!/^Bearer [a-zA-Z0-9._~+/-]+={0,2}$/.test(bearer)) return null;
  const data = await auth.api.getSession({headers: request.headers});
  return data?.user || null;
}
async function ownedProfile(env, userId, profileId) {
  return env.DB.prepare("SELECT id FROM kv_profiles WHERE id=? AND owner_id=?")
    .bind(profileId, userId).first();
}
async function handleSync(request, env, user, url) {
  if (request.method === "GET") {
    const profileId = url.searchParams.get("profileId") || "";
    if (!validProfileId(profileId) || !await ownedProfile(env, user.id, profileId))
      return jsonResponse({error:"Profil introuvable"},404);
    const requestedOffset = url.searchParams.get("offset") || "0";
    if (!validSyncOffset(requestedOffset)) return jsonResponse({error:"Curseur invalide"},400);
    const offset = Number(requestedOffset);
    const requestedLimit = url.searchParams.get("limit") || "25";
    if (!/^(?:[1-9]|1[0-9]|2[0-5])$/.test(requestedLimit))
      return jsonResponse({error:"Limite invalide"},400);
    const limit = Number(requestedLimit);
    // 25 x 32 KiB fits under the native clients' 1 MiB response ceiling.
    const result = await env.DB.prepare(
      "SELECT category, item_key AS itemKey, payload, revision, deleted, updated_at AS updatedAt "+
      "FROM kv_sync WHERE owner_id=? AND profile_id=? ORDER BY category, item_key LIMIT ? OFFSET ?"
    ).bind(user.id, profileId, limit + 1, offset).all();
    const rows = result.results || [];
    const partial = rows.length > limit;
    return jsonResponse({
      items: rows.slice(0,limit).map(row => ({
        ...row, payload: JSON.parse(row.payload), deleted: row.deleted === 1
      })),
      limit,
      offset,
      nextOffset: partial ? offset + limit : null,
      partial
    });
  }
  if (request.method !== "POST") return jsonResponse({error:"Méthode interdite"},405);
  const body = await readJson(request);
  if (!validSyncInput(body)) return jsonResponse({error:"Données invalides ou trop volumineuses"},400);
  if (!await ownedProfile(env, user.id, body.profileId))
    return jsonResponse({error:"Profil introuvable"},404);
  const now = Date.now();
  const payload = JSON.stringify(body.payload);
  if (body.revision === 0) {
    const inserted = await env.DB.prepare(
      "INSERT OR IGNORE INTO kv_sync "+
      "(owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at) "+
      "VALUES (?,?,?,?,?,1,?,?)"
    ).bind(user.id, body.profileId, body.category, body.itemKey, payload, body.deleted ? 1 : 0, now).run();
    if (inserted.meta?.changes === 1) return jsonResponse({revision:1,updatedAt:now},201);
  } else {
    const updated = await env.DB.prepare(
      "UPDATE kv_sync SET payload=?, revision=revision+1,deleted=?,updated_at=? "+
      "WHERE owner_id=? AND profile_id=? AND category=? AND item_key=? AND revision=?"
    ).bind(payload, body.deleted ? 1 : 0, now, user.id, body.profileId, body.category,
      body.itemKey, body.revision).run();
    if (updated.meta?.changes === 1) return jsonResponse({revision:body.revision+1,updatedAt:now});
  }
  const latest = await env.DB.prepare(
    "SELECT revision,updated_at AS updatedAt FROM kv_sync "+
    "WHERE owner_id=? AND profile_id=? AND category=? AND item_key=?"
  ).bind(user.id, body.profileId, body.category, body.itemKey).first();
  return jsonResponse({error:"Conflit de synchronisation",current:latest},409);
}
async function handleProfiles(request,env,user,url) {
  if (request.method === "GET") {
    const result = await env.DB.prepare(
      "SELECT id,display_name AS displayName,avatar_path AS avatarPath,"+
      "background_path AS backgroundPath,created_at AS createdAt,updated_at AS updatedAt "+
      "FROM kv_profiles WHERE owner_id=? ORDER BY created_at ASC LIMIT 8"
    ).bind(user.id).all();
    return jsonResponse({profiles:result.results || []});
  }
  if (request.method === "PATCH" || request.method === "DELETE") {
    const profileId = url.searchParams.get("profileId");
    if (!validProfileId(profileId) || !await ownedProfile(env, user.id, profileId))
      return jsonResponse({error:"Profil introuvable"},404);
    if (request.method === "PATCH") {
      const body = await readJson(request);
      if (!validProfileName(body?.displayName))
        return jsonResponse({error:"Nom de profil invalide"},400);
      const now = Date.now();
      await env.DB.prepare(
        "UPDATE kv_profiles SET display_name=?,updated_at=? WHERE id=? AND owner_id=?"
      ).bind(body.displayName.trim(),now,profileId,user.id).run();
      return jsonResponse({profile:{id:profileId,displayName:body.displayName.trim(),updatedAt:now}});
    }
    // Keep at least one usable profile; a delete cascades the owned sync data.
    const removed = await env.DB.prepare(
      "DELETE FROM kv_profiles WHERE owner_id=? AND id=? "+
      "AND (SELECT count(*) FROM kv_profiles WHERE owner_id=?)>1"
    ).bind(user.id,profileId,user.id).run();
    if (removed.meta?.changes !== 1)
      return jsonResponse({error:"Le dernier profil ne peut pas être supprimé"},409);
    return jsonResponse({deleted:true,id:profileId});
  }
  if (request.method !== "POST") return jsonResponse({error:"Méthode interdite"},405);
  const body = await readJson(request);
  if (!validProfileName(body?.displayName)) return jsonResponse({error:"Nom de profil invalide"},400);
  const id = crypto.randomUUID();
  const now = Date.now();
  // Single atomic insertion enforces the 8-profile maximum also under concurrency.
  const inserted = await env.DB.prepare(
    "INSERT INTO kv_profiles(id,owner_id,display_name,created_at,updated_at) "+
    "SELECT ?,?,?,?,? WHERE (SELECT count(*) FROM kv_profiles WHERE owner_id=?)<8"
  ).bind(id,user.id,body.displayName.trim(),now,now,user.id).run();
  if (inserted.meta?.changes !== 1) return jsonResponse({error:"Maximum de huit profils"},409);
  return jsonResponse({profile:{id,displayName:body.displayName.trim(),createdAt:now}},201);
}

/** Etat technique du Worker, sans lire de donnees personnelles.
 * /health indique uniquement que le script HTTP repond ; /ready prouve en plus
 * que D1, le secret d'authentification et la migration des oeuvres vues existent.
 * Il ne remplace PAS un test authentifie entre appareils reels.
 */
async function productionReadiness(env) {
  const unavailable = () => jsonResponse({service:"Kovelyx Cloud",status:"not_ready"},503);
  if (!env.DB || !env.BETTER_AUTH_SECRET || env.BETTER_AUTH_SECRET.length < 32)
    return unavailable();
  try {
    const tables = await env.DB.prepare(
      "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('user','session','account','kv_profiles')"
    ).all();
    const found = new Set((tables.results || []).map(row => row.name));
    if (!["user","session","account","kv_profiles"].every(name => found.has(name)))
      return unavailable();
    const sync = await env.DB.prepare(
      "SELECT sql FROM sqlite_master WHERE type='table' AND name='kv_sync'"
    ).first();
    if (typeof sync?.sql !== "string" || !sync.sql.includes("'watched_items'"))
      return unavailable();
    return jsonResponse({
      service:"Kovelyx Cloud",provider:"Cloudflare D1",
      status:"ok",schema:"0002_watched_history"
    });
  } catch {
    return unavailable();
  }
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("origin");
    const workerOrigin = new URL(request.url).origin;
    if (!isAllowedOrigin(origin, workerOrigin)) return jsonResponse({error:"Origine interdite"},403);
    const headers = corsHeaders(origin);
    if (request.method === "OPTIONS")
      return new Response(null,{status:204,headers});
    const respond = result => {
      const out = new Response(result.body,result);
      for (const [key,value] of Object.entries(headers)) out.headers.set(key,value);
      out.headers.set("cache-control","no-store");
      out.headers.set("x-content-type-options","nosniff");
      return out;
    };
    const url = new URL(request.url);
    if (url.pathname === "/health")
      return respond(jsonResponse({service:"Kovelyx Cloud",provider:"Cloudflare D1",status:"ok",version:"0.1.0"}));
    if (url.pathname === "/ready" && request.method === "GET")
      return respond(await productionReadiness(env));
    if (!env.DB) return respond(jsonResponse({error:"D1 non configuré"},503));
    if (!env.BETTER_AUTH_SECRET || env.BETTER_AUTH_SECRET.length < 32)
      return respond(jsonResponse({error:"Secret serveur non configuré"},503));
    try {
      const auth = makeAuth(request,env);
      if (url.pathname.startsWith("/api/auth/")) {
        if (url.pathname === "/api/auth/sign-up/email" &&
            request.method === "POST" && env.KOVELYX_REGISTRATION_OPEN !== "true")
          return respond(jsonResponse({error:"Inscriptions temporairement fermées"},403));
        return respond(await auth.handler(request));
      }
      if (!url.pathname.startsWith("/v1/")) return respond(jsonResponse({error:"Page introuvable"},404));
      const user = await authenticatedUser(auth,request);
      if (!user) return respond(jsonResponse({error:"Authentification requise"},401));
      if (url.pathname === "/v1/me" && request.method === "GET")
        return respond(jsonResponse({id:user.id,email:user.email,name:user.name}));
      if (url.pathname === "/v1/profiles") return respond(await handleProfiles(request,env,user,url));
      if (url.pathname === "/v1/sync") return respond(await handleSync(request,env,user,url));
      return respond(jsonResponse({error:"Route inconnue"},404));
    } catch (error) {
      console.error("Kovelyx Cloud request failed", {path:url.pathname,kind:error?.name || "Error"});
      return respond(jsonResponse({error:"Erreur du serveur Kovelyx"},500));
    }
  }
};
