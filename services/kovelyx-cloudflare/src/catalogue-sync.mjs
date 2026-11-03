import {validProfileId,validSyncInput,jsonResponse} from './contract.mjs';

export const CATALOGUE_CHUNK_CHARS=6000;
export const CATALOGUE_MAX_BYTES=4_000_000;
export const CATALOGUE_MAX_PARTS=700;
export const catalogueKinds=new Set(['collections','layout']);
const hashPattern=/^[a-f0-9]{64}$/;
export function cataloguePrefix(kind,hash) { return `snapshot:${kind}:${hash}:`; }
export function validCatalogueChunk(body) {
  const match=/^snapshot:(collections|layout):([a-f0-9]{64}):(\d{6})$/.exec(body?.itemKey||'');
  const payload=body?.payload;
  return !!match && body.revision===0 && body.deleted===false &&
    Number(match[3])<CATALOGUE_MAX_PARTS && payload &&
    payload.index===Number(match[3]) && typeof payload.text==='string' &&
    payload.text.length>0 && payload.text.length<=CATALOGUE_CHUNK_CHARS;
}

export async function handleCatalogueChunk(request,env,user,body) {
  if(request.method!=='POST')return jsonResponse({error:'Méthode interdite'},405);
  if(!validSyncInput(body) || body.category!=='catalogues' || !validCatalogueChunk(body))
    return jsonResponse({error:'Bloc de catalogue invalide'},400);
  const owned=await env.DB.prepare('SELECT id FROM kv_profiles WHERE owner_id=? AND id=?')
    .bind(user.id,body.profileId).first();
  if(!owned)return jsonResponse({error:'Profil introuvable'},404);
  const payload=JSON.stringify(body.payload),now=Date.now();
  const result=await env.DB.prepare(`INSERT OR IGNORE INTO kv_sync
    (owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at)
    VALUES (?,?,'catalogues',?,?,1,0,?)`).bind(user.id,body.profileId,body.itemKey,payload,now).run();
  if(result.meta?.changes===1)return jsonResponse({revision:1,updatedAt:now},201);
  const old=await env.DB.prepare(`SELECT payload,deleted FROM kv_sync
    WHERE owner_id=? AND profile_id=? AND category='catalogues' AND item_key=?`)
    .bind(user.id,body.profileId,body.itemKey).first();
  const data=old?.payload ? JSON.parse(old.payload) : null;
  if(old?.deleted===0 && data?.index===body.payload.index && data?.text===body.payload.text)
    return jsonResponse({revision:1},200);
  return jsonResponse({error:'Bloc de catalogue différent ; conserver les données locales'},409);
}

export async function handleCatalogueManifest(request,env,user,body) {
  if(request.method!=='POST')return jsonResponse({error:'Méthode interdite'},405);
  if(!body || !validProfileId(body.profileId) || !catalogueKinds.has(body.kind) ||
     !hashPattern.test(body.sha256||'') || !Number.isSafeInteger(body.parts) ||
     body.parts<1 || body.parts>CATALOGUE_MAX_PARTS ||
     !Number.isSafeInteger(body.utf8Bytes) || body.utf8Bytes<1 || body.utf8Bytes>CATALOGUE_MAX_BYTES ||
     !Number.isSafeInteger(body.revision) || body.revision<0)
    return jsonResponse({error:'Catalogue invalide ou trop volumineux'},400);
  const owned=await env.DB.prepare('SELECT id FROM kv_profiles WHERE owner_id=? AND id=?')
    .bind(user.id,body.profileId).first();
  if(!owned)return jsonResponse({error:'Profil introuvable'},404);
  const key='catalogue_'+body.kind,prefix=cataloguePrefix(body.kind,body.sha256),now=Date.now();
  const payload=JSON.stringify({schema:1,sha256:body.sha256,parts:body.parts,utf8Bytes:body.utf8Bytes});
  // D1 limits LIKE/GLOB patterns: use indexed key ranges, including the full SHA-256.
  // Count and CAS are one statement: cleanup cannot publish a partial snapshot.
  const complete=`(SELECT count(*) FROM kv_sync WHERE owner_id=? AND profile_id=?
    AND category='catalogues' AND deleted=0 AND item_key>=? AND item_key<?
    AND CAST(substr(item_key,length(?)+1) AS INTEGER) BETWEEN 0 AND ?)=?`;
  let result;
  if(body.revision===0) {
    result=await env.DB.prepare(`INSERT OR IGNORE INTO kv_sync
      (owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at)
      SELECT ?,?,'preferences',?,?,1,0,? WHERE ${complete}`)
      .bind(user.id,body.profileId,key,payload,now,user.id,body.profileId,prefix,prefix+'~',prefix,body.parts-1,body.parts).run();
  } else {
    result=await env.DB.prepare(`UPDATE kv_sync SET payload=?,revision=revision+1,deleted=0,updated_at=?
      WHERE owner_id=? AND profile_id=? AND category='preferences' AND item_key=? AND revision=? AND ${complete}`)
      .bind(payload,now,user.id,body.profileId,key,body.revision,user.id,body.profileId,prefix,prefix+'~',prefix,body.parts-1,body.parts).run();
  }
  if(result.meta?.changes!==1)return jsonResponse({error:'Conflit ou transfert incomplet ; reprendre la synchronisation'},409);
  // Resolve the CURRENT head in this statement, not the head captured by this request.
  // Concurrent writers may retry missing chunks, but never expose partial files.
  const kindPrefix='snapshot:'+body.kind+':';
  await env.DB.prepare(`DELETE FROM kv_sync WHERE owner_id=? AND profile_id=? AND category='catalogues'
    AND item_key>=? AND item_key<? AND substr(item_key,length(?)+1,64) <>
      (SELECT json_extract(payload,'$.sha256') FROM kv_sync WHERE owner_id=? AND profile_id=?
        AND category='preferences' AND item_key=? AND deleted=0)`)
    .bind(user.id,body.profileId,kindPrefix,kindPrefix+'~',kindPrefix,user.id,body.profileId,key).run();
  return jsonResponse({revision:body.revision+1,updatedAt:now},body.revision===0?201:200);
}
