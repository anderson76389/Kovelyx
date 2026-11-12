import assert from "node:assert/strict";
import {test} from "node:test";
import {DatabaseSync} from "node:sqlite";
import {createLocalD1} from "../portable/sqlite-d1.mjs";

test("portable D1 adapter preserves bind, rows, changes and binary payloads",async()=>{
  const db=new DatabaseSync(":memory:");
  try {
    db.exec("CREATE TABLE items(id TEXT PRIMARY KEY, data BLOB NOT NULL)");
    const d1=createLocalD1(db);
    const bytes=Uint8Array.from([0,1,2,254,255]);
    const saved=await d1.prepare("INSERT INTO items(id,data) VALUES (?,?)")
      .bind("first",bytes.buffer).run();
    assert.equal(saved.meta.changes,1);
    const row=await d1.prepare("SELECT id,data FROM items WHERE id=?").bind("first").first();
    assert.equal(row.id,"first");
    assert.deepEqual(Buffer.from(row.data),Buffer.from(bytes));
    const listed=await d1.prepare("SELECT id FROM items WHERE id=?").bind("first").all();
    assert.equal(listed.results.length,1);
    assert.equal(await d1.prepare("SELECT id FROM items WHERE id=?").bind("missing").first(),null);
  } finally {db.close();}
});

test("portable D1 batch rolls back all statements on failure",async()=>{
  const db=new DatabaseSync(":memory:");
  try {
    db.exec("CREATE TABLE data(id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
    const d1=createLocalD1(db);
    await assert.rejects(
      d1.batch([
        d1.prepare("INSERT INTO data(id,value) VALUES (?,?)").bind(1,"good"),
        d1.prepare("INSERT INTO data(id,value) VALUES (?,?)").bind(1,"duplicate")
      ])
    );
    assert.equal(db.prepare("SELECT count(*) AS n FROM data").get().n,0);
    const result=await d1.batch([
      d1.prepare("INSERT INTO data(id,value) VALUES (?,?)").bind(2,"yes"),
      d1.prepare("UPDATE data SET value=? WHERE id=?").bind("updated",2)
    ]);
    assert.deepEqual(result.map(x=>x.meta.changes),[1,1]);
    assert.equal(db.prepare("SELECT value FROM data WHERE id=2").get().value,"updated");
  } finally {db.close();}
});
