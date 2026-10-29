import {validProfileId,jsonResponse} from './contract.mjs';
export const MAX_PROFILE_MEDIA_BYTES = 2 * 1024 * 1024;
const CHUNK_BYTES = 512 * 1024;
export function profileImageMime(bytes) {
  if (bytes.length >= 8 && [137,80,78,71,13,10,26,10].every((v,i)=>bytes[i]===v)) return 'image/png';
  if (bytes.length >= 3 && bytes[0]===255 && bytes[1]===216 && bytes[2]===255) return 'image/jpeg';
  const text = new TextDecoder().decode(bytes.slice(0,12));
  if (text.startsWith('GIF87a') || text.startsWith('GIF89a')) return 'image/gif';
  if (text.startsWith('RIFF') && text.slice(8,12)==='WEBP') return 'image/webp';
  return null;
}
async function limitedBytes(request) {
  if (Number(request.headers.get('content-length')) > MAX_PROFILE_MEDIA_BYTES) return null;
  const reader = request.body?.getReader();
  if (!reader) return null;
  const chunks=[];let size=0;
  try {
    while(true) {
      const {done,value}=await reader.read();if(done)break;
      size+=value.length;
      if(size>MAX_PROFILE_MEDIA_BYTES){await reader.cancel();return null;}
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  if(!size)return null;
  const all=new Uint8Array(size);let offset=0;
  for(const chunk of chunks){all.set(chunk,offset);offset+=chunk.length;}
  return all;
}
export async function handleProfileMedia(request,env,user,url) {
  const profileId=url.searchParams.get('profileId'),kind=url.searchParams.get('kind');
  if(!validProfileId(profileId)||!['avatar','background'].includes(kind))
    return jsonResponse({error:'Image de profil invalide'},400);
  const owned=await env.DB.prepare('SELECT id FROM kv_profiles WHERE owner_id=? AND id=?')
    .bind(user.id,profileId).first();
  if(!owned)return jsonResponse({error:'Profil introuvable'},404);
  const row=await env.DB.prepare('SELECT version,mime,size FROM kv_profile_media WHERE owner_id=? AND profile_id=? AND kind=?')
    .bind(user.id,profileId,kind).first();
  if(request.method==='GET') {
    if(!row)return jsonResponse({error:'Image absente'},404);
    const expected=url.searchParams.get('version');
    if(expected && expected!==row.version)return jsonResponse({error:'Image modifiée, actualiser le profil'},409);
    const chunks=await env.DB.prepare('SELECT data FROM kv_profile_media_chunks WHERE owner_id=? AND profile_id=? AND kind=? ORDER BY position')
      .bind(user.id,profileId,kind).all();
    const bytes=new Uint8Array(row.size);let offset=0;
    for(const chunk of chunks.results||[]) {
      const data=new Uint8Array(chunk.data);
      if(offset+data.length>bytes.length)return jsonResponse({error:'Image incomplète'},503);
      bytes.set(data,offset);offset+=data.length;
    }
    if(offset!==row.size)return jsonResponse({error:'Image incomplète'},503);
    return new Response(bytes,{headers:{'Content-Type':row.mime,'Content-Length':String(row.size),
      'X-Kovelyx-Media-Version':row.version,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
  }
  if(!['PUT','DELETE'].includes(request.method))return jsonResponse({error:'Méthode interdite'},405);
  const expected=request.headers.get('if-match');
  if(expected!==(row?.version || 'none'))return jsonResponse({error:'Image modifiée par un autre appareil'},409);
  const column=kind==='avatar'?'avatar_path':'background_path';
  // Recheck the expected version within the transaction, not only before reading the body.
  const now=Date.now();
  if(request.method==='DELETE') {
    const result=await env.DB.batch([
      env.DB.prepare('DELETE FROM kv_profile_media WHERE owner_id=? AND profile_id=? AND kind=? AND version=?')
        .bind(user.id,profileId,kind,row?.version||''),
      env.DB.prepare(`UPDATE kv_profiles SET ${column}=NULL,updated_at=? WHERE owner_id=? AND id=? AND ${column}=?`)
        .bind(now,user.id,profileId,row?.version||'')
    ]);
    if(row && result[0].meta?.changes!==1)return jsonResponse({error:"Image modifiée par un autre appareil"},409);
    return jsonResponse({version:null});
  }
  const bytes=await limitedBytes(request);
  if(!bytes)return jsonResponse({error:'Image vide ou supérieure à 2 Mio'},413);
  const mime=profileImageMime(bytes);
  if(!mime)return jsonResponse({error:'Utiliser une image JPG, PNG, WebP ou GIF'},415);
  const hash=new Uint8Array(await crypto.subtle.digest('SHA-256',bytes));
  const version=Array.from(hash,b=>b.toString(16).padStart(2,'0')).join('');
  // Serialize competing writers with the profile-row compare-and-swap. A failed
  // conditional update aborts the batch through the NOT NULL version assertion below.
  const statements=[
    env.DB.prepare(`UPDATE kv_profiles SET ${column}=?,updated_at=? WHERE owner_id=? AND id=? AND COALESCE(${column},'none')=?`)
      .bind(version,now,user.id,profileId,expected),
    env.DB.prepare(`INSERT INTO kv_profile_media(owner_id,profile_id,kind,version,mime,size,updated_at)
      SELECT ?,?,?,CASE WHEN ${column}=? THEN ? ELSE NULL END,?,?,? FROM kv_profiles WHERE owner_id=? AND id=?
      ON CONFLICT(owner_id,profile_id,kind) DO UPDATE SET version=excluded.version,mime=excluded.mime,size=excluded.size,updated_at=excluded.updated_at`)
      .bind(user.id,profileId,kind,version,version,mime,bytes.length,now,user.id,profileId),
    env.DB.prepare('DELETE FROM kv_profile_media_chunks WHERE owner_id=? AND profile_id=? AND kind=?')
      .bind(user.id,profileId,kind)
  ];
  for(let offset=0;offset<bytes.length;offset+=CHUNK_BYTES)statements.push(
    env.DB.prepare('INSERT INTO kv_profile_media_chunks(owner_id,profile_id,kind,position,data) VALUES (?,?,?,?,?)')
      .bind(user.id,profileId,kind,offset/CHUNK_BYTES,bytes.slice(offset,offset+CHUNK_BYTES).buffer));
  try { await env.DB.batch(statements); }
  catch(error) {
    const current=await env.DB.prepare(`SELECT ${column} AS version FROM kv_profiles WHERE owner_id=? AND id=?`).bind(user.id,profileId).first();
    if(current?.version!==expected && current?.version!==version)return jsonResponse({error:'Image modifiée par un autre appareil'},409);
    throw error;
  }
  return jsonResponse({version,mime,size:bytes.length},201);
}
