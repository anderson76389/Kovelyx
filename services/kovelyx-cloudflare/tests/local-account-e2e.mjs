#!/usr/bin/env node
/**
 * Vrai Worker + D1 LOCAUX, comptes de test jetables. Jamais de DB distante.
 * Une compilation verte seule ne prouve pas les sessions Better Auth.
 */
import assert from "node:assert/strict";
import {spawn,spawnSync} from "node:child_process";
import {randomUUID,createHash} from "node:crypto";
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
  assert.equal(ready.data?.schema,"0004_profile_media_gifs");
  assert.equal(ready.data?.maxProfileMediaBytes,18_000_000);
  assert.equal(ready.data?.catalogueSnapshotSchema,1);
  assert.equal(ready.data?.profilePinSchema,1,"Profile PIN migration must be present");

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

  // PINs are account-scoped, not part of kv_sync; validate using two independent sessions.
  const initialPins=await req("/v1/profile-pins","GET",null,alice.token);
  assert.equal(initialPins.status,200);
  assert.deepEqual(initialPins.data.profiles,[]);
  const setPin=await req("/v1/profile-pin","POST",{profileId,pin:"0427"},alice.token);
  assert.equal(setPin.status,201,"Set profile PIN in private D1 table");
  const tvPins=await req("/v1/profile-pins","GET",null,tvToken);
  assert.equal(tvPins.status,200);
  assert.equal(tvPins.data.profiles.length,1,"TV receives PIN-enabled state");
  assert.equal(tvPins.data.profiles[0].pinEnabled,true);
  const tvProfileLocks=await req("/v1/profiles","GET",null,tvToken);
  assert.equal(tvProfileLocks.data.profiles[0].pinEnabled,true);
  const wrongPin=await req("/v1/profile-pin/verify","POST",{profileId,pin:"0000"},tvToken);
  assert.equal(wrongPin.status,200);
  assert.equal(wrongPin.data.unlocked,false);
  const correctPin=await req("/v1/profile-pin/verify","POST",{profileId,pin:"0427"},tvToken);
  assert.equal(correctPin.status,200);
  assert.equal(correctPin.data.unlocked,true);
  const missingOld=await req("/v1/profile-pin","POST",{profileId,pin:"1234"},alice.token);
  assert.equal(missingOld.status,409,"Change needs current PIN");
  const changedPin=await req("/v1/profile-pin","POST",{profileId,pin:"1234",currentPin:"0427"},alice.token);
  assert.equal(changedPin.status,200,"Change PIN from desktop");
  assert.equal((await req("/v1/profile-pin/verify","POST",{profileId,pin:"0427"},tvToken)).data.unlocked,false);
  assert.equal((await req("/v1/profile-pin/verify","POST",{profileId,pin:"1234"},tvToken)).data.unlocked,true);
  assert.equal((await req("/v1/profile-pin","DELETE",{profileId,currentPin:"1234"},alice.token)).status,200);
  assert.deepEqual((await req("/v1/profile-pins","GET",null,tvToken)).data.profiles,[],
    "Delete PIN is propagated across devices");
  assert.equal((await req("/v1/profiles","GET",null,tvToken)).data.profiles[0].pinEnabled,false);
  assert.equal((await req("/v1/profile-pin","POST",{profileId,pin:"1234"},alice.token)).status,201);
  for(let attempt=0;attempt<5;attempt++){
    const invalid=await req("/v1/profile-pin/verify","POST",{profileId,pin:"0000"},tvToken);
    assert.equal(invalid.data?.unlocked,false);
  }
  const blocked=await req("/v1/profile-pin/verify","POST",{profileId,pin:"1234"},tvToken);
  assert.equal(blocked.status,200);
  assert.equal(blocked.data.unlocked,false);
  assert.ok(blocked.data.retryAfterSeconds>0,"Five failed attempts must lock PIN verification");
  assert.equal((await req("/v1/profile-pin/verify","POST",{profileId,pin:"1234"},bob.token)).status,404,
    "Another user must not verify foreign profile PINs");
  assert.deepEqual((await req("/v1/profile-pins","GET",null,bob.token)).data.profiles,[],
    "Another account must not read PIN states");

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

  // Exact configured addon URL, ordered list and removal cross two independent sessions.
  const addons={profileId,category:"addons",itemKey:"installed",revision:0,deleted:false,
    payload:{defaultsVersion:1,items:[
      {url:"https://aio.example/Config%2FAbC/manifest.json?lang=fr&key=Test%2BOnly%3D",name:"AIO Metadata",enabled:true},
      {url:"https://streamfusion.example/config/AbCDef/manifest.json?token=Test%2FOnly&path=/",name:"StreamFusion",enabled:false},
      {url:"https://comet.example/eyJrZXkiOiJURVNUIn0=/manifest.json",name:"Comet",enabled:true}
    ]}};
  assert.equal((await req("/v1/sync","POST",addons,alice.token)).status,201);
  const tvAddons=await req("/v1/sync?profileId="+profileId+"&limit=25","GET",null,tvToken);
  assert.deepEqual(tvAddons.data.items.find(x=>x.category==="addons").payload,addons.payload);
  assert.equal((await req("/v1/sync","POST",{...addons,revision:1,payload:{items:[]}},tvToken)).status,200);
  assert.equal((await req("/v1/sync","POST",addons,alice.token)).status,409,"Old device cannot resurrect removed addons with a stale revision");
  assert.equal((await req("/v1/sync","POST",addons,bob.token)).status,404);

  // Real chunk transfer, atomic publication, isolation, deletion and old-version cleanup.

  for (const [itemKey,payload] of Object.entries({
    visual_theme_v1:{selected_theme:{type:"string",value:"PURPLE"},amoled_enabled:{type:"boolean",value:true}},
    visual_meta_v1:{value:JSON.stringify({background_mode:"cinematic",hero_trailer_playback:true,items:[{key:"CAST",enabled:false,order:2}]})},
    plugin_repositories_v1:{repositories:[{url:"https://modules.example/Config/AbC/manifest.json?token=AbC%2F&path=/",name:"Module configuré",repo_type:"NUVIO_JS"}],disabled:["https://modules.example/Config/AbC/manifest.json?token=AbC%2F&path=/|Code.js"],enabled:true,group:true}
  })) {
    const row={profileId,category:"profile_settings",itemKey,payload,revision:0,deleted:false};
    assert.equal((await req('/v1/sync','POST',row,alice.token)).status,201);
    const items=[]; let cursor=0;
    do {
      const other=await req(`/v1/sync?profileId=${profileId}&limit=25&offset=${cursor}`,'GET',null,tvToken);
      assert.equal(other.status,200); items.push(...other.data.items); cursor=other.data.nextOffset;
    } while(cursor!==null);
    assert.deepEqual(items.find(x=>x.category==='profile_settings'&&x.itemKey===itemKey).payload,payload);
    assert.equal((await req('/v1/sync','POST',row,tvToken)).status,409);
    assert.equal((await req('/v1/sync','POST',row,bob.token)).status,404);
  }
  console.log('PASS: apparence, écran méta et modules configurés reçus exactement dans une seconde session, conflits et isolation vérifiés');
  const catalogueText=JSON.stringify([{id:"ma-collection",folders:[{id:"films",sources:[{provider:"addon",addonId:"kaptain",catalogId:"x"}],name:"é".repeat(900000)}]}]);
  const catalogueHash=createHash('sha256').update(catalogueText).digest('hex');
  const catalogueParts=Array.from({length:Math.ceil(catalogueText.length/6000)},(_,i)=>catalogueText.slice(i*6000,(i+1)*6000));
  const chunk=(hash,index,text)=>({profileId,category:"catalogues",itemKey:`snapshot:collections:${hash}:${String(index).padStart(6,'0')}`,revision:0,deleted:false,payload:{index,text}});
  const manifest={profileId,kind:"collections",sha256:catalogueHash,parts:catalogueParts.length,utf8Bytes:Buffer.byteLength(catalogueText),revision:0};
  assert.equal((await req('/v1/catalogue-manifest','POST',manifest,alice.token)).status,409,'Cannot publish missing chunks');
  assert.equal((await req('/v1/catalogue-chunk','POST',chunk(catalogueHash,0,catalogueParts[0]),bob.token)).status,404);
  for(const [index,text] of catalogueParts.entries())
    assert.equal((await req('/v1/catalogue-chunk','POST',chunk(catalogueHash,index,text),alice.token)).status,201);
  assert.equal((await req('/v1/catalogue-chunk','POST',chunk(catalogueHash,0,catalogueParts[0]),alice.token)).status,200,'Idempotent immutable chunk');
  assert.equal((await req('/v1/catalogue-chunk','POST',chunk(catalogueHash,0,'different'),alice.token)).status,409);
  assert.equal((await req('/v1/catalogue-manifest','POST',manifest,alice.token)).status,201);
  let catalogueOffset=0,cataloguePages=0; const fromTvItems=[];
  do {
    const fromTv=await req(`/v1/sync?profileId=${profileId}&snapshotKind=collections&snapshotHash=${catalogueHash}&offset=${catalogueOffset}&limit=25`,'GET',null,tvToken);
    assert.equal(fromTv.status,200); fromTvItems.push(...fromTv.data.items); cataloguePages++;
    const nextCatalogueOffset=fromTv.data.nextOffset;
    if(nextCatalogueOffset!==null) assert.equal(nextCatalogueOffset,catalogueOffset+25);
    catalogueOffset=nextCatalogueOffset;
  } while(catalogueOffset!==null);
  assert.equal(fromTvItems.length,catalogueParts.length);
  assert.deepEqual(fromTvItems.map(x=>x.payload.index),catalogueParts.map((_,i)=>i));
  assert.equal(fromTvItems.map(x=>x.payload.text).join(''),catalogueText);
  assert.ok(cataloguePages>1); console.log('PASS: catalogue volumineux, '+Buffer.byteLength(catalogueText)+' octets, '+catalogueParts.length+' blocs, '+cataloguePages+' pages, reconstruction exacte');
  const normal=await req(`/v1/sync?profileId=${profileId}`,'GET',null,tvToken);
  assert.equal(normal.data.items.some(x=>x.category==='catalogues'),false,'Progress sync never returns catalogue chunks');
  const emptyHash=createHash('sha256').update('[]').digest('hex');
  assert.equal((await req('/v1/catalogue-chunk','POST',chunk(emptyHash,0,'[]'),tvToken)).status,201);
  assert.equal((await req('/v1/catalogue-manifest','POST',{...manifest,sha256:emptyHash,parts:1,utf8Bytes:2,revision:1},tvToken)).status,200);
  const removedCatalogues=await req(`/v1/sync?profileId=${profileId}&snapshotKind=collections&snapshotHash=${catalogueHash}`,'GET',null,alice.token);
  assert.equal(removedCatalogues.data.items.length,0,'Obsolete snapshots do not accumulate');
  assert.equal((await req('/v1/catalogue-manifest','POST',manifest,alice.token)).status,409,'Stale writer cannot resurrect deleted catalogues');
  assert.equal((await req('/v1/sync','POST',chunk(emptyHash,0,'[]'),alice.token)).status,400,'Generic writes cannot bypass immutable protocol');

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
  const gif=Buffer.from("R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQBFAABACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7","base64");
  // A valid two-frame GIF with comment blocks crosses the former 2 MiB limit.
  const comments=[Buffer.from([0x21,0xfe])];
  for(let i=0;i<70300;i++)comments.push(Buffer.from([255]),Buffer.alloc(255,65));
  comments.push(Buffer.from([0]));
  const largePicture=new Uint8Array(Buffer.concat([gif.subarray(0,-1),...comments,gif.subarray(-1)]));
  assert.ok(largePicture.length>17_000_000 && largePicture.length<=18_000_000);
  const chunked=await putPicture(alice.token,imageVersion,largePicture);
  assert.equal(chunked.status,201,"Photos are chunked below D1's cell limit");
  const chunkVersion=(await chunked.json()).version;
  // Background and avatar have independent files and profile-version columns.
  const backgroundUrl=base+"/v1/profile-media?profileId="+profileId+"&kind=background";
  const backgroundPicture=new Uint8Array([...picture,0]);
  const backgroundPut=await fetch(backgroundUrl,{method:"PUT",headers:{Authorization:"Bearer "+tvToken,
    Origin:base,"Content-Type":"image/png","If-Match":"none"},body:backgroundPicture});
  assert.equal(backgroundPut.status,201);
  const backgroundVersion=(await backgroundPut.json()).version;
  const imageProfiles=await req("/v1/profiles","GET",null,alice.token);
  assert.equal(imageProfiles.data.profiles[0].avatarPath,chunkVersion);
  assert.equal(imageProfiles.data.profiles[0].backgroundPath,backgroundVersion);
  const backgroundGet=await fetch(backgroundUrl,{headers:{Authorization:"Bearer "+alice.token}});
  assert.equal(backgroundGet.status,200);
  assert.deepEqual(new Uint8Array(await backgroundGet.arrayBuffer()),backgroundPicture);
  const roundTrip=await fetch(mediaUrl,{headers:{Authorization:"Bearer "+tvToken}});
  assert.equal(roundTrip.headers.get("content-type"),"image/gif");
  assert.deepEqual(new Uint8Array(await roundTrip.arrayBuffer()),largePicture);
  assert.equal((await putPicture(alice.token,chunkVersion,new Uint8Array(18_000_001))).status,413);
  const changed=picture.slice();changed[changed.length-1]^=1;
  const racing=await Promise.all([putPicture(alice.token,chunkVersion,picture),putPicture(alice.token,chunkVersion,changed)]);
  assert.deepEqual(racing.map(r=>r.status).sort(),[201,409],"Concurrent writers cannot overwrite one another");
  const winner=await fetch(mediaUrl,{headers:{Authorization:"Bearer "+alice.token}});
  const winnerVersion=winner.headers.get("x-kovelyx-media-version");
  assert.equal((await fetch(mediaUrl,{method:"DELETE",headers:{Authorization:"Bearer "+alice.token,Origin:base,"If-Match":chunkVersion}})).status,409);
  assert.equal((await fetch(mediaUrl,{method:"DELETE",headers:{Authorization:"Bearer "+alice.token,Origin:base,"If-Match":winnerVersion}})).status,200);
  assert.equal((await fetch(mediaUrl,{headers:{Authorization:"Bearer "+alice.token}})).status,404);

  assert.equal((await fetch(backgroundUrl,{headers:{Authorization:"Bearer "+tvToken}})).status,200,
    "Deleting the avatar must not remove the background");

  // Batch marks span multiple pages and keep film/series identities and episode numbers.
  const batchMarks = Array.from({length:31}, (_, n) => ({
    profileId, category:"watched_items", itemKey:`series:tt-batch:1:${n+1}`, revision:0,
    deleted:false, payload:{id:"tt-batch",type:"series",name:"Série française",season:1,episode:n+1,markedAtEpochMs:1720010600000+n}
  }));
  for (const mark of batchMarks) {
    assert.equal((await req("/v1/sync","POST",mark,alice.token)).status,201,"Mark episode watched");
  }
  async function readAllSync(token) {
    const rows=[];
    let offset=0;
    do {
      const page=await req(`/v1/sync?profileId=${profileId}&limit=25&offset=${offset}`,"GET",null,token);
      assert.equal(page.status,200);
      rows.push(...page.data.items);
      offset=page.data.nextOffset;
    } while(offset!==null);
    return rows;
  }
  const secondSessionRows=await readAllSync(tvToken);
  assert.equal(secondSessionRows.filter(row=>row.category==="watched_items" && row.payload?.id==="tt-batch").length,31,
    "All batch watched marks must cross sessions and pagination");
  const unmark={...batchMarks[5],revision:1,deleted:true,payload:{removed:true}};
  assert.equal((await req("/v1/sync","POST",unmark,tvToken)).status,200,"Unmark one episode");
  assert.equal((await req("/v1/sync","POST",batchMarks[5],alice.token)).status,409,"Offline stale mark must not resurrect an unmarked episode");
  const afterUnmark=await readAllSync(alice.token);
  const tombstone=afterUnmark.find(row=>row.category==="watched_items" && row.itemKey===unmark.itemKey);
  assert.equal(tombstone?.deleted,true,"Unmark tombstone visible to first session");
  assert.equal(afterUnmark.filter(row=>row.category==="watched_items" && row.payload?.id==="tt-batch" && !row.deleted).length,30);

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
