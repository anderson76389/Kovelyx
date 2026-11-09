#!/usr/bin/env node
/**
 * Vrai Worker + D1 LOCAUX, comptes de test jetables. Jamais de DB distante.
 * Une compilation verte seule ne prouve pas les sessions Better Auth.
 */
import assert from "node:assert/strict";
import {spawn,spawnSync} from "node:child_process";
import {randomUUID,createHash,webcrypto} from "node:crypto";
import vm from "node:vm";
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

  // Trois clients natifs SIMULÉS : sessions Better Auth indépendantes sur D1 jetable.
  // Le test couvre les routes et le contrat de synchronisation, PAS les vrais écrans APK.
  const mobileLogin=await req("/api/auth/sign-in/email","POST",{
    email:alice.email,password:accountPassword
  });
  assert.equal(mobileLogin.status,200,"Android mobile opens its own session");
  const mobileToken=mobileLogin.headers.get("set-auth-token")||mobileLogin.data?.token;
  assert.ok(mobileToken?.length>=16,"Android mobile has a bearer token");
  assert.notEqual(mobileToken,alice.token);
  assert.notEqual(mobileToken,tvToken);
  assert.equal((await req("/v1/me","GET",null,mobileToken)).data?.id,alice.userId,
    "Three device sessions must identify the same account");
  const mobileProfiles=await req("/v1/profiles","GET",null,mobileToken);
  assert.equal(mobileProfiles.data?.profiles?.[0]?.id,profileId,
    "Android mobile sees the same cloud profile as Windows and TV");

  async function threeWayRows(token,requestedProfile=profileId) {
    const rows=[];
    let next=0;
    do {
      const page=await req("/v1/sync?profileId="+requestedProfile+"&offset="+next+"&limit=25",
        "GET",null,token);
      assert.equal(page.status,200,"Cloud library pages must be readable on every device");
      rows.push(...page.data.items);
      next=page.data.nextOffset;
    } while(next!==null);
    return rows;
  }
  const threeWayRow=async (token,category,key,requestedProfile=profileId)=>
    (await threeWayRows(token,requestedProfile)).find(row=>
      row.category===category && row.itemKey===key);
  const threeWayWrite=(token,category,key,revision,payload,deleted=false,requestedProfile=profileId)=>
    req("/v1/sync","POST",{profileId:requestedProfile,category,itemKey:key,
      revision,deleted,payload},token);

  const filmKey="movie:tt-three-devices-film";
  const seriesKey="series:tt-three-devices-series";
  const keepKey="movie:tt-three-devices-keep";
  const savedAt=Date.now()-60000;
  const filmData={id:"tt-three-devices-film",type:"movie",name:"Film synchronisé",savedAtEpochMs:savedAt};
  const seriesData={id:"tt-three-devices-series",type:"series",name:"Série synchronisée",savedAtEpochMs:savedAt};
  const keepData={id:"tt-three-devices-keep",type:"movie",name:"Film conservé",savedAtEpochMs:savedAt};
  assert.equal((await threeWayWrite(alice.token,"library",filmKey,0,filmData)).status,201,
    "Windows adds one film");
  assert.equal((await threeWayWrite(mobileToken,"library",seriesKey,0,seriesData)).status,201,
    "Android adds one series");
  assert.equal((await threeWayWrite(mobileToken,"library",keepKey,0,keepData)).status,201,
    "Android adds a second film");
  for(const token of [alice.token,tvToken,mobileToken]){
    const items=await threeWayRows(token);
    for(const key of [filmKey,seriesKey,keepKey]){
      assert.equal(items.find(row=>row.category==="library"&&row.itemKey===key)?.deleted,false,
        "Windows, TV and Android all see each added work: "+key);
    }
  }

  // TV deletes one title, Android/Windows pull the tombstone. An offline stale
  // writer must not resurrect it. Only a newer explicit save can restore it.
  assert.equal((await threeWayWrite(tvToken,"library",filmKey,1,
    {id:filmData.id,type:filmData.type},true)).status,200,"TV removes saved film");
  for(const token of [alice.token,mobileToken]){
    assert.equal((await threeWayRow(token,"library",filmKey))?.deleted,true,
      "Deleting from TV must remove this film on Windows and Android");
    assert.equal((await threeWayRow(token,"library",keepKey))?.deleted,false,
      "Deleting one title must preserve the other saved film");
  }
  assert.equal((await threeWayWrite(alice.token,"library",filmKey,0,filmData)).status,409,
    "Offline Windows cannot restore the film using a stale revision");
  const newFilm={...filmData,savedAtEpochMs:Date.now()+1000};
  assert.equal((await threeWayWrite(mobileToken,"library",filmKey,2,newFilm)).status,200,
    "A newer intentional Android save restores the deleted film");
  assert.equal((await threeWayRow(tvToken,"library",filmKey))?.deleted,false,
    "TV sees the intentional re-addition");
  assert.equal((await threeWayWrite(alice.token,"library",filmKey,3,
    {id:filmData.id,type:filmData.type},true)).status,200,
    "Windows can delete the restored film");
  assert.equal((await threeWayRow(mobileToken,"library",filmKey))?.deleted,true);
  assert.equal((await threeWayRow(tvToken,"library",filmKey))?.deleted,true);
  assert.equal((await threeWayWrite(alice.token,"library",seriesKey,1,
    {id:seriesData.id,type:seriesData.type},true)).status,200,
    "Windows removes series without touching remaining film");
  assert.equal((await threeWayRow(tvToken,"library",seriesKey))?.deleted,true);
  assert.equal((await threeWayRow(mobileToken,"library",keepKey))?.deleted,false);

  // Watched / unwatched state is independent of library membership.
  const watchedKey="movie:tt-three-devices-film:-1:-1";
  const watchedData={id:filmData.id,type:"movie",name:filmData.name,
    markedAtEpochMs:Date.now()};
  assert.equal((await threeWayWrite(alice.token,"watched_items",watchedKey,0,watchedData)).status,201,
    "Windows marks the removed film watched");
  assert.equal((await threeWayRow(tvToken,"watched_items",watchedKey))?.deleted,false);
  assert.equal((await threeWayRow(mobileToken,"watched_items",watchedKey))?.deleted,false);
  assert.equal((await threeWayWrite(tvToken,"watched_items",watchedKey,1,
    {removed:true},true)).status,200,"TV unmarks watched");
  assert.equal((await threeWayRow(alice.token,"watched_items",watchedKey))?.deleted,true);
  assert.equal((await threeWayRow(mobileToken,"watched_items",watchedKey))?.deleted,true);
  assert.equal((await threeWayWrite(alice.token,"watched_items",watchedKey,0,watchedData)).status,409,
    "Stale watched flag cannot resurrect after unmark");
  assert.equal((await threeWayWrite(mobileToken,"watched_items",watchedKey,2,
    {...watchedData,markedAtEpochMs:Date.now()+1000})).status,200,
    "Android can explicitly mark watched again");
  assert.equal((await threeWayRow(tvToken,"watched_items",watchedKey))?.deleted,false);
  assert.equal((await threeWayRow(alice.token,"library",filmKey))?.deleted,true,
    "Watching a film must not silently re-add it to the library");

  // Resume position passes Windows -> TV -> Android -> Windows, using CAS revisions.
  const progressKey="movie:tt-three-devices-film";
  const progressData={parentMetaId:filmData.id,parentMetaType:"movie",contentType:"movie",
    videoId:filmData.id,title:filmData.name,lastPositionMs:10000,durationMs:120000,
    lastUpdatedEpochMs:Date.now(),isCompleted:false};
  assert.equal((await threeWayWrite(alice.token,"watch_progress",progressKey,0,progressData)).status,201);
  assert.equal((await threeWayRow(tvToken,"watch_progress",progressKey))?.payload?.lastPositionMs,10000);
  assert.equal((await threeWayWrite(tvToken,"watch_progress",progressKey,1,
    {...progressData,lastPositionMs:25000,lastUpdatedEpochMs:Date.now()+1000})).status,200);
  assert.equal((await threeWayRow(mobileToken,"watch_progress",progressKey))?.payload?.lastPositionMs,25000);
  assert.equal((await threeWayWrite(mobileToken,"watch_progress",progressKey,2,
    {...progressData,lastPositionMs:45000,lastUpdatedEpochMs:Date.now()+2000})).status,200);
  assert.equal((await threeWayRow(alice.token,"watch_progress",progressKey))?.payload?.lastPositionMs,45000);
  assert.equal((await threeWayWrite(alice.token,"watch_progress",progressKey,0,progressData)).status,409,
    "Old Windows playback progress cannot overwrite Android's newer position");

  // Simulate an offline mobile client holding a stale local revision. The TV
  // advances the real D1 row while mobile is offline; on reconnect the stale
  // write MUST conflict, then the client can fetch/rebase without losing progress.
  // These are disposable Better Auth sessions against LOCAL D1, not production.
  const offlineMobile=await threeWayRow(mobileToken,"watch_progress",progressKey);
  assert.equal(offlineMobile.revision,3,"Mobile captured the last synced revision");
  assert.equal(offlineMobile.payload.lastPositionMs,45000);
  const tvAfterDisconnect={...progressData,lastPositionMs:57000,
    lastUpdatedEpochMs:Date.now()+3000};
  assert.equal((await threeWayWrite(tvToken,"watch_progress",progressKey,3,
    tvAfterDisconnect)).status,200,"TV progresses while mobile is offline");
  const staleMobile={...progressData,lastPositionMs:40000,
    lastUpdatedEpochMs:Date.now()+2000};
  assert.equal((await threeWayWrite(mobileToken,"watch_progress",progressKey,
    offlineMobile.revision,staleMobile)).status,409,
    "Disconnected mobile must not overwrite newer TV playback on reconnect");
  const rebasedMobile=await threeWayRow(mobileToken,"watch_progress",progressKey);
  assert.equal(rebasedMobile.revision,4,"Mobile fetches latest D1 revision");
  assert.equal(rebasedMobile.payload.lastPositionMs,57000);
  assert.equal((await threeWayWrite(mobileToken,"watch_progress",progressKey,4,
    {...progressData,lastPositionMs:72000,
      lastUpdatedEpochMs:Date.now()+4000})).status,200,
    "Mobile may publish newer progress after resolving the conflict");
  for(const token of [alice.token,tvToken,mobileToken]){
    const row=await threeWayRow(token,"watch_progress",progressKey);
    assert.equal(row.revision,5);
    assert.equal(row.payload.lastPositionMs,72000,
      "Windows, Android and TV converge after simulated offline reconnection");
  }
  assert.equal((await threeWayWrite(alice.token,"watch_progress",progressKey,3,
    progressData)).status,409,"Third device cannot overwrite new position with stale cached state");

  // Another profile under the same account must not share library or watched state.
  const isolated=await req("/v1/profiles","POST",{displayName:"Profil test secondaire"},mobileToken);
  assert.equal(isolated.status,201);
  const isolatedId=isolated.data?.profile?.id;
  assert.match(isolatedId||"",/^[a-f0-9-]{36}$/i);
  const isolatedKey="movie:tt-secondary-private";
  assert.equal((await threeWayWrite(mobileToken,"library",isolatedKey,0,
    {id:"tt-secondary-private",type:"movie",name:"Second profil",savedAtEpochMs:Date.now()},
    false,isolatedId)).status,201);
  assert.equal((await threeWayRow(tvToken,"library",isolatedKey,isolatedId))?.deleted,false,
    "TV can access the secondary profile after switching to it");
  assert.equal(await threeWayRow(alice.token,"library",isolatedKey),undefined,
    "The primary profile never contains the secondary profile's items");
  assert.equal((await req("/v1/sync?profileId="+isolatedId,"GET",null,bob.token)).status,404,
    "A foreign account must not read the secondary profile");
  assert.equal((await req("/v1/profiles?profileId="+isolatedId,"DELETE",{},mobileToken)).status,200,
    "Delete only the disposable secondary test profile");
  console.log("PASS: 3 sessions Windows/Android/TV, films et séries, suppressions bidirectionnelles, anti-résurrection, vues, progression et isolation des profils");

  // Site Ma-Collection VRAIMENT publié, transféré à un Worker D1 LOCAL jetable.
  // Aucune requête authentifiée vers les comptes Cloudflare de production.
  const siteRoot="https://anderson76389.github.io/Ma-Collection";
  const siteSources=await Promise.all([
    fetch(siteRoot+"/kovelyx-cloud-push.js?v=1",{signal:AbortSignal.timeout(20000)}),
    fetch(siteRoot+"/export/nuvio-collection-fr.json",{signal:AbortSignal.timeout(20000)})
  ]);
  for(const response of siteSources)assert.equal(response.status,200,
    "Ma-Collection doit servir son vrai script de Push et son export catalogue public : "+response.url);
  const livePushSource=await siteSources[0].text();
  const liveCatalogue=await siteSources[1].json();
  assert.match(livePushSource,/MaCollectionCloudPush/);
  assert.ok(Array.isArray(liveCatalogue),"Le catalogue public Ma-Collection doit être un tableau");
  const liveCategory=liveCatalogue.find(row=>row?.id && Array.isArray(row.folders) &&
    row.folders.length>=2 && row.folders[0]?.id !== row.folders[1]?.id);
  assert.ok(liveCategory,"Deux vrais dossiers Ma-Collection nécessaires au test");
  const websiteProfile=await req("/v1/profiles","POST",{displayName:"Ma-Collection CI jetable"},alice.token);
  assert.equal(websiteProfile.status,201);
  const websiteProfileId=websiteProfile.data.profile.id;
  const origin="https://anderson76389.github.io";
  const siteNodes=new Map();
  class FakeElement {
    constructor(tag="div") { this.tagName=tag;this.children=[];this.value="";this.textContent="";
      this.disabled=false;this.hidden=true;this.dataset={};this.handlers={}; }
    setAttribute() {}
    addEventListener(event,handler) {this.handlers[event]=handler;}
    focus() {}
    replaceChildren() {this.children=[];this.value="";}
    appendChild(child) {
      this.children.push(child);
      if(this.tagName==="select" && this.children.length===1)this.value=child.value;
      return child;
    }
  }
  for(const name of ["account","login","reload","profile","mode","status","cancel","send"])
    siteNodes.set("mc-kovelyx-cloud-"+name,new FakeElement(
      ["profile","mode"].includes(name)?"select":"div"));
  siteNodes.get("mc-kovelyx-cloud-mode").value="merge";
  const fakeDocument={
    getElementById:id=>siteNodes.get(id)||null,
    createElement:tag=>new FakeElement(tag),
    head:new FakeElement("head"),body:new FakeElement("body"),
    addEventListener(){}
  };
  const websiteApp={
    MaCollectionAccount:{
      status:()=>({cloudSignedIn:true,cloudEmail:alice.email}),
      async cloudFetch(path,options={}){
        assert.ok(path.startsWith("/v1/"),"Pas d'appel distant hors Kovelyx");
        return fetch(base+path,{
          method:options.method||"GET",
          headers:{Accept:"application/json",Origin:origin,Authorization:"Bearer "+alice.token,
            ...(options.body===undefined?{}:{"Content-Type":"application/json"})},
          body:options.body===undefined?undefined:JSON.stringify(options.body),
          signal:AbortSignal.timeout(15000)
        });
      }
    },
    MaCollectionBackendConfig:{current:()=>({url:base})},
    MaCollectionKovelyx:{buildExport:async()=>""},
    confirm:()=>true
  };
  const browserFetch=(url,options={})=>fetch(url,{
    ...options,headers:{...options.headers,Origin:origin},signal:AbortSignal.timeout(15000)
  });
  vm.runInNewContext(livePushSource,{
    window:websiteApp,document:fakeDocument,fetch:browserFetch,
    TextEncoder,structuredClone,crypto:webcrypto,console
  },{timeout:3000,filename:"Ma-Collection-deployed/kovelyx-cloud-push.js"});
  const sitePush=websiteApp.MaCollectionCloudPush;
  assert.equal(typeof sitePush?.publish,"function","Vraie fonctionnalité Push publiée sur GitHub Pages");
  const websiteCategory=(folders)=>[{
    ...liveCategory, folders:folders.map(folder=>structuredClone(folder))
  }];
  async function publishWebsite(folders) {
    websiteApp.MaCollectionKovelyx.buildExport=async()=>JSON.stringify(websiteCategory(folders));
    await sitePush.open();
    siteNodes.get("mc-kovelyx-cloud-profile").value=websiteProfileId;
    await sitePush.publish();
    const state=siteNodes.get("mc-kovelyx-cloud-status");
    assert.equal(state.dataset.error,"false","Site -> Worker D1 local : "+state.textContent);
    assert.match(state.textContent,/publiés|déjà publiés/,
      "Une publication doit être confirmée seulement par le Worker : "+state.textContent);
  }
  async function readWebsiteSnapshot(token) {
    const response=await req("/v1/sync?profileId="+websiteProfileId+"&limit=25","GET",null,token);
    assert.equal(response.status,200);
    const head=response.data.items.find(row=>row.category==="preferences"&&
      row.itemKey==="catalogue_collections"&&!row.deleted);
    assert.ok(head,"Le manifeste doit être visible dans chaque session");
    const parts=[];
    let offset=0;
    do {
      const page=await req("/v1/sync?profileId="+websiteProfileId+
        "&snapshotKind=collections&snapshotHash="+head.payload.sha256+
        "&limit=25&offset="+offset,"GET",null,token);
      assert.equal(page.status,200);
      parts.push(...page.data.items.map(item=>item.payload));
      offset=page.data.nextOffset;
    } while(offset!==null);
    parts.sort((a,b)=>a.index-b.index);
    assert.equal(parts.length,head.payload.parts);
    const raw=parts.map(part=>part.text).join("");
    assert.equal(createHash("sha256").update(raw).digest("hex"),head.payload.sha256);
    assert.equal(Buffer.byteLength(raw),head.payload.utf8Bytes);
    return JSON.parse(raw);
  }
  // PC/website publishes actual Ma-Collection JSON ; TV reads another Bearer.
  await publishWebsite([liveCategory.folders[0]]);
  const fromTv=await readWebsiteSnapshot(tvToken);
  assert.equal(fromTv[0].folders[0].id,liveCategory.folders[0].id);
  // A second Push in fusion mode MUST preserve the original folder (no data loss).
  await publishWebsite([liveCategory.folders[1]]);
  for(const token of [tvToken,mobileToken,alice.token]){
    const remote=await readWebsiteSnapshot(token);
    assert.ok(remote.some(category=>category.id===liveCategory.id));
    const ids=new Set(remote.find(category=>category.id===liveCategory.id).folders.map(x=>x.id));
    assert.ok(ids.has(liveCategory.folders[0].id),"Le dossier initial reste sur chaque appareil");
    assert.ok(ids.has(liveCategory.folders[1].id),"Le nouveau dossier arrive sur chaque appareil");
  }
  assert.equal((await req("/v1/sync?profileId="+websiteProfileId,"GET",null,bob.token)).status,404,
    "Un autre utilisateur ne doit pas accéder aux catalogues publiés");
  assert.equal((await req("/v1/profiles?profileId="+websiteProfileId,"DELETE",{},alice.token)).status,200,
    "Profil de test isolé effacé de D1 locale uniquement");
  console.log("PASS: vrai JS GitHub Pages Ma-Collection et vrais catalogues FR -> Worker+D1 locaux -> trois sessions PC/Android/TV ; fusion sans perte, SHA-256, isolation");

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
  assert.equal((await req("/v1/me","GET",null,mobileToken)).status,200,
    "La déconnexion Windows ne doit pas invalider Android mobile");
  const tvSignedOut=await req("/api/auth/sign-out","POST",{},tvToken);
  assert.ok(tvSignedOut.status>=200&&tvSignedOut.status<300);
  assert.equal((await req("/v1/me","GET",null,tvToken)).status,401,
    "La TV doit pouvoir déconnecter sa propre session");
  assert.equal((await req("/v1/me","GET",null,mobileToken)).status,200,
    "La déconnexion TV ne doit pas invalider la session Android mobile");
  assert.ok((await req("/api/auth/sign-out","POST",{},mobileToken)).status<300);
  assert.equal((await req("/v1/me","GET",null,mobileToken)).status,401,
    "Android mobile doit révoquer sa propre session");
  console.log("PASS: trois appareils simulés et sessions indépendantes, Worker+D1 locaux, bibliothèque, lecture reprise, œuvres vues, suppressions, déconnexion isolée et profils");
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
