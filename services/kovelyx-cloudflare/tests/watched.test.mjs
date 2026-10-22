import test from "node:test";
import assert from "node:assert/strict";
import {watchedKey,validWatchedMutation} from "../src/watched.mjs";

const profileId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const film={
  contentId:"tt0111161",contentType:"movie",title:"Un film",
  season:null,episode:null,watchedAt:1720010500000
};
const episode={
  contentId:"tt0944947",contentType:"series",title:"Une série",
  season:2,episode:4,watchedAt:1720010600000
};

test("stable media and episode identities match all three clients",()=>{
  assert.equal(watchedKey(film),"tt0111161");
  assert.equal(watchedKey(episode),"tt0944947_s2e4");
  assert.equal(watchedKey({...episode,season:null}),null);
  assert.equal(watchedKey({...film,contentId:"https://private.example/stream?token=1"}),null);
});

test("only safe watched metadata and CAS revisions may be persisted",()=>{
  const input={profileId,itemKey:"tt0111161",payload:{item:film},revision:0,deleted:false};
  assert.equal(validWatchedMutation(input),true);
  assert.equal(validWatchedMutation({...input,revision:1}),true);
  assert.equal(validWatchedMutation({...input,revision:-1}),false);
  assert.equal(validWatchedMutation({...input,profileId:"another-account"}),false);
  assert.equal(validWatchedMutation({...input,itemKey:"a-different-film"}),false);
  assert.equal(validWatchedMutation({...input,payload:{item:{...film,password:"hidden"}}}),false);
  assert.equal(validWatchedMutation({...input,payload:{item:{...film,accessToken:"hidden"}}}),false);
  assert.equal(validWatchedMutation({...input,payload:{item:{...film,contentId:"https://private/video"}}}),false);
  assert.equal(validWatchedMutation({...input,deleted:true,payload:{}}),true);
  assert.equal(validWatchedMutation({...input,deleted:true,payload:{item:film}}),false);
  assert.equal(validWatchedMutation({...input,deleted:false,payload:{}}),false);
});
