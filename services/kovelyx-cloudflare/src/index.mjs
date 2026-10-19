import { betterAuth } from "better-auth";
import { bearer } from "better-auth/plugins";
import { CATEGORIES, jsonResponse, validProfileName, validSyncInput, isAllowedOrigin } from "./contract.mjs";
import {handleProfileMedia} from "./media.mjs";

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
    trustedOrigins: [new URL(request.url).origin, "https://anderson76389.github.io"],
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
export async function handleSync(request, env, user, url) {
  if (request.method === "GET") {
    const profileId = url.searchParams.get("profileId") || "";
    if (!/^[a-f0-9-]{36}$/i.test(profileId) || !await ownedProfile(env, user.id, profileId))
      return jsonResponse({error:"Profil introuvable"},404);
    const requestedCategory = url.searchParams.get("category");
    if (requestedCategory !== null) {
      if (!CATEGORIES.has(requestedCategory)) return jsonResponse({error:"Catégorie invalide"},400);
      const cursor = url.searchParams.get("after") || "";
      const requestedLimit = Number(url.searchParams.get("limit") || "100");
      if (cursor.length > 200 || !Number.isSafeInteger(requestedLimit) ||
          requestedLimit < 1 || requestedLimit > 100)
        return jsonResponse({error:"Pagination invalide"},400);
      const result = await env.DB.prepare(
        "SELECT category, item_key AS itemKey, payload, revision, deleted, updated_at AS updatedAt "+
        "FROM kv_sync WHERE owner_id=? AND profile_id=? AND category=? AND item_key>? "+
        "ORDER BY item_key LIMIT ?"
      ).bind(user.id,profileId,requestedCategory,cursor,requestedLimit+1).all();
      const rows = result.results || [];
      const page = rows.slice(0,requestedLimit);
      return jsonResponse({
        items: page.map(row => ({
          ...row, payload:JSON.parse(row.payload), deleted:row.deleted===1
        })),
        limit:requestedLimit,
        partial:rows.length>requestedLimit,
        nextCursor:rows.length>requestedLimit ? page[page.length-1].itemKey : null
      });
    }
    // Legacy endpoint remains usable for older diagnostic clients only.
    const result = await env.DB.prepare(
      "SELECT category, item_key AS itemKey, payload, revision, deleted, updated_at AS updatedAt "+
      "FROM kv_sync WHERE owner_id=? AND profile_id=? ORDER BY updated_at, category, item_key LIMIT 500"
    ).bind(user.id, profileId).all();
    return jsonResponse({
      items: (result.results || []).map(row => ({
        ...row, payload: JSON.parse(row.payload), deleted: row.deleted === 1
      })),
      limit: 500,
      partial: (result.results || []).length === 500
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
export async function handleProfiles(request,env,user,url) {
  const route = url.pathname;
  // Toute mutation reste limitée au compte authentifié.
  if (route === "/v1/profiles/update" || route === "/v1/profiles/delete") {
    if (request.method !== "POST") return jsonResponse({error:"Méthode interdite"},405);
    const body = await readJson(request);
    if (!body || typeof body.profileId !== "string" ||
        !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(body.profileId))
      return jsonResponse({error:"Identifiant de profil invalide"},400);
    if (route === "/v1/profiles/update") {
      if (!validProfileName(body.displayName))
        return jsonResponse({error:"Nom de profil invalide"},400);
      const name = body.displayName.trim();
      const now = Date.now();
      const updated = await env.DB.prepare(
        "UPDATE kv_profiles SET display_name=?,updated_at=? WHERE id=? AND owner_id=?"
      ).bind(name,now,body.profileId,user.id).run();
      if (updated.meta?.changes !== 1) return jsonResponse({error:"Profil introuvable"},404);
      return jsonResponse({profile:{id:body.profileId,displayName:name,updatedAt:now}});
    }
    // Suppression explicite seulement : les lignes kv_sync du profil sont effacées
    // par la contrainte ON DELETE CASCADE. Le dernier profil est préservé.
    const deleted = await env.DB.prepare(
      "DELETE FROM kv_profiles WHERE id=? AND owner_id=? " +
      "AND (SELECT count(*) FROM kv_profiles WHERE owner_id=?)>1 " +
      "AND id!=(SELECT id FROM kv_profiles WHERE owner_id=? ORDER BY created_at,id LIMIT 1)"
    ).bind(body.profileId,user.id,user.id,user.id).run();
    if (deleted.meta?.changes === 1) return jsonResponse({deleted:true});
    if (!await ownedProfile(env,user.id,body.profileId))
      return jsonResponse({error:"Profil introuvable"},404);
    return jsonResponse({error:"Le profil principal ou dernier profil ne peut pas être supprimé"},409);
  }
  if (route !== "/v1/profiles") return jsonResponse({error:"Route inconnue"},404);
  if (request.method === "GET") {
    const result = await env.DB.prepare(
      "SELECT id,display_name AS displayName,avatar_path AS avatarPath,"+
      "background_path AS backgroundPath,created_at AS createdAt,updated_at AS updatedAt "+
      "FROM kv_profiles WHERE owner_id=? ORDER BY created_at ASC LIMIT 8"
    ).bind(user.id).all();
    return jsonResponse({profiles:result.results || []});
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

export default {
  async fetch(request, env) {
    const origin = request.headers.get("origin");
    if (!isAllowedOrigin(origin, new URL(request.url).origin))
      return jsonResponse({error:"Origine interdite"},403);
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
      if (url.pathname === "/v1/profiles" || url.pathname === "/v1/profiles/update" ||
          url.pathname === "/v1/profiles/delete")
        return respond(await handleProfiles(request,env,user,url));
      if (url.pathname === "/v1/media") return respond(await handleProfileMedia(request,env,user,url));
      if (url.pathname === "/v1/sync") return respond(await handleSync(request,env,user,url));
      return respond(jsonResponse({error:"Route inconnue"},404));
    } catch (error) {
      console.error("Kovelyx Cloud request failed", {path:url.pathname,kind:error?.name || "Error"});
      return respond(jsonResponse({error:"Erreur du serveur Kovelyx"},500));
    }
  }
};
