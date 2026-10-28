import test from "node:test";
import assert from "node:assert/strict";
import {validProfileName, validProfileId, validSyncOffset, validSyncInput, isAllowedOrigin} from "../src/contract.mjs";
const base = {profileId:"f4b81d5e-982f-4f37-9923-87f47920196c",
  category:"favorites",itemKey:"movie:123",revision:0,deleted:false,payload:{watched:true}};
test("validates user-owned synchronization shape",()=>{
  assert.equal(validSyncInput(base),true);
  assert.equal(validSyncInput({...base,category:"credentials"}),false);
  assert.equal(validSyncInput({...base,payload:{access_token:"sensitive"}}),false);
  assert.equal(validSyncInput({...base,revision:-1}),false);
  assert.equal(validSyncInput({...base,profileId:"../other-user"}),false);
  assert.equal(validSyncInput({...base,payload:{text:"a".repeat(33000)}}),false);
});
test("rejects secret fields inside nested profile settings and encoded JSON",()=>{
  const settings={...base,category:"profile_settings",itemKey:"mobile",
    payload:{settings:{features:{theme_settings:{amoled:true}}}}};
  assert.equal(validSyncInput(settings),true);
  assert.equal(validSyncInput({...settings,payload:{settings:{features:{
    debrid_settings:{debrid_alldebrid_api_key:"very-secret"}
  }}}}),false);
  assert.equal(validSyncInput({...settings,payload:{settings:{
    trakt_settings_payload:JSON.stringify({access_token:"secret"})
  }}}),false);
  assert.equal(validSyncInput({...settings,payload:{settings:{features:{
    nested:{apiKey:"secret"}
  }}}}),false);
});

test("profile names, strict identifiers, cursor pagination and origins",()=>{
  assert.equal(validProfileId(base.profileId),true);
  assert.equal(validProfileId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),false);
  assert.equal(validSyncOffset("0"),true);
  assert.equal(validSyncOffset("500"),true);
  assert.equal(validSyncOffset("-1"),false);
  assert.equal(validSyncOffset("1000000"),false);
  assert.equal(validSyncInput({...base,category:"watched_items"}),true);
  assert.equal(validProfileName("Mon profil"),true);
  assert.equal(validProfileName(""),false);
  assert.equal(validProfileName("x".repeat(65)),false);
  assert.equal(isAllowedOrigin("https://anderson76389.github.io"),true);
  assert.equal(isAllowedOrigin(null, "https://kovelyx-cloud.example.workers.dev"),true);
  assert.equal(isAllowedOrigin("https://kovelyx-cloud.example.workers.dev", "https://kovelyx-cloud.example.workers.dev"),true);
  assert.equal(isAllowedOrigin("https://evil.example", "https://kovelyx-cloud.example.workers.dev"),false);
  assert.equal(isAllowedOrigin("https://kovelyx-cloud.example.workers.dev.evil.example", "https://kovelyx-cloud.example.workers.dev"),false);
  assert.equal(isAllowedOrigin("http://kovelyx-cloud.example.workers.dev", "http://kovelyx-cloud.example.workers.dev"),false);
  assert.equal(isAllowedOrigin("https://evil.example"),false);
  assert.equal(isAllowedOrigin("http://127.0.0.1:8765","http://127.0.0.1:8765"),true);
  assert.equal(isAllowedOrigin("http://localhost:8787","http://localhost:8787"),true);
  assert.equal(isAllowedOrigin("http://evil.example","http://evil.example"),false);
  assert.equal(isAllowedOrigin("http://127.0.0.1:8765","http://localhost:8787"),false);
});
