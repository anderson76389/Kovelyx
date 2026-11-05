import test from "node:test";
import assert from "node:assert/strict";
import {pinHash,validPin} from "../src/profile-pin.mjs";

test("PIN length is restricted and never accepts free text",()=>{
  for(const pin of ["1234","123456","12345678"]) assert.equal(validPin(pin),true);
  for(const pin of ["123","123456789","abcd","123a",null,1234])
    assert.equal(validPin(pin),false);
});

test("secret-based profile PIN HMAC is salted, owner-scoped and deterministic",async()=>{
  const secret="local-only-long-auth-secret-with-at-least-32-characters";
  const a=await pinHash(secret,"alice","11111111-1111-4111-8111-111111111111","a".repeat(32),"1234");
  const b=await pinHash(secret,"alice","11111111-1111-4111-8111-111111111111","b".repeat(32),"1234");
  const c=await pinHash(secret,"bob","11111111-1111-4111-8111-111111111111","a".repeat(32),"1234");
  const d=await pinHash(secret,"alice","11111111-1111-4111-8111-111111111111","a".repeat(32),"1235");
  assert.match(a,/^[0-9a-f]{64}$/);
  assert.notEqual(a,b);
  assert.notEqual(a,c);
  assert.notEqual(a,d);
  assert.equal(a,await pinHash(secret,"alice","11111111-1111-4111-8111-111111111111","a".repeat(32),"1234"));
  assert.ok(!a.includes("1234"),"No raw PIN in stored hash");
});
