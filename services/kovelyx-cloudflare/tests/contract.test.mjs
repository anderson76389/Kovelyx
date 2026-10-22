import test from "node:test";
import assert from "node:assert/strict";
import {validProfileName, validSyncInput, isAllowedOrigin} from "../src/contract.mjs";
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
test("profile names and origins",()=>{
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
});
