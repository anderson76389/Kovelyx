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
