import test from "node:test";
import assert from "node:assert/strict";
import {handleProfileMedia,validMedia,MEDIA_LIMIT} from "../src/media.mjs";
const id="9c0b91b1-f61b-4483-8120-bda141b9c0e9";
const url=(kind="avatar")=>new URL("https://kovelyx.example/v1/media?profileId="+id+"&kind="+kind);
const png=Uint8Array.from([137,80,78,71,13,10,26,10,0,0,0,0]);
const gif=new TextEncoder().encode("GIF89aabc");
const video=new TextEncoder().encode("....ftypisom");
function ctx() {
 const state={old:null,owner:"alice",objects:new Map(),writes:[]};
 const DB={prepare(sql){return {bind(...args){return {
  async first(){return args[0]===state.owner&&args[1]===id?{avatarPath:state.old,backgroundPath:null}:null},
  async run(){state.writes.push({sql,args});state.old=sql.includes("=NULL")?null:args[0];return {meta:{changes:1}}}
 }}}}};
 const MEDIA={
   async put(k,bytes,info){state.objects.set(k,{body:bytes,httpMetadata:info.httpMetadata});},
   async get(k){return state.objects.get(k)||null;},
   async delete(k){state.objects.delete(k)}
 };
 return {state,env:{DB,MEDIA}};
}
test("recognizes original GIF and video; rejects renamed executable and large content",()=>{
 assert.equal(validMedia(gif,"image/gif"),true);
 assert.equal(validMedia(video,"video/mp4"),true);
 assert.equal(validMedia(new TextEncoder().encode("MZ"),"image/png"),false);
 assert.equal(validMedia(png,"image/png"),true);
 assert.equal(validMedia(new Uint8Array(MEDIA_LIMIT+1),"image/gif"),false);
});
test("upload/download are private to authenticated profile owner",async()=>{
 const {env,state}=ctx();
 const request=new Request(url(),{method:"PUT",headers:{"content-type":"image/gif"},body:gif});
 const result=await handleProfileMedia(request,env,{id:"alice"},url());
 assert.equal(result.status,200);
 assert.equal(state.writes.length,1);
 assert.ok(state.old.startsWith("users/alice/profiles/"+id+"/avatar/"));
 const fetched=await handleProfileMedia(new Request(url()),env,{id:"alice"},url());
 assert.equal(fetched.status,200);
 assert.equal(fetched.headers.get("content-type"),"image/gif");
 assert.deepEqual(new Uint8Array(await fetched.arrayBuffer()),gif);
 const blocked=await handleProfileMedia(new Request(url()),env,{id:"bob"},url());
 assert.equal(blocked.status,404);
});
test("disabled R2 never accepts write; invalid formats and profile identifiers fail",async()=>{
 const {env,state}=ctx();
 assert.equal((await handleProfileMedia(new Request(url()),{DB:env.DB},{id:"alice"},url())).status,503);
 const invalid=new Request(url(),{method:"PUT",headers:{"content-type":"application/octet-stream"},body:gif});
 assert.equal((await handleProfileMedia(invalid,env,{id:"alice"},url())).status,413);
 assert.equal(state.writes.length,0);
 const bad=url();bad.searchParams.set("profileId","../alice");
 assert.equal((await handleProfileMedia(new Request(bad),env,{id:"alice"},bad)).status,400);
});

test("explicit owner delete clears R2 path and prevents stale TV media",async()=>{
 const {env,state}=ctx();
 await handleProfileMedia(new Request(url(),{method:"PUT",headers:{"content-type":"image/gif"},body:gif}),env,{id:"alice"},url());
 assert.equal(state.objects.size,1);
 const removed=await handleProfileMedia(new Request(url(),{method:"DELETE"}),env,{id:"alice"},url());
 assert.equal(removed.status,200);
 assert.equal(state.objects.size,0);
 assert.equal(state.old,null);
 assert.equal((await handleProfileMedia(new Request(url()),env,{id:"alice"},url())).status,404);
});
