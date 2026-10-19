// Médias privés des profils Kovelyx. Les objets ne sont pas publics : seules
// les requêtes authentifiées du propriétaire peuvent les lire et les remplacer.
export const MEDIA_LIMIT = 20 * 1024 * 1024;
const MIME = new Set(["image/jpeg","image/png","image/gif","image/webp",
                      "video/mp4","video/webm","video/quicktime"]);
const UUID = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const type = (bytes,mime) => {
  const starts=(...v)=>v.every((b,i)=>bytes[i]===b);
  const ascii=(i,n)=>String.fromCharCode(...bytes.slice(i,i+n));
  if(mime==="image/jpeg") return starts(0xff,0xd8,0xff);
  if(mime==="image/png") return starts(137,80,78,71,13,10,26,10);
  if(mime==="image/gif") return ascii(0,4)==="GIF8";
  if(mime==="image/webp") return ascii(0,4)==="RIFF" && ascii(8,4)==="WEBP";
  if(mime==="video/webm") return starts(0x1a,0x45,0xdf,0xa3);
  if(mime==="video/mp4" || mime==="video/quicktime") return ascii(4,4)==="ftyp";
  return false;
};
export function validMedia(bytes,mime) {
  return bytes instanceof Uint8Array && bytes.length>0 &&
    bytes.length<=MEDIA_LIMIT && MIME.has(mime) && type(bytes,mime);
}
const respond=(data,status=200)=>new Response(JSON.stringify(data),{
  status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}
});
export async function handleProfileMedia(request,env,user,url) {
  if(!env.MEDIA) return respond({error:"Cloudflare R2 non configuré : médias conservés localement"},503);
  const profileId=url.searchParams.get("profileId")||"";
  const kind=url.searchParams.get("kind")||"";
  if(!UUID.test(profileId)||!["avatar","background"].includes(kind))
    return respond({error:"Paramètres invalides"},400);
  const profile=await env.DB.prepare(
    "SELECT avatar_path AS avatarPath,background_path AS backgroundPath "+
    "FROM kv_profiles WHERE owner_id=? AND id=?"
  ).bind(user.id,profileId).first();
  if(!profile) return respond({error:"Profil introuvable"},404);
  const field=kind==="avatar"?"avatarPath":"backgroundPath";
  if(request.method==="GET") {
    const key=profile[field];
    if(!key) return respond({error:"Aucun média"},404);
    const object=await env.MEDIA.get(key);
    if(!object) return respond({error:"Média indisponible"},404);
    return new Response(object.body,{status:200,headers:{
      "content-type":object.httpMetadata?.contentType||"application/octet-stream",
      "cache-control":"private, no-store","x-content-type-options":"nosniff"
    }});
  }
  if(request.method==="DELETE") {
    const column=kind==="avatar"?"avatar_path":"background_path";
    if(!profile[field]) return respond({removed:true});
    const removed=await env.DB.prepare(
      `UPDATE kv_profiles SET ${column}=NULL,updated_at=? WHERE owner_id=? AND id=? AND ${column} IS ?`
    ).bind(Date.now(),user.id,profileId,profile[field]).run();
    if(removed.meta?.changes!==1) return respond({error:"Modification concurrente du média"},409);
    await env.MEDIA.delete(profile[field]).catch(()=>{});
    return respond({removed:true});
  }
  if(request.method!=="PUT") return respond({error:"Méthode non autorisée"},405);
  const mime=(request.headers.get("content-type")||"").split(";")[0].trim().toLowerCase();
  const announced=Number(request.headers.get("content-length")||0);
  if(!MIME.has(mime)||announced>MEDIA_LIMIT||!Number.isFinite(announced))
    return respond({error:"Format ou taille refusé (20 Mo max)"},413);
  const bytes=new Uint8Array(await request.arrayBuffer());
  if(!validMedia(bytes,mime)) return respond({error:"Fichier illisible, trop gros ou format non accepté"},415);
  const extension={"image/jpeg":"jpg","image/png":"png","image/gif":"gif",
    "image/webp":"webp","video/mp4":"mp4","video/webm":"webm","video/quicktime":"mov"}[mime];
  const key=`users/${encodeURIComponent(user.id)}/profiles/${profileId}/${kind}/${crypto.randomUUID()}.${extension}`;
  await env.MEDIA.put(key,bytes,{httpMetadata:{contentType:mime}});
  const column=kind==="avatar"?"avatar_path":"background_path";
  const saved=await env.DB.prepare(
    `UPDATE kv_profiles SET ${column}=?,updated_at=? WHERE owner_id=? AND id=? AND ${column} IS ?`
  ).bind(key,Date.now(),user.id,profileId,profile[field]).run();
  if(saved.meta?.changes!==1) {
    await env.MEDIA.delete(key);
    return respond({error:"Profil modifié pendant l'import"},409);
  }
  // La suppression d'une ancienne ressource est un nettoyage non critique.
  if(profile[field]&&profile[field]!==key)
    await env.MEDIA.delete(profile[field]).catch(()=>{});
  return respond({ok:true,kind,contentType:mime});
}
