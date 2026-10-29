import {test} from 'node:test';
import assert from 'node:assert/strict';
import {profileImageMime,MAX_PROFILE_MEDIA_BYTES} from '../src/profile-media.mjs';
test('only supported raster signatures are accepted, not SVG or HTML',()=>{
  assert.equal(profileImageMime(Uint8Array.from([137,80,78,71,13,10,26,10])),'image/png');
  assert.equal(profileImageMime(Uint8Array.from([255,216,255])),'image/jpeg');
  assert.equal(profileImageMime(new TextEncoder().encode('GIF89a0123456')),'image/gif');
  assert.equal(profileImageMime(new TextEncoder().encode('RIFFxxxxWEBP')),'image/webp');
  assert.equal(profileImageMime(new TextEncoder().encode('<svg><script>alert(1)</script></svg>')),null);
  assert.equal(profileImageMime(new Uint8Array()),null);
  assert.equal(MAX_PROFILE_MEDIA_BYTES,2097152);
});

import {handleProfileMedia} from '../src/profile-media.mjs';
test('cascade child deletions count as a successful media deletion, stale writes do not',async()=>{
  const profile='12345678-1234-4234-8234-123456789012';
  const version='a'.repeat(64);
  const url=new URL(`https://worker.example/v1/profile-media?profileId=${profile}&kind=avatar`);
  const request=new Request(url,{method:'DELETE',headers:{'If-Match':version}});
  const environment=(changes)=>({DB:{
    prepare(sql){return {bind(){return {first:async()=>sql.startsWith('SELECT id')?{id:profile}:{version,mime:'image/png',size:1}}}}},
    batch:async()=>[{meta:{changes}},{meta:{changes:1}}]
  }});
  assert.equal((await handleProfileMedia(request,environment(5),{id:'owner'},url)).status,200);
  assert.equal((await handleProfileMedia(request,environment(0),{id:'owner'},url)).status,409);
});
