import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validCatalogueChunk} from '../src/catalogue-sync.mjs';

test('catalogue snapshot chunks are bounded, immutable and addressed by their index',()=>{
  const body={category:'catalogues',itemKey:'snapshot:collections:'+'a'.repeat(64)+':000000',
    revision:0,deleted:false,payload:{index:0,text:'x'.repeat(6000)}};
  assert.equal(validCatalogueChunk(body),true);
  assert.equal(validCatalogueChunk({...body,revision:1}),false);
  assert.equal(validCatalogueChunk({...body,deleted:true}),false);
  assert.equal(validCatalogueChunk({...body,payload:{index:1,text:'x'}}),false);
  assert.equal(validCatalogueChunk({...body,payload:{index:0,text:'x'.repeat(6001)}}),false);
  assert.equal(validCatalogueChunk({...body,itemKey:body.itemKey.replace('000000','000700')}),false);
});
