"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {LIMITS,consumeRoomQuota,guardRoomOperation}=require("../src/apiRateLimit");

function fakeDb(){
  const rows=new Map();
  return {
    rows,
    collection(name){
      assert.equal(name,"roomOperationLimits");
      return {doc(key){assert.match(key,/^[a-f0-9]{64}$/);return key;}};
    },
    async runTransaction(fn){
      return fn({
        async get(key){return {exists:rows.has(key),get:field=>rows.get(key)?.[field]};},
        set(key,value){rows.set(key,value);}
      });
    }
  };
}

test("fixed-window room quota denies excess and resets next window",async()=>{
  const db=fakeDb();
  const rule=LIMITS.createRoom;
  const now=()=>rule.windowMs*2+1000;
  for(let i=0;i<rule.max;i++)await consumeRoomQuota(db,"first","createRoom",now);
  await assert.rejects(consumeRoomQuota(db,"first","createRoom",now),e=>e.code==="resource-exhausted");
  await consumeRoomQuota(db,"second","createRoom",now);
  await consumeRoomQuota(db,"first","previewInvitation",now);
  await consumeRoomQuota(db,"first","createRoom",()=>rule.windowMs*3);
  assert.equal(db.rows.size,3);
  for(const record of db.rows.values())assert.ok(record.expiresAt.toMillis()>now());
});

test("unauthenticated callable never consumes quota or runs handler",async()=>{
  const db=fakeDb();
  await assert.rejects(guardRoomOperation(db,{},"createRoom",()=>assert.fail("handler called")),e=>e.code==="unauthenticated");
  assert.equal(db.rows.size,0);
});
