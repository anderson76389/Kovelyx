import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync,readdirSync} from 'node:fs';
import {handleCatalogueChunk,handleCatalogueManifest} from '../src/catalogue-sync.mjs';

function database() {
  const db=new DatabaseSync(':memory:');
  db.exec('PRAGMA foreign_keys=ON');
  const directory=new URL('../migrations/',import.meta.url);
  for(const file of readdirSync(directory).filter(x=>x.endsWith('.sql')).sort()) db.exec(readFileSync(new URL(file,directory),'utf8'));
  db.prepare('INSERT INTO user (id,name,email,emailVerified,createdAt,updatedAt) VALUES (?,?,?,?,?,?)').run('alice','Alice','alice@example.test',1,1,1);
  const profile='11111111-1111-4111-8111-111111111111';
  db.prepare('INSERT INTO kv_profiles (id,owner_id,display_name,created_at,updated_at) VALUES (?,?,?,?,?)').run(profile,'alice','Principal',1,1);
  return {db,profile,env:{DB:{prepare(sql){let args=[];return {
    bind(...values){args=values;return this},
    async first(){return db.prepare(sql).get(...args)||null},
    async run(){return {meta:{changes:Number(db.prepare(sql).run(...args).changes)}}}
  }}}}};
}
const request=new Request('https://worker.test/v1/catalogue-manifest',{method:'POST'});
const user={id:'alice'};
const chunk=(profile,hash,index,text)=>({profileId:profile,category:'catalogues',itemKey:`snapshot:collections:${hash}:${String(index).padStart(6,'0')}`,revision:0,deleted:false,payload:{index,text}});

test('real SQLite statements publish only complete snapshots, use CAS and remove obsolete chunks',async()=>{
  const {db,env,profile}=database();
  try {
    const a='a'.repeat(64),b='b'.repeat(64);
    const head={profileId:profile,kind:'collections',sha256:a,parts:2,utf8Bytes:4,revision:0};
    assert.equal((await handleCatalogueManifest(request,env,user,head)).status,409);
    assert.equal((await handleCatalogueChunk(request,env,user,chunk(profile,a,0,'['))).status,201);
    assert.equal((await handleCatalogueManifest(request,env,user,head)).status,409);
    assert.equal((await handleCatalogueChunk(request,env,user,chunk(profile,a,1,']'))).status,201);
    assert.equal((await handleCatalogueManifest(request,env,user,head)).status,201);
    assert.equal((await handleCatalogueChunk(request,env,user,chunk(profile,a,1,']'))).status,200);
    assert.equal((await handleCatalogueChunk(request,env,user,chunk(profile,a,1,'different'))).status,409);
    assert.equal((await handleCatalogueChunk(request,env,{id:'bob'},chunk(profile,b,0,'[]'))).status,404);
    assert.equal((await handleCatalogueChunk(request,env,user,chunk(profile,b,0,'[]'))).status,201);
    const next={...head,sha256:b,parts:1,utf8Bytes:2,revision:1};
    assert.equal((await handleCatalogueManifest(request,env,user,next)).status,200);
    assert.equal(db.prepare("SELECT count(*) n FROM kv_sync WHERE category='catalogues'").get().n,1);
    assert.equal((await handleCatalogueManifest(request,env,user,head)).status,409);
    assert.equal((await handleCatalogueManifest(request,env,user,next)).status,409);
    const payload=JSON.parse(db.prepare("SELECT payload FROM kv_sync WHERE category='preferences'").get().payload);
    assert.equal(payload.sha256,b);
  } finally {db.close()}
});

test('normal synchronization uses category index lookups, independently of large catalogue payloads',()=>{
  const {db,profile}=database();
  try {
    const source=readFileSync(new URL('../src/index.mjs',import.meta.url),'utf8');
    const clause=source.match(/ AND category IN \('[^\n]+/)[0].split('"')[0];
    const sql='SELECT category,item_key,payload FROM kv_sync WHERE owner_id=? AND profile_id=?'+clause+' ORDER BY category,item_key LIMIT 26 OFFSET 0';
    const plan=db.prepare('EXPLAIN QUERY PLAN '+sql).all('alice',profile).map(x=>x.detail).join('\n');
    assert.match(plan,/owner_id=\? AND profile_id=\? AND category=\?/);
    const insert=db.prepare('INSERT INTO kv_sync (owner_id,profile_id,category,item_key,payload,revision,deleted,updated_at) VALUES (?,?,?,?,?,1,0,1)');
    for(let i=0;i<700;i++)insert.run('alice',profile,'catalogues','piece'+i,JSON.stringify({text:'x'.repeat(6000)}));
    insert.run('alice',profile,'preferences','catalogue_collections','{}');
    assert.equal(db.prepare(sql).all('alice',profile).length,1);
  } finally {db.close()}
});
