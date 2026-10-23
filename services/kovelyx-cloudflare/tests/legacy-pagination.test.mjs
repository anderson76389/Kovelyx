import test from "node:test";
import assert from "node:assert/strict";
import {handleSync} from "../src/index.mjs";
const PROFILE="f4b81d5e-982f-4f37-9923-87f47920196c";
const records=Array.from({length:551},(_,n)=>({
 category:"library", itemKey:"movie:"+String(n).padStart(3,"0"),
 payload:JSON.stringify({item:{id:String(n)}}),revision:1,deleted:n===8?1:0,updatedAt:n
}));
function database() {
 return {prepare(sql) {
  return {bind(...args) {
   return {
    async first() {
     const [id,owner]=args;
     return id===PROFILE && owner==="alice"?{id:PROFILE}:null;
    },
    async all() {
     assert.match(sql,/ORDER BY category, item_key LIMIT \? OFFSET \?/);
     const [owner,id,limit,offset]=args;
     assert.equal(owner,"alice");
     assert.equal(id,PROFILE);
     return {results:records.slice(offset,offset+limit)};
    }
   };
  }};
 }};
}
async function load(query,owner="alice") {
 const url=new URL("https://cloud.example/v1/sync?profileId="+PROFILE+query);
 const result=await handleSync(new Request(url),{DB:database()},{id:owner},url);
 return {status:result.status,body:await result.json()};
}
test("legacy client pagination reads over 500 items without hard cap",async()=>{
 let offset=0, all=[], pages=0;
 while (true) {
  const page=await load("&limit=25&offset="+offset);
  assert.equal(page.status,200);
  all.push(...page.body.items);
  pages++;
  if (page.body.nextOffset===null) {
   assert.equal(page.body.partial,false);
   break;
  }
  assert.equal(page.body.nextOffset,offset+25);
  offset=page.body.nextOffset;
  assert.ok(pages<30,"pagination must terminate");
 }
 assert.equal(all.length,551);
 assert.equal(all[8].deleted,true);
 assert.ok(pages>20,"more than 500 sync rows must be retrievable");
});
test("legacy cursors reject bad input and foreign profiles",async()=>{
 assert.equal((await load("&offset=-1")).status,400);
 assert.equal((await load("&offset=1000000")).status,400);
 assert.equal((await load("&limit=26")).status,400);
 assert.equal((await load("&limit=0")).status,400);
 assert.equal((await load("","bob")).status,404);
});
