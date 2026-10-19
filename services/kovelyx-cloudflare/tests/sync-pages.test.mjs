import test from "node:test";
import assert from "node:assert/strict";
import {handleSync} from "../src/index.mjs";

const PROFILE="f4b81d5e-982f-4f37-9923-87f47920196c";
function database() {
  const data = [
    {owner:"alice", profile:PROFILE, category:"library",item_key:"movie:001",payload:JSON.stringify({item:{id:"001",type:"movie",name:"A",savedAtEpochMs:1}}),revision:1,deleted:0,updatedAt:1},
    {owner:"alice", profile:PROFILE, category:"library",item_key:"movie:002",payload:JSON.stringify({item:{id:"002",type:"movie",name:"B",savedAtEpochMs:2}}),revision:2,deleted:0,updatedAt:2},
    {owner:"alice", profile:PROFILE, category:"library",item_key:"movie:003",payload:JSON.stringify({}),revision:3,deleted:1,updatedAt:3},
    {owner:"bob",profile:PROFILE,category:"library",item_key:"movie:004",payload:JSON.stringify({secret:true}),revision:1,deleted:0,updatedAt:4}
  ];
  return {
    prepare(sql) {
      return {
        bind(...args) {
          return {
            async first() {
              assert.match(sql,/owner_id=\?/);
              const [id,owner]=args;
              return id===PROFILE && owner==="alice"?{id:PROFILE}:null;
            },
            async all() {
              assert.match(sql,/owner_id=\?/);
              assert.match(sql,/category=\?/);
              const [owner,profile,category,after,limit]=args;
              return {results:data.filter(row=>row.owner===owner&&row.profile===profile&&row.category===category&&row.item_key>after)
                .sort((a,b)=>a.item_key.localeCompare(b.item_key)).slice(0,limit)
                .map(row=>({category:row.category,itemKey:row.item_key,payload:row.payload,revision:row.revision,deleted:row.deleted,updatedAt:row.updatedAt}))};
            },
          };
        },
      };
    },
  };
}
const base="https://kovelyx-cloud.example/v1/sync?profileId="+PROFILE+"&category=library";
async function load(query,owner="alice") {
  const url=new URL(query);
  const response=await handleSync(new Request(url),{DB:database()},{id:owner},url);
  return {status:response.status,data:await response.json()};
}
test("category pagination covers all records, including tombstones, without leaking users",async()=>{
  const first=await load(base+"&limit=2");
  assert.equal(first.status,200);
  assert.deepEqual(first.data.items.map(x=>x.itemKey),["movie:001","movie:002"]);
  assert.equal(first.data.nextCursor,"movie:002");
  assert.equal(first.data.partial,true);
  const last=await load(base+"&limit=2&after=movie%3A002");
  assert.deepEqual(last.data.items.map(x=>x.itemKey),["movie:003"]);
  assert.equal(last.data.nextCursor,null);
  assert.equal(last.data.partial,false);
  assert.equal(last.data.items[0].deleted,true);
  assert.equal(last.data.items.find(x=>x.itemKey==="movie:004"),undefined);
});
test("category pagination rejects bad limits, category, and unknown ownership",async()=>{
  assert.equal((await load(base+"&limit=101")).status,400);
  assert.equal((await load(base+"&limit=0")).status,400);
  assert.equal((await load(base.replace("category=library","category=credentials"))).status,400);
  assert.equal((await load(base,"bob")).status,404);
});
