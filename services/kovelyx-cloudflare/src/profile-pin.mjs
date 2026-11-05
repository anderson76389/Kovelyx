import {jsonResponse,validProfileId} from "./contract.mjs";

const PIN_RE=/^[0-9]{4,8}$/;
const MAX_FAILURES=5;
const LOCK_MS=60_000;

export function validPin(pin) { return typeof pin==="string" && PIN_RE.test(pin); }

/** A stolen D1 export must not permit offline 4-digit PIN enumeration.
 * The HMAC key is the Worker's secret (never the public account token).
 * A separate random salt prevents identical PIN hashes across profiles.
 */
export async function pinHash(secret,userId,profileId,salt,pin) {
  const encoder=new TextEncoder();
  const key=await crypto.subtle.importKey("raw",encoder.encode(secret),
    {name:"HMAC",hash:"SHA-256"},false,["sign"]);
  const signed=await crypto.subtle.sign("HMAC",key,
    encoder.encode("kovelyx-profile-pin-v1\0"+userId+"\0"+profileId+"\0"+salt+"\0"+pin));
  return Array.from(new Uint8Array(signed),b=>b.toString(16).padStart(2,"0")).join("");
}
function equalHex(a,b) {
  if(typeof a!=="string"||typeof b!=="string"||a.length!==64||b.length!==64)return false;
  let diff=0;
  for(let i=0;i<64;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);
  return diff===0;
}
const randomSalt=()=>Array.from(crypto.getRandomValues(new Uint8Array(16)),
  b=>b.toString(16).padStart(2,"0")).join("");
const retrySeconds=(until,now)=>Math.max(0,Math.ceil((until-now)/1000));

async function owned(env,userId,profileId) {
  return env.DB.prepare("SELECT id FROM kv_profiles WHERE id=? AND owner_id=?")
    .bind(profileId,userId).first();
}
async function currentPin(env,userId,profileId) {
  return env.DB.prepare(
    "SELECT salt,pin_hash,failed_attempts,blocked_until FROM kv_profile_pins WHERE owner_id=? AND profile_id=?"
  ).bind(userId,profileId).first();
}
async function checkCurrent(env,user,profileId,row,pin) {
  const now=Date.now();
  if(Number(row.blocked_until)>now)
    return {unlocked:false,retryAfterSeconds:retrySeconds(Number(row.blocked_until),now)};
  if(!validPin(pin))
    return {unlocked:false,retryAfterSeconds:0};
  const expected=await pinHash(env.BETTER_AUTH_SECRET,user.id,profileId,row.salt,pin);
  if(!equalHex(expected,row.pin_hash)) {
    await env.DB.prepare(
      "UPDATE kv_profile_pins SET failed_attempts=failed_attempts+1,"+
      "blocked_until=CASE WHEN failed_attempts+1>=? THEN ? ELSE 0 END "+
      "WHERE owner_id=? AND profile_id=? AND pin_hash=? AND blocked_until<=?"
    ).bind(MAX_FAILURES,now+LOCK_MS,user.id,profileId,row.pin_hash,now).run();
    const latest=await currentPin(env,user.id,profileId);
    return {unlocked:false,retryAfterSeconds:retrySeconds(Number(latest?.blocked_until||0),now)};
  }
  const updated=await env.DB.prepare(
    "UPDATE kv_profile_pins SET failed_attempts=0,blocked_until=0 "+
    "WHERE owner_id=? AND profile_id=? AND pin_hash=?"
  ).bind(user.id,profileId,row.pin_hash).run();
  return {unlocked:Number(updated.meta?.changes||0)===1,retryAfterSeconds:0};
}

