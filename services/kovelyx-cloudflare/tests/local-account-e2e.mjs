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
      // Native clients omit Origin; browsers are separately restricted by CORS.
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
  assert.notEqual(alice.userId,bob.userId,"Account isolation");
  const created=await req("/v1/profiles","POST",{displayName:"Principal"},alice.token);
  assert.equal(created.status,201,"Create principal profile");
  const profileId=created.data?.profile?.id;
  assert.match(profileId||"",/^[a-f0-9-]{36}$/i);
  const otherUser=await req("/v1/sync?profileId="+profileId,"GET",null,bob.token);
  assert.equal(otherUser.status,404,"Foreign account must not read D1 profile");
  assert.equal((await req("/v1/profiles","GET",null,bob.token)).data.profiles.length,0);

  const entry=(n)=>({
    profileId,category:"library",itemKey:"movie:"+String(n).padStart(3,"0"),
    revision:0,deleted:false,
    payload:{id:"tt"+n,type:"movie",name:"Film "+n,savedAtEpochMs:1720010000000+n}
  });
  const first=await req("/v1/sync","POST",entry(1),alice.token);
  assert.equal(first.status,201,"Insert first library item");
  assert.equal(first.data.revision,1);
  assert.equal((await req("/v1/sync","POST",entry(1),alice.token)).status,409,
    "Stale CAS writes rejected");

  for(let n=2;n<=31;n++){
    const result=await req("/v1/sync","POST",entry(n),alice.token);
    assert.equal(result.status,201,"Library insert "+n);
  }
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

  const watched={
    profileId,category:"watched_items",itemKey:"movie:tt1:-1:-1",revision:0,
    deleted:false,payload:{id:"tt1",type:"movie",name:"Film 1",markedAtEpochMs:1720010600000}
  };
  assert.equal((await req("/v1/sync","POST",watched,alice.token)).status,201,
    "Watched history category requires migration 0002");
  const all=await req("/v1/sync?profileId="+profileId+"&limit=25&offset=25","GET",null,alice.token);
  assert.ok(all.data.items.some(x=>x.category==="watched_items" || x.category==="watch_progress"));
  assert.equal((await req("/v1/sync","POST",watched,bob.token)).status,404,
    "Other user must never write watched state");
  assert.equal((await req("/v1/sync","POST",{
    ...watched,itemKey:"movie:private",payload:{access_token:"private"}
  },alice.token)).status,400,"Credential fields must not sync");

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
  console.log("PASS: two local Better Auth accounts, D1 0002 watched state, library 31 items, offset pagination, progress, tombstones, CAS 409, profile rename, isolation, sign-out");
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
