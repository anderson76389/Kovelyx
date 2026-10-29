#!/usr/bin/env node
/**
 * Vrai Worker + D1 LOCAUX, comptes de test jetables. Jamais de DB distante.
 * Une compilation verte seule ne prouve pas les sessions Better Auth.
 */
import assert from "node:assert/strict";
import {spawn,spawnSync} from "node:child_process";
import {randomUUID} from "node:crypto";
import {mkdtempSync,readFileSync,writeFileSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {resolve,join} from "node:path";
import {setTimeout as sleep} from "node:timers/promises";

const root=resolve(new URL("../",import.meta.url).pathname);
process.chdir(root);
const testDir=mkdtempSync(join(tmpdir(),"kovelyx-worker-audit-"));
const config="wrangler.audit.local.toml";
const salt=randomUUID().replaceAll("-","");
const accountPassword="Kovelyx-Audit-"+salt+"!";
const base="http://127.0.0.1:8765";
let worker;
let logs="";
try {
  const source=readFileSync("wrangler.toml","utf8");
  assert.match(source,/REPLACE_WITH_YOUR_D1_DATABASE_ID/);
  writeFileSync(config,source.replace("REPLACE_WITH_YOUR_D1_DATABASE_ID","00000000-0000-4000-8000-000000000001"));
  const cli=(args)=>{
    const r=spawnSync("npx",["wrangler",...args,"--config",config],{
      cwd:root,encoding:"utf8",timeout:120000,
      env:{...process.env,WRANGLER_SEND_METRICS:"false"}
    });
    if(r.status!==0) throw Error("Local Wrangler "+args.join(" ")+" : "+(r.stderr||r.stdout).slice(-2500));
  };
  cli(["d1","migrations","apply","DB","--local","--persist-to",testDir]);
  worker=spawn("npx",[
      "wrangler","dev","--local","--config",config,"--persist-to",testDir,
      "--ip","127.0.0.1","--port","8765",
      "--var","BETTER_AUTH_SECRET:"+("local-only-"+salt+"-long-enough-for-auth"),
      "--var","KOVELYX_REGISTRATION_OPEN:true"
    ],{
      cwd:root,env:{...process.env,WRANGLER_SEND_METRICS:"false"},
      // npm/npx démarre Wrangler + workerd descendants. Sans groupe isolé,
      // tuer seulement npm laisse des pipes ouverts et fait expirer le job CI.
      detached:true,stdio:["ignore","pipe","pipe"]
    });
  for(const stream of [worker.stdout,worker.stderr])
    stream.on("data",b=>{logs=(logs+b.toString()).slice(-5000)});
  let reachable=false;
  for(let tries=0;tries<60;tries++){
    if(worker.exitCode!==null) throw Error("Wrangler exited during startup: "+logs);
    try {const r=await fetch(base+"/health",{signal:AbortSignal.timeout(1500)});
      if(r.ok && (await r.json()).status==="ok"){reachable=true;break;}
    } catch {}
    await sleep(500);
  }
  assert.ok(reachable,"Local Worker not reachable: "+logs);
  async function req(path,method="GET",body=null,token=null) {
    const headers={Accept:"application/json"};
    if(body!==null) {
      headers["Content-Type"]="application/json";
      headers.Origin=base; // Native clients send their Worker's own trusted Origin.
    }
    if(token) headers.Authorization="Bearer "+token;
    const response=await fetch(base+path,{
      method,headers,body:body===null?undefined:JSON.stringify(body),
      signal:AbortSignal.timeout(12000)
    });
    const text=await response.text();
    let data;
    try{data=JSON.parse(text)}catch{data=null}
    return {status:response.status,data,headers:response.headers};
  }
  // Un /health vert ne prouve pas que D1 et la migration watched_items existent.
  // Ce test utilise UNIQUEMENT la base locale jetable du processus Wrangler.
  const ready=await req("/ready");
  assert.equal(ready.status,200,"La base locale et Better Auth doivent etre prets");
  assert.equal(ready.data?.status,"ok");
  assert.equal(ready.data?.schema,"0003_profile_media");

  const unauth=await req("/v1/profiles");
  assert.equal(unauth.status,401,"Unauthenticated private endpoint");
  const runId=randomUUID();
  async function signupAndLogin(label) {
    const email="local-audit-"+label+"-"+runId+"@example.test";
    const signUp=await req("/api/auth/sign-up/email","POST",{
      email,name:"Kovelyx "+label,password:accountPassword
    });
    assert.ok([200,201].includes(signUp.status),
      "Signup failed "+label+" HTTP "+signUp.status+" "+JSON.stringify(signUp.data)?.slice(0,280));
    const signIn=await req("/api/auth/sign-in/email","POST",{email,password:accountPassword});
    assert.equal(signIn.status,200,
      "Signin failed "+label+": "+JSON.stringify(signIn.data)?.slice(0,300));
    const token=signIn.headers.get("set-auth-token")||signIn.data?.token;
    assert.ok(token?.length>=16,"No Better Auth bearer token for "+label);
    const me=await req("/v1/me","GET",null,token);
    assert.equal(me.status,200,"Bearer authorization must work");
    assert.equal(me.data.email,email);
    return {token,userId:me.data.id,email};
  }
  const alice=await signupAndLogin("alice");
  const bob=await signupAndLogin("bob");
  assert.notEqual(alice.userId,bob.userId,"Account isolation");
  const created=await req("/v1/profiles","POST",{displayName:"Principal"},alice.token);
  assert.equal(created.status,201,"Create principal profile");
  const profileId=created.data?.profile?.id;
  assert.match(profileId||"",/^[a-f0-9-]{36}$/i);
  // Un autre appareil se connecte au MEME compte, avec sa propre session.
  // La présence d'un Worker répondant /health ne suffit pas : il faut
  // pouvoir relire et modifier les données écrites par cette autre session.
  const tvLogin=await req("/api/auth/sign-in/email","POST",{
    email:alice.email,password:accountPassword
  });
  assert.equal(tvLogin.status,200,"Deuxième session du même compte");
  const tvToken=tvLogin.headers.get("set-auth-token")||tvLogin.data?.token;
  assert.ok(tvToken?.length>=16,"Session distincte pour le second appareil");
  assert.notEqual(tvToken,alice.token,"Les appareils doivent avoir des sessions indépendantes");
  const tvMe=await req("/v1/me","GET",null,tvToken);
  assert.equal(tvMe.data?.id,alice.userId,"Deuxième appareil lié au même utilisateur");
  const tvProfiles=await req("/v1/profiles","GET",null,tvToken);
  assert.equal(tvProfiles.data?.profiles?.[0]?.id,profileId,"Profil commun visible sur TV");

  const otherUser=await req("/v1/sync?profileId="+profileId,"GET",null,bob.token);
  assert.equal(otherUser.status,404,"Foreign account must not read D1 profile");
  assert.equal((await req("/v1/profiles","GET",null,bob.token)).data.profiles.length,0);

  const entry=(n)=>({
    profileId,category:"library",itemKey:"movie:"+String(n).padStart(3,"0"),
    revision:0,deleted:false,
    payload:{id:"tt"+n,type:"movie",name:"Film "+n,savedAtEpochMs:1720010000000+n}
  });
  // Écriture depuis PC / lecture depuis TV à l'aide d'un autre Bearer.
  const first=await req("/v1/sync","POST",entry(1),alice.token);
  assert.equal(first.status,201,"Insert first library item");
  assert.equal(first.data.revision,1);
  assert.equal((await req("/v1/sync","POST",entry(1),alice.token)).status,409,
    "Stale CAS writes rejected");

  for(let n=2;n<=31;n++){
    const result=await req("/v1/sync","POST",entry(n),alice.token);
    assert.equal(result.status,201,"Library insert "+n);
  }
  const tvLibrary=await req("/v1/sync?profileId="+profileId+"&limit=25","GET",null,tvToken);
  assert.equal(tvLibrary.status,200,"La TV doit relire les éléments publiés par Windows");
  assert.ok(tvLibrary.data?.items?.some(item=>item.category==="library" &&
    item.payload?.id==="tt1"),"Une œuvre de la bibliothèque doit être visible sur la TV");

  const pageOne=await req("/v1/sync?profileId="+profileId+"&limit=25","GET",null,alice.token);
  assert.equal(pageOne.status,200);
  assert.equal(pageOne.data.items.length,25);
  assert.equal(pageOne.data.nextOffset,25);
  const pageTwo=await req("/v1/sync?profileId="+profileId+"&offset=25&limit=25","GET",null,alice.token);
  assert.equal(pageTwo.status,200);
  assert.equal(pageTwo.data.items.length,6);
  assert.equal(pageTwo.data.nextOffset,null);
  assert.equal((await req("/v1/sync?profileId="+profileId+"&limit=26","GET",null,alice.token)).status,400);

  // A delete is a small tombstone: desktop/Android clients must apply it without
  // requiring all LibraryItem fields in the payload.
  const removed=await req("/v1/sync","POST",{
    profileId,category:"library",itemKey:"movie:001",revision:1,
    payload:{id:"tt1",type:"movie"},deleted:true
  },alice.token);
  assert.equal(removed.status,200);
  assert.equal(removed.data.revision,2);
  const stillOld=await req("/v1/sync","POST",entry(1),alice.token);
  assert.equal(stillOld.status,409,"Offline client cannot resurrect deleted title");

  const progress={
    profileId,category:"watch_progress",itemKey:"movie:tt1",revision:0,
    payload:{parentMetaId:"tt1",parentMetaType:"movie",contentType:"movie",
      videoId:"tt1",title:"Film 1",lastPositionMs:35000,durationMs:120000,
      lastUpdatedEpochMs:1720010500000,isCompleted:false},deleted:false
  };
  const savedProgress=await req("/v1/sync","POST",progress,alice.token);
  assert.equal(savedProgress.status,201,"Save playback position");
  assert.equal((await req("/v1/sync","POST",{
    ...progress,revision:1,payload:{...progress.payload,lastPositionMs:60000,
       lastUpdatedEpochMs:1720010600000}
  },alice.token)).status,200,"Update playback position from second simulated device");
  assert.equal((await req("/v1/sync","POST",progress,alice.token)).status,409,
    "Stale playback position conflict");

  // Android TV reprend la progression puis le PC récupère la position mise
  // à jour : vérification de la synchronisation croisée des sessions.
  const tvProgress=await req("/v1/sync","POST",{
    ...progress,revision:2,payload:{...progress.payload,
      lastPositionMs:85000,lastUpdatedEpochMs:1720010700000}
  },tvToken);
  assert.equal(tvProgress.status,200,"Mise à jour progression depuis un autre appareil");
  const pcProgress=await req("/v1/sync?profileId="+profileId+"&limit=25&offset=25","GET",null,alice.token);
  assert.ok(pcProgress.data?.items?.some(item=>item.category==="watch_progress" &&
    item.itemKey==="movie:tt1" && item.payload?.lastPositionMs===85000),
    "La progression publiée depuis la TV doit être lisible depuis le PC");

  const watched={
    profileId,category:"watched_items",itemKey:"movie:tt1:-1:-1",revision:0,
    deleted:false,payload:{id:"tt1",type:"movie",name:"Film 1",markedAtEpochMs:1720010600000}
  };
  assert.equal((await req("/v1/sync","POST",watched,alice.token)).status,201,
    "Watched history category requires migration 0002");
  // Même historique sur deux appareils, sans partager de jeton de session.
  const tvWatched=await req("/v1/sync?profileId="+profileId+"&limit=25&offset=25","GET",null,tvToken);
  assert.equal(tvWatched.status,200);
  assert.ok(tvWatched.data.items.some(item=>item.category==="watched_items" &&
    item.itemKey==="movie:tt1:-1:-1"),"Historique vu synchronisé sur la TV");
  const all=await req("/v1/sync?profileId="+profileId+"&limit=25&offset=25","GET",null,alice.token);
  assert.ok(all.data.items.some(x=>x.category==="watched_items" || x.category==="watch_progress"));
  assert.equal((await req("/v1/sync","POST",watched,bob.token)).status,404,
    "Other user must never write watched state");
  assert.equal((await req("/v1/sync","POST",{
    ...watched,itemKey:"movie:private",payload:{access_token:"private"}
  },alice.token)).status,400,"Credential fields must not sync");

  // Private user pictures are real bytes, isolated by owner and protected by revisions.
  const mediaUrl=base+"/v1/profile-media?profileId="+profileId+"&kind=avatar";
  const picture=Uint8Array.from(Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aM5sAAAAASUVORK5CYII=","base64"));
  const putPicture=(token,expected,bytes=picture)=>fetch(mediaUrl,{method:"PUT",
    headers:{Authorization:"Bearer "+token,Origin:base,"Content-Type":"image/png","If-Match":expected},body:bytes});
  assert.equal((await fetch(mediaUrl)).status,401,"Images require an authenticated account");
  assert.equal((await putPicture(bob.token,"none")).status,404,"Other user cannot upload");
  const imagePut=await putPicture(alice.token,"none");
  assert.equal(imagePut.status,201,"First private picture upload");
  const imageVersion=(await imagePut.json()).version;
  assert.match(imageVersion,/^[a-f0-9]{64}$/);
  const photoOnTv=await fetch(mediaUrl+"&version="+imageVersion,{headers:{Authorization:"Bearer "+tvToken}});
  assert.equal(photoOnTv.status,200);
  assert.equal(photoOnTv.headers.get("content-type"),"image/png");
  assert.deepEqual(new Uint8Array(await photoOnTv.arrayBuffer()),picture,"Another session receives the actual private file");
  assert.equal((await fetch(mediaUrl,{headers:{Authorization:"Bearer "+bob.token}})).status,404);
  assert.equal((await putPicture(alice.token,"none")).status,409,"Stale uploads must not overwrite newer photos");
  const largePicture=new Uint8Array(1_100_000);largePicture.set(picture);
  const chunked=await putPicture(alice.token,imageVersion,largePicture);
  assert.equal(chunked.status,201,"Photos are chunked below D1's cell limit");
  const chunkVersion=(await chunked.json()).version;
  const roundTrip=await fetch(mediaUrl,{headers:{Authorization:"Bearer "+alice.token}});
  assert.deepEqual(new Uint8Array(await roundTrip.arrayBuffer()),largePicture);
  assert.equal((await putPicture(alice.token,chunkVersion,new Uint8Array(2_097_153))).status,413);
  const changed=picture.slice();changed[changed.length-1]^=1;
  const racing=await Promise.all([putPicture(alice.token,chunkVersion,picture),putPicture(alice.token,chunkVersion,changed)]);
  assert.deepEqual(racing.map(r=>r.status).sort(),[201,409],"Concurrent writers cannot overwrite one another");
  const winner=await fetch(mediaUrl,{headers:{Authorization:"Bearer "+alice.token}});
  const winnerVersion=winner.headers.get("x-kovelyx-media-version");
  assert.equal((await fetch(mediaUrl,{method:"DELETE",headers:{Authorization:"Bearer "+alice.token,Origin:base,"If-Match":chunkVersion}})).status,409);
  assert.equal((await fetch(mediaUrl,{method:"DELETE",headers:{Authorization:"Bearer "+alice.token,Origin:base,"If-Match":winnerVersion}})).status,200);
  assert.equal((await fetch(mediaUrl,{headers:{Authorization:"Bearer "+alice.token}})).status,404);

  const renamed=await req("/v1/profiles?profileId="+profileId,"PATCH",
    {displayName:"Principal TV"},alice.token);
  assert.equal(renamed.status,200,"Rename profile from second device");
  const loaded=await req("/v1/profiles","GET",null,alice.token);
  assert.equal(loaded.data.profiles[0].displayName,"Principal TV");
  const last=await req("/v1/profiles?profileId="+profileId,"DELETE",{},alice.token);
  assert.equal(last.status,409,"Last profile cannot be deleted");

  const signedOut=await req("/api/auth/sign-out","POST",{},alice.token);
  assert.ok(signedOut.status>=200&&signedOut.status<300);
  assert.equal((await req("/v1/me","GET",null,alice.token)).status,401,
    "Sign-out revokes the bearer token");
  assert.equal((await req("/v1/me","GET",null,tvToken)).status,200,
    "La déconnexion Windows ne doit pas invalider la session Android TV");
  const tvSignedOut=await req("/api/auth/sign-out","POST",{},tvToken);
  assert.ok(tvSignedOut.status>=200&&tvSignedOut.status<300);
  assert.equal((await req("/v1/me","GET",null,tvToken)).status,401,
    "La TV doit pouvoir déconnecter sa propre session");
  console.log("PASS: deux appareils et sessions indépendantes sur un même compte, Worker+D1 locaux, bibliothèque, lecture reprise, œuvres vues, déconnexion isolée, CAS, migrations et séparation des comptes");
} finally {
  if (worker?.pid) {
    // Sous Linux, -pid désigne notre groupe dédié, jamais celui du runner.
    // Fermer tous les descendants npx -> wrangler -> workerd et leurs pipes.
    const terminate=(signal)=>{
      try {process.kill(-worker.pid,signal)}
      catch(error) {if(error.code!=="ESRCH") throw error}
    };
    terminate("SIGTERM");
    await sleep(300);
    terminate("SIGKILL");
    worker.stdout?.destroy();
    worker.stderr?.destroy();
    worker.unref();
  }
  rmSync(config,{force:true});
  rmSync(testDir,{force:true,recursive:true});
}
