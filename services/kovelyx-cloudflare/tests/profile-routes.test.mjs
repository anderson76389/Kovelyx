import test from "node:test";
import assert from "node:assert/strict";
import {handleProfiles} from "../src/index.mjs";

const MAIN = "f4b81d5e-982f-4f37-9923-87f47920196c";
const SECOND = "a4b81d5e-982f-4f37-9923-87f47920196c";
const OTHER = "b4b81d5e-982f-4f37-9923-87f47920196c";

function fakeDatabase() {
  const profiles = [
    {id:MAIN,owner_id:"alice",display_name:"Principal"},
    {id:SECOND,owner_id:"alice",display_name:"Enfant"},
    {id:OTHER,owner_id:"bob",display_name:"Privé"}
  ];
  return {
    profiles,
    prepare(sql) {
      return {
        bind(...args) {
          return {
            async run() {
              if (sql.startsWith("UPDATE kv_profiles")) {
                const [name,,id,owner] = args;
                const p = profiles.find(p=>p.id===id && p.owner_id===owner);
                if (p) p.display_name=name;
                return {meta:{changes:p?1:0}};
              }
              if (sql.startsWith("DELETE FROM kv_profiles")) {
                const [id,owner,checkedOwner,principalOwner] = args;
                assert.equal(owner,checkedOwner);
                assert.equal(owner,principalOwner);
                const matches = profiles.findIndex(p=>p.id===id && p.owner_id===owner);
                const owned = profiles.filter(p=>p.owner_id===owner);
                const canDelete = owned.length>1 && owned[0]?.id!==id;
                if (matches<0 || !canDelete) return {meta:{changes:0}};
                profiles.splice(matches,1);
                return {meta:{changes:1}};
              }
              throw new Error("unexpected SQL run");
            },
            async first() {
              assert.match(sql,/owner_id=\?/);
              const [id,owner] = args;
              return profiles.find(p=>p.id===id && p.owner_id===owner) || null;
            },
            async all() {
              assert.match(sql,/WHERE owner_id=\?/);
              return {results:profiles.filter(p=>p.owner_id===args[0])
                .map(p=>({id:p.id,displayName:p.display_name}))};
            }
          };
        }
      };
    }
  };
}
function request(path,body,method="POST") {
  const url="https://kovelyx-cloud.example"+path;
  const req=new Request(url,{
    method,headers:{"content-type":"application/json"},
    ...(method==="GET"?{}:{body:JSON.stringify(body)})
  });
  return [req,new URL(url)];
}

test("profiles update uses account ownership, never another user",async()=>{
  const db=fakeDatabase();
  const [badReq,badUrl]=request("/v1/profiles/update",{profileId:OTHER,displayName:"Volé"});
  const bad=await handleProfiles(badReq,{DB:db},{id:"alice"},badUrl);
  assert.equal(bad.status,404);
  assert.equal(db.profiles.find(p=>p.id===OTHER).display_name,"Privé");

  const [req,url]=request("/v1/profiles/update",{profileId:MAIN,displayName:"Mon profil"});
  const good=await handleProfiles(req,{DB:db},{id:"alice"},url);
  assert.equal(good.status,200);
  assert.equal(db.profiles.find(p=>p.id===MAIN).display_name,"Mon profil");
});

test("profile deletion is account-scoped and cannot remove last profile",async()=>{
  const db=fakeDatabase();
  const [otherReq,otherUrl]=request("/v1/profiles/delete",{profileId:OTHER});
  assert.equal((await handleProfiles(otherReq,{DB:db},{id:"alice"},otherUrl)).status,404);
  const [primaryReq,primaryUrl]=request("/v1/profiles/delete",{profileId:MAIN});
  assert.equal((await handleProfiles(primaryReq,{DB:db},{id:"alice"},primaryUrl)).status,409);
  const [secondReq,secondUrl]=request("/v1/profiles/delete",{profileId:SECOND});
  assert.equal((await handleProfiles(secondReq,{DB:db},{id:"alice"},secondUrl)).status,200);
  const [lastReq,lastUrl]=request("/v1/profiles/delete",{profileId:MAIN});
  assert.equal((await handleProfiles(lastReq,{DB:db},{id:"alice"},lastUrl)).status,409);
  assert.equal(db.profiles.filter(p=>p.owner_id==="alice").length,1);
});

test("profile routes reject invalid bodies and unsupported methods",async()=>{
  const db=fakeDatabase();
  const [noUuid,url]=request("/v1/profiles/update",{profileId:"../../bob",displayName:"Non"});
  assert.equal((await handleProfiles(noUuid,{DB:db},{id:"alice"},url)).status,400);
  const [invalidName,nameUrl]=request("/v1/profiles/update",{profileId:MAIN,displayName:"    "});
  assert.equal((await handleProfiles(invalidName,{DB:db},{id:"alice"},nameUrl)).status,400);
  const [getReq,getUrl]=request("/v1/profiles/delete",null,"GET");
  assert.equal((await handleProfiles(getReq,{DB:db},{id:"alice"},getUrl)).status,405);
});


test("PATCH and DELETE remain compatible with already published clients",async()=>{
  const db=fakeDatabase();
  const [update,url]=request("/v1/profiles?profileId="+SECOND,{displayName:"Télévision"},"PATCH");
  const good=await handleProfiles(update,{DB:db},{id:"alice"},url);
  assert.equal(good.status,200);
  assert.equal(db.profiles.find(p=>p.id===SECOND).display_name,"Télévision");

  const [bad,other]=request("/v1/profiles?profileId="+OTHER,{displayName:"Pirate"},"PATCH");
  assert.equal((await handleProfiles(bad,{DB:db},{id:"alice"},other)).status,404);
  const [forbidden,principal]=request("/v1/profiles?profileId="+MAIN,null,"DELETE");
  assert.equal((await handleProfiles(forbidden,{DB:db},{id:"alice"},principal)).status,409);
  const [deletion,location]=request("/v1/profiles?profileId="+SECOND,null,"DELETE");
  const ok=await handleProfiles(deletion,{DB:db},{id:"alice"},location);
  assert.equal(ok.status,200);
  assert.equal((await ok.json()).id,SECOND);
  assert.equal(db.profiles.filter(p=>p.owner_id==="alice").length,1);
});
