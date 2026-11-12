import assert from "node:assert/strict";
import {test} from "node:test";
import {spawn,spawnSync} from "node:child_process";
import {createServer} from "node:net";
import {mkdtempSync,writeFileSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {join,resolve} from "node:path";
import {randomUUID,createHash} from "node:crypto";
import {setTimeout as sleep} from "node:timers/promises";
const root=resolve(new URL("../",import.meta.url).pathname);
async function freePort(){
  const s=createServer();
  await new Promise((ok,fail)=>s.once("error",fail).listen(0,"127.0.0.1",ok));
  const port=s.address().port;
  await new Promise(ok=>s.close(ok));
  return port;
}
test("portable Better Auth and profile sync survive restart",{timeout:90000},async()=>{
  const dir=mkdtempSync(join(tmpdir(),"kv-portable-")),db=join(dir,"db.sqlite"),
    secretPath=join(dir,"secret.txt");
  writeFileSync(secretPath,"test-only-"+randomUUID()+"-portable-signing",{mode:0o600});
  const prep=spawnSync("python3",["portable/prepare.py","--empty","--output",db],
    {cwd:root,encoding:"utf8",timeout:15000});
  assert.equal(prep.status,0,"DB preparation: "+prep.stderr);
  const port=await freePort(),base="http://127.0.0.1:"+port;
  let child,log="";
  async function start(){
    child=spawn(process.execPath,["portable/server.mjs"],{
      cwd:root,env:{...process.env,KOVELYX_DB_FILE:db,
        KOVELYX_AUTH_SECRET_FILE:secretPath,KOVELYX_PORT:String(port),
        KOVELYX_REGISTRATION_OPEN:"true"},stdio:["ignore","pipe","pipe"]});
    for(const stream of [child.stdout,child.stderr])
      stream.on("data",data=>{log=(log+data).slice(-2500)});
    for(let tries=0;tries<60;tries++){
      if(child.exitCode!==null)throw Error("Server exited: "+log);
      try{if((await fetch(base+"/ready",{signal:AbortSignal.timeout(800)})).ok)return;}catch{}
      await sleep(250);
    }
    throw Error("Server not ready: "+log);
  }
  async function stop(){
    if(!child)return;
    const p=child;child=null;p.kill("SIGTERM");
    await Promise.race([new Promise(ok=>p.once("exit",ok)),sleep(2500)]);
    if(p.exitCode===null)p.kill("SIGKILL");
  }
  async function call(path,method="GET",body=null,token=null){
    const headers={accept:"application/json"};
    if(body){headers["content-type"]="application/json";headers.origin=base;}
    if(token)headers.authorization="Bearer "+token;
    const r=await fetch(base+path,{method,headers,
      body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(8000)});
    return {status:r.status,data:await r.json(),headers:r.headers};
  }
  try{
    await start();
    assert.equal((await call("/ready")).data.profilePinSchema,1);
    assert.equal((await call("/health")).data.provider,"SQLite portable");
    assert.equal((await call("/v1/profiles")).status,401);
    const email="kv-"+randomUUID()+"@example.test",password="Kovelyx-"+randomUUID()+"!";
    const signup=await call("/api/auth/sign-up/email","POST",{email,name:"Portable",password});
    assert.ok([200,201].includes(signup.status),"Sign-up failed: "+signup.status);
    const signin=await call("/api/auth/sign-in/email","POST",{email,password});
    assert.equal(signin.status,200,"Sign-in failed");
    const token=signin.headers.get("set-auth-token")||signin.data.token;
    assert.ok(token?.length>16,"Bearer token missing");
    const second=await call("/api/auth/sign-in/email","POST",{email,password});
    const tvToken=second.headers.get("set-auth-token")||second.data.token;
    assert.ok(tvToken?.length>16&&tvToken!==token);
    const created=await call("/v1/profiles","POST",{displayName:"Principal"},token);
    assert.equal(created.status,201);
    const profileId=created.data.profile.id;
    assert.equal((await call("/v1/profiles","GET",null,tvToken)).data.profiles[0].id,profileId);
    const record={profileId,category:"library",itemKey:"movie:restored",
      revision:0,deleted:false,payload:{id:"tt1",type:"movie"}};
    assert.equal((await call("/v1/sync","POST",record,token)).status,201);
    assert.equal((await call("/v1/sync","POST",record,tvToken)).status,409);
    assert.ok((await call("/v1/sync?profileId="+profileId,"GET",null,tvToken))
      .data.items.some(r=>r.itemKey===record.itemKey));
    assert.equal((await call("/v1/profile-pin","POST",{profileId,pin:"4321"},token)).status,201);
    // GIF/photo transport uses the same atomic binary D1 batch as Cloudflare.
    const image=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO9ASb8AAAAASUVORK5CYII=","base64");
    const imagePath="/v1/profile-media?profileId="+profileId+"&kind=avatar";
    const upload=await fetch(base+imagePath,{method:"PUT",
      headers:{authorization:"Bearer "+token,origin:base,"if-match":"none","content-type":"image/png"},
      body:image,signal:AbortSignal.timeout(8000)});
    assert.equal(upload.status,201,"Private avatar upload");
    const imageFromTv=await fetch(base+imagePath,{
      headers:{authorization:"Bearer "+tvToken},signal:AbortSignal.timeout(8000)});
    assert.equal(imageFromTv.status,200);
    assert.deepEqual(Buffer.from(await imageFromTv.arrayBuffer()),image,
      "Byte-identical profile image via a separate session");
    // Published catalogue snapshot may only become visible once all chunks exist.
    const snapshot=JSON.stringify({list:["Film é","Série 🎬"]});
    const sha=createHash("sha256").update(snapshot).digest("hex");
    const chunk={profileId,category:"catalogues",itemKey:"snapshot:collections:"+sha+":000000",
      payload:{index:0,text:snapshot},revision:0,deleted:false};
    assert.equal((await call("/v1/catalogue-chunk","POST",chunk,token)).status,201);
    assert.equal((await call("/v1/catalogue-manifest","POST",{
      profileId,kind:"collections",sha256:sha,parts:1,
      utf8Bytes:Buffer.byteLength(snapshot),revision:0},token)).status,201);
    const catalog=await call("/v1/sync?profileId="+profileId+
      "&snapshotKind=collections&snapshotHash="+sha,"GET",null,tvToken);
    assert.equal(catalog.status,200);
    assert.equal(catalog.data.items[0].payload.text,snapshot);
    const foreignEmail="other-"+randomUUID()+"@example.test";
    assert.equal((await call("/api/auth/sign-up/email","POST",
      {email:foreignEmail,name:"Other",password},null)).status,200);
    const foreignLogin=await call("/api/auth/sign-in/email","POST",
      {email:foreignEmail,password});
    const foreignToken=foreignLogin.headers.get("set-auth-token")||foreignLogin.data.token;
    assert.equal((await call("/v1/sync?profileId="+profileId,
      "GET",null,foreignToken)).status,404,"Another account cannot read private data");
    await stop();await start();
    assert.equal((await call("/v1/profiles","GET",null,token)).data.profiles[0].id,profileId);
    assert.ok((await call("/v1/sync?profileId="+profileId,"GET",null,tvToken))
      .data.items.some(r=>r.itemKey===record.itemKey));
    assert.equal((await call("/v1/profile-pin/verify","POST",
      {profileId,pin:"4321"},tvToken)).data.unlocked,true);
    const forbidden=await fetch(base+"/v1/profiles",{
      headers:{origin:"https://evil.example",authorization:"Bearer "+token}});
    assert.equal(forbidden.status,403);
  }finally{await stop();rmSync(dir,{recursive:true,force:true});}
});