/** The route is protected by Better Auth *before* calling this handler. */
export async function handleProfilePin(request,env,user,url,readJson) {
  if(url.pathname==="/v1/profile-pins") {
    if(request.method!=="GET")return jsonResponse({error:"Méthode interdite"},405);
    const found=await env.DB.prepare(
      "SELECT profile_id AS profileId,blocked_until AS blockedUntil FROM kv_profile_pins "+
      "WHERE owner_id=? ORDER BY profile_id"
    ).bind(user.id).all();
    return jsonResponse({profiles:(found.results||[]).map(row=>({
      profileId:row.profileId,pinEnabled:true,blockedUntil:row.blockedUntil
    }))});
  }
  if(!["POST","DELETE"].includes(request.method))
    return jsonResponse({error:"Méthode interdite"},405);
  const input=await readJson(request);
  const profileId=input?.profileId;
  if(!validProfileId(profileId))
    return jsonResponse({error:"Profil invalide"},400);
  if(!await owned(env,user.id,profileId))
    return jsonResponse({error:"Profil introuvable"},404);
  const row=await currentPin(env,user.id,profileId);
  if(url.pathname==="/v1/profile-pin/verify") {
    if(request.method!=="POST"||!validPin(input?.pin))
      return jsonResponse({error:"Code PIN invalide"},400);
    if(!row)return jsonResponse({unlocked:true,retryAfterSeconds:0});
    return jsonResponse(await checkCurrent(env,user,profileId,row,input.pin));
  }
  if(url.pathname!=="/v1/profile-pin")
    return jsonResponse({error:"Route inconnue"},404);

  if(request.method==="DELETE") {
    if(!row)return jsonResponse({removed:true});
    if(!validPin(input?.currentPin))
      return jsonResponse({error:"Code PIN actuel requis",code:"PIN_REQUIRED"},409);
    const verified=await checkCurrent(env,user,profileId,row,input.currentPin);
    if(!verified.unlocked)return jsonResponse({
      error:"Code PIN incorrect",code:"PIN_INCORRECT",
      retryAfterSeconds:verified.retryAfterSeconds
    },403);
    const result=await env.DB.prepare(
      "DELETE FROM kv_profile_pins WHERE owner_id=? AND profile_id=? AND pin_hash=?"
    ).bind(user.id,profileId,row.pin_hash).run();
    if(Number(result.meta?.changes||0)!==1)
      return jsonResponse({error:"Code PIN modifié sur un autre appareil"},409);
    return jsonResponse({removed:true});
  }
  if(!validPin(input?.pin))
    return jsonResponse({error:"Le PIN doit contenir de 4 à 8 chiffres"},400);
  if(row) {
    if(!validPin(input?.currentPin))
      return jsonResponse({error:"Code PIN actuel requis",code:"PIN_REQUIRED"},409);
    const verified=await checkCurrent(env,user,profileId,row,input.currentPin);
    if(!verified.unlocked)return jsonResponse({
      error:"Code PIN incorrect",code:"PIN_INCORRECT",
      retryAfterSeconds:verified.retryAfterSeconds
    },403);
  }
  const salt=randomSalt(),now=Date.now();
  const hash=await pinHash(env.BETTER_AUTH_SECRET,user.id,profileId,salt,input.pin);
  if(row) {
    const result=await env.DB.prepare(
      "UPDATE kv_profile_pins SET salt=?,pin_hash=?,failed_attempts=0,blocked_until=0,updated_at=? "+
      "WHERE owner_id=? AND profile_id=? AND pin_hash=?"
    ).bind(salt,hash,now,user.id,profileId,row.pin_hash).run();
    if(Number(result.meta?.changes||0)!==1)
      return jsonResponse({error:"Code PIN modifié sur un autre appareil"},409);
    return jsonResponse({pinEnabled:true});
  }
  const result=await env.DB.prepare(
    "INSERT OR IGNORE INTO kv_profile_pins "+
    "(owner_id,profile_id,salt,pin_hash,updated_at) VALUES (?,?,?,?,?)"
  ).bind(user.id,profileId,salt,hash,now).run();
  if(Number(result.meta?.changes||0)!==1)
    return jsonResponse({error:"Un code PIN existe déjà, fournir le code actuel"},409);
  return jsonResponse({pinEnabled:true},201);
}
