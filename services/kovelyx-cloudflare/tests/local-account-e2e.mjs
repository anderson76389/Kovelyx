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
  console.log("PASS: two real local Better Auth accounts, bearer login/logout, profile isolation, rename, D1 CAS, sync pagination, disabled R2");
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
