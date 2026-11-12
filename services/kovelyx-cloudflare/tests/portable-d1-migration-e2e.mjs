import assert from "node:assert/strict";
import {test} from "node:test";
import {spawn,spawnSync} from "node:child_process";
import {createServer} from "node:net";
import {mkdtempSync,readFileSync,writeFileSync,rmSync,existsSync} from "node:fs";
import {tmpdir} from "node:os";
import {join,resolve} from "node:path";
import {randomUUID,createHash} from "node:crypto";
import {setTimeout as sleep} from "node:timers/promises";

/**
 * Disaster recovery proof on throwaway data:
 * 1) Create a REAL local workerd/D1 database via Wrangler.
 * 2) Write accounts, profiles, PINs, library, watched/progress and avatar.
 * 3) Export genuine D1 SQL using Wrangler.
 * 4) Restore that export with the portable SQLite preparer.
 * 5) Start the portable HTTP server with the SAME Better Auth secret.
 * 6) Authenticate and check data from multiple independent sessions.
 *
 * CI runs this INSIDE Docker --network none (loopback only).
 * Never points to production; no Cloudflare credentials or private data.
 */
const root=resolve(new URL("../",import.meta.url).pathname);
async function freePort() {
  const s=createServer();
  await new Promise((ok,fail)=>s.once("error",fail).listen(0,"127.0.0.1",ok));
  const port=s.address().port;
  await new Promise(ok=>s.close(ok));
  return port;
}
function command(program,args,timeout=45000) {
  const r=spawnSync(program,args,{cwd:root,encoding:"utf8",timeout,
    env:{...process.env,WRANGLER_SEND_METRICS:"false"}});
  if(r.status!==0)throw Error(program+" "+args.slice(0,3).join(" ")+
    " failed (status "+r.status+"): "+(r.stderr||r.stdout).slice(-1800));
}
async function shutdown(proc) {
  if(!proc)return;
  try {
    // Wrangler via npx has children. Keep process group isolated.
    if(process.platform!=="win32")process.kill(-proc.pid,"SIGTERM");
    else proc.kill("SIGTERM");
  } catch {}
  await Promise.race([new Promise(ok=>proc.once("exit",ok)),sleep(1800)]);
  if(proc.exitCode===null) {
    try{if(process.platform!=="win32")process.kill(-proc.pid,"SIGKILL");
      else proc.kill("SIGKILL");}catch{}
  }
}
async function waitReady(base,proc,logs) {
  for(let i=0;i<90;i++) {
    if(proc.exitCode!==null)throw Error("Server stopped: "+logs());
    try {
      const r=await fetch(base+"/ready",{signal:AbortSignal.timeout(900)});
      if(r.status===200)return;
    } catch {}
    await sleep(250);
  }
  throw Error("Server not ready (local test): "+logs());
}
async function request(base,path,method="GET",data=null,bearer=null,extra={}) {
  const headers={accept:"application/json",...extra};
  if(data!==null){headers["content-type"]="application/json";headers.origin=base;}
  if(bearer)headers.authorization="Bearer "+bearer;
  const r=await fetch(base+path,{method,headers,
    body:data===null?undefined:JSON.stringify(data),signal:AbortSignal.timeout(10000)});
  const response=await r.json().catch(()=>null);
  return {status:r.status,body:response,headers:r.headers};
}

