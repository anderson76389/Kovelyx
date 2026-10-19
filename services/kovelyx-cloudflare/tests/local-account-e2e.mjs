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
      headers.Origin=base; // Same Worker origin as Windows / Android native clients.
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
    return {token,userId:me.data.id};
  }
  const alice=await signupAndLogin("alice");
  const bob=await signupAndLogin("bob");
  assert.notEqual(alice.userId,bob.userId);
  const created=await req("/v1/profiles","POST",{displayName:"Profil principal audit"},alice.token);
  assert.equal(created.status,201,"Create profile");
  const id=created.data?.profile?.id;
  assert.ok(/^[a-f0-9-]{36}$/i.test(id||""));
  assert.equal((await req("/v1/profiles","GET",null,bob.token)).data.profiles.length,0,
    "Account isolation");
  const forbidden=await req("/v1/sync?profileId="+id+"&category=library","GET",null,bob.token);
  assert.equal(forbidden.status,404,"Second user must not read first user's profile");
  const input={
    profileId:id,category:"library",itemKey:"movie:101",
    payload:{item:{id:"101",type:"movie",name:"Test local D1"}},
    deleted:false,revision:0
  };
  const inserted=await req("/v1/sync","POST",input,alice.token);
  assert.equal(inserted.status,201,"Insert revision zero");
  assert.equal(inserted.data.revision,1);
  const conflict=await req("/v1/sync","POST",input,alice.token);
  assert.equal(conflict.status,409,"Stale revision must conflict");
  const updated=await req("/v1/sync","POST",{...input,revision:1},alice.token);
  assert.equal(updated.status,200,"CAS update");
  assert.equal(updated.data.revision,2);
  const page=await req("/v1/sync?profileId="+id+"&category=library&limit=1","GET",null,alice.token);
  assert.equal(page.status,200);
  assert.equal(page.data.items.length,1);
  assert.equal(page.data.items[0].revision,2);

  // Simuler les contrats de données d'un PC, d'un mobile et d'une TV via de
  // VRAIES requêtes HTTP au même Worker+D1. Ce n'est pas un essai matériel.
  const mobileEntry={
    profileId:id,category:"library",itemKey:"movie:202",
    payload:{item:{
      id:"202",type:"movie",name:"Ajout Android",savedAtEpochMs:1720010200000,
      poster:"https://image.tmdb.org/t/p/w500/poster.jpg"
    }},deleted:false,revision:0
  };
  const fromMobile=await req("/v1/sync","POST",mobileEntry,alice.token);
  assert.equal(fromMobile.status,201,"Android-like metadata must be stored");
  assert.equal(fromMobile.data.revision,1);
  const tvEntry={
    profileId:id,category:"library",itemKey:"series:303",
    payload:{item:{id:"303",type:"series",name:"Ajout Android TV",savedAtEpochMs:1720010300000}},
    deleted:false,revision:0
  };
  const fromTv=await req("/v1/sync","POST",tvEntry,alice.token);
  assert.equal(fromTv.status,201,"TV-like metadata must be stored");

  const crossDevicePage1=await req(
    "/v1/sync?profileId="+id+"&category=library&limit=1","GET",null,alice.token);
  assert.equal(crossDevicePage1.status,200);
  assert.equal(crossDevicePage1.data.items.length,1);
  assert.equal(crossDevicePage1.data.partial,true);
  assert.equal(crossDevicePage1.data.nextCursor,"movie:101");
  assert.equal(crossDevicePage1.data.items[0].payload.item.id,"101");
  const crossDevicePage2=await req(
    "/v1/sync?profileId="+id+"&category=library&limit=1&after="+
      encodeURIComponent(crossDevicePage1.data.nextCursor),"GET",null,alice.token);
  assert.equal(crossDevicePage2.status,200);
  assert.equal(crossDevicePage2.data.items[0].payload.item.name,"Ajout Android");
  assert.equal(crossDevicePage2.data.nextCursor,"movie:202");
  const crossDevicePage3=await req(
    "/v1/sync?profileId="+id+"&category=library&limit=1&after="+
      encodeURIComponent(crossDevicePage2.data.nextCursor),"GET",null,alice.token);
  assert.equal(crossDevicePage3.status,200);
  assert.equal(crossDevicePage3.data.items[0].payload.item.name,"Ajout Android TV");
  assert.equal(crossDevicePage3.data.partial,false);
  assert.equal(crossDevicePage3.data.nextCursor,null);

  // Suppression Windows transmise aux autres clients comme une tombstone :
  // le modèle TV doit la voir sans effacer les autres lignes.
  const deletion=await req("/v1/sync","POST",{
    profileId:id,category:"library",itemKey:"movie:101",
    payload:{},deleted:true,revision:2
  },alice.token);
  assert.equal(deletion.status,200);
  assert.equal(deletion.data.revision,3);
  const afterDelete=await req(
    "/v1/sync?profileId="+id+"&category=library&limit=100","GET",null,alice.token);
  assert.equal(afterDelete.status,200);
  assert.equal(afterDelete.data.items.length,3);
  const tombstone=afterDelete.data.items.find(x=>x.itemKey==="movie:101");
  assert.equal(tombstone.deleted,true);
  assert.equal(tombstone.revision,3);
  assert.equal(afterDelete.data.items.find(x=>x.itemKey==="movie:202").deleted,false);
  assert.equal(afterDelete.data.items.find(x=>x.itemKey==="series:303").deleted,false);

  // Un téléphone hors ligne gardant la révision 2 ne peut pas ressusciter le
  // favori supprimé à la révision 3 par une réécriture aveugle.
  const staleMobile=await req("/v1/sync","POST",{
    ...input,revision:2,payload:{item:{id:"101",type:"movie",name:"Vieille version"}}
  },alice.token);
  assert.equal(staleMobile.status,409);
  assert.equal(staleMobile.data?.current?.revision,3);
  const secondAccountWrite=await req("/v1/sync","POST",mobileEntry,bob.token);
  assert.equal(secondAccountWrite.status,404,"No cross-account write to another profile");

  const deniedMedia=await req("/v1/media?profileId="+id+"&kind=avatar","GET",null,bob.token);
  assert.equal(deniedMedia.status,503,"R2 disabled by design on ephemeral Worker");
  const accountName=await req("/v1/profiles/update","POST",
    {profileId:id,displayName:"Audit renommé"},alice.token);
  assert.equal(accountName.status,200);
  const afterRename=await req("/v1/profiles","GET",null,alice.token);
  assert.equal(afterRename.data.profiles.find(x=>x.id===id).displayName,"Audit renommé");
  const signOut=await req("/api/auth/sign-out","POST",{},alice.token);
  assert.ok(signOut.status>=200&&signOut.status<300);
  assert.equal((await req("/v1/me","GET",null,alice.token)).status,401,"Signout invalidates token");
  console.log("PASS: two real local Better Auth accounts, bearer login/logout, profile isolation, rename, D1 CAS, 3 simulated clients, multi-page D1, CAS conflicts, deletion tombstone, cross-account isolation, disabled R2");
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