test("genuine local D1 SQL -> SQLite accounts, PINs, catalogue, media and library",{
  timeout:180000
},async()=>{
  const folder=mkdtempSync(join(tmpdir(),"kv-offline-proof-"));
  const id=randomUUID().replaceAll("-","");
  const config="wrangler.audit.blackout."+id+".toml";
  const configFile=join(root,config);
  const persistence=join(folder,"d1"),sql=join(folder,"export.sql"),db=join(folder,"restored.sqlite");
  const secretPath=join(folder,"secret.txt");
  const secret="test-only-same-secret-"+randomUUID()+"-keep-out-of-production";
  writeFileSync(secretPath,secret,{mode:0o600});
  const template=readFileSync(join(root,"wrangler.toml"),"utf8");
  assert.ok(template.includes("REPLACE_WITH_YOUR_D1_DATABASE_ID"));
  writeFileSync(configFile,template.replace("REPLACE_WITH_YOUR_D1_DATABASE_ID",
    "00000000-0000-4000-8000-000000000001"));
  const d1Port=await freePort();
  const localPort=await freePort();
  const workerBase="http://127.0.0.1:"+d1Port,portableBase="http://127.0.0.1:"+localPort;
  let worker,server,logs="";
  try{
    command("npx",["--no-install","wrangler","d1","migrations","apply","DB",
      "--local","--persist-to",persistence,"--config",config],90000);
    worker=spawn("npx",["--no-install","wrangler","dev","--local",
      "--config",config,"--persist-to",persistence,
      "--ip","127.0.0.1","--port",String(d1Port),
      "--var","BETTER_AUTH_SECRET:"+secret,
      "--var","KOVELYX_REGISTRATION_OPEN:true"],{
      cwd:root,detached:true,stdio:["ignore","pipe","pipe"],
      env:{...process.env,WRANGLER_SEND_METRICS:"false"}
    });
    for(const stream of [worker.stdout,worker.stderr])
      stream.on("data",b=>{logs=(logs+b.toString()).slice(-3000)});
    await waitReady(workerBase,worker,()=>logs);
    const email="offline-"+randomUUID()+"@example.test";
    const password="Test-"+randomUUID()+"-strong!";
    let r=await request(workerBase,"/api/auth/sign-up/email","POST",
      {email,name:"Proprietaire test",password});
    assert.ok([200,201].includes(r.status),"Local D1 signup: "+r.status);
    r=await request(workerBase,"/api/auth/sign-in/email","POST",{email,password});
    assert.equal(r.status,200,"Local D1 signin");
    const token=r.headers.get("set-auth-token")||r.body?.token;
    assert.ok(token?.length>=16,"Bearer from genuine Wrangler/D1");
    const p=await request(workerBase,"/v1/profiles","POST",{displayName:"Principal"},token);
    assert.equal(p.status,201);
    const profileId=p.body.profile.id;
    const record={profileId,category:"library",itemKey:"movie:blackout",revision:0,
      deleted:false,payload:{id:"tt-original",type:"movie",name:"Archive"}};
    assert.equal((await request(workerBase,"/v1/sync","POST",record,token)).status,201);
    assert.equal((await request(workerBase,"/v1/sync","POST",{
      ...record,category:"watched_items",itemKey:"movie:tt-original:-1:-1",
      payload:{id:"tt-original",type:"movie",markedAtEpochMs:1234567890000}
    },token)).status,201);
    assert.equal((await request(workerBase,"/v1/sync","POST",{
      ...record,category:"watch_progress",itemKey:"movie:tt-original",
      payload:{lastPositionMs:29000,durationMs:60000,isCompleted:false}
    },token)).status,201);
    assert.equal((await request(workerBase,"/v1/profile-pin","POST",{
      profileId,pin:"1842"},token)).status,201);
    const bytes=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO9ASb8AAAAASUVORK5CYII=","base64");
    const mediaPath="/v1/profile-media?profileId="+profileId+"&kind=avatar";
    const media=await fetch(workerBase+mediaPath,{method:"PUT",
      headers:{authorization:"Bearer "+token,origin:workerBase,"if-match":"none",
        "content-type":"image/png"},body:bytes,signal:AbortSignal.timeout(12000)});
    assert.equal(media.status,201,"Local D1 avatar created");
    const snapshot=JSON.stringify({name:"Films français",items:["à","🎬"]});
    const hash=createHash("sha256").update(snapshot).digest("hex");
    assert.equal((await request(workerBase,"/v1/catalogue-chunk","POST",{
      profileId,category:"catalogues",itemKey:"snapshot:collections:"+hash+":000000",
      payload:{index:0,text:snapshot},revision:0,deleted:false
    },token)).status,201);
    assert.equal((await request(workerBase,"/v1/catalogue-manifest","POST",{
      profileId,kind:"collections",sha256:hash,parts:1,
      utf8Bytes:Buffer.byteLength(snapshot),revision:0
    },token)).status,201);
    await shutdown(worker);worker=null;
    command("npx",["--no-install","wrangler","d1","export","DB","--local",
      "--persist-to",persistence,"--config",config,"--output",sql],90000);
    assert.ok(existsSync(sql),"Wrangler must generate genuine D1 export");
    command("python3",["portable/prepare.py","--from-sql",sql,"--output",db]);
    server=spawn(process.execPath,["portable/server.mjs"],{
      cwd:root,detached:true,stdio:["ignore","pipe","pipe"],
      env:{...process.env,KOVELYX_DB_FILE:db,KOVELYX_AUTH_SECRET_FILE:secretPath,
        KOVELYX_PORT:String(localPort)}
    });
    logs="";
    for(const stream of [server.stdout,server.stderr])
      stream.on("data",b=>{logs=(logs+b.toString()).slice(-3000)});
    await waitReady(portableBase,server,()=>logs);
    assert.equal((await request(portableBase,"/health")).body.provider,"SQLite portable");
    // Retained token proves the original Better Auth sessions survived the
    // D1 export and subsequent backend switch.
    const me=await request(portableBase,"/v1/me","GET",null,token);
    assert.equal(me.status,200,"Original D1 session survives offline backend migration");
    assert.equal(me.body.email,email);
    const signed=await request(portableBase,"/api/auth/sign-in/email","POST",{email,password});
    assert.equal(signed.status,200,"Same password works against restored Better Auth");
    const anotherToken=signed.headers.get("set-auth-token")||signed.body?.token;
    assert.ok(anotherToken?.length>=16);
    assert.equal((await request(portableBase,"/v1/profiles","GET",null,anotherToken))
      .body.profiles[0].id,profileId);
    const collection=await request(portableBase,"/v1/sync?profileId="+profileId,
      "GET",null,anotherToken);
    assert.equal(collection.status,200);
    for(const category of ["library","watched_items","watch_progress"])
      assert.ok(collection.body.items.some(row=>row.category===category),
        "Restore must retain "+category);
    assert.equal((await request(portableBase,"/v1/profile-pin/verify","POST",
      {profileId,pin:"1842"},anotherToken)).body.unlocked,true);
    const returned=await fetch(portableBase+mediaPath,{
      headers:{authorization:"Bearer "+anotherToken}});
    assert.equal(returned.status,200);
    assert.deepEqual(Buffer.from(await returned.arrayBuffer()),bytes);
    const published=await request(portableBase,"/v1/sync?profileId="+profileId+
      "&snapshotKind=collections&snapshotHash="+hash,"GET",null,anotherToken);
    assert.equal(published.status,200);
    assert.equal(published.body.items[0]?.payload?.text,snapshot);
    assert.equal((await request(portableBase,"/v1/sync","POST",record,token)).status,409,
      "Revisions remain protected after migration");
    // If this test runs inside Docker --network none, neither backend can
    // communicate with Nuvio, Cloudflare, GitHub or any external API.
    console.log("PASS: genuine local D1 export -> portable SQLite; original session,"+
      " password, profile, PIN, library, watched, progress, avatar and catalogue retained");
  } finally {
    await shutdown(worker);
    await shutdown(server);
    rmSync(configFile,{force:true});
    rmSync(folder,{force:true,recursive:true});
  }
});
