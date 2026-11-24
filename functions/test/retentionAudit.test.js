"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {Timestamp}=require("firebase-admin/firestore");
const {DAY_MS}=require("../src/retention");
const {classify,scanRetention}=require("../src/retentionAudit");

test("legacy retention distinguishes ordinary chat and game chat without deriving expiry for malformed records",()=>{
  const now=1000*DAY_MS;
  const room="rooms/r/messages/m",game="rooms/r/gameSessions/s/publicMessages/m";
  const ts=days=>Timestamp.fromMillis(now-days*DAY_MS);
  assert.equal(classify(room,ts(90),undefined,now),"legacyDue");
  assert.equal(classify(room,ts(89),undefined,now),"legacyFuture");
  assert.equal(classify(game,ts(7),null,now),"legacyDue");
  assert.equal(classify(game,ts(6),undefined,now),"legacyFuture");
  assert.equal(classify(room,ts(1),ts(0),now),"indexedDue");
  assert.equal(classify(room,ts(1),"bad",now),"invalidExpiry");
  assert.equal(classify(room,undefined,undefined,now),"missingExpiryInvalidCreatedAt");
  assert.equal(classify(room,ts(-1),undefined,now),"missingExpiryInvalidCreatedAt");
  assert.equal(classify("unknown/r/messages/m",ts(91),undefined,now),"unrecognizedPath");
});

test("audit projects only timestamps and indicates capped results",async()=>{
  const cutoff=1000*DAY_MS;
  const reads=[];
  const db={collectionGroup(name){
    const query={select(...fields){assert.deepEqual(fields,["createdAt","expiresAt"]);return this;},
      orderBy(){return this;},limit(size){assert.equal(size,1);return this;},
      async get(){
        reads.push(name);
        if(name==="publicMessages")return {size:0,docs:[]};
        return {size:1,docs:[{ref:{path:"rooms/r/messages/m"},get:f=>f==="createdAt"?Timestamp.fromMillis(cutoff-91*DAY_MS):undefined}]};
      }};
    return query;
  }};
  const report=await scanRetention(db,{maxDocuments:1,pageSize:1,now:()=>cutoff});
  assert.deepEqual(reads,["messages","publicMessages"]);
  assert.equal(report.groups.messages.counts.legacyDue,1);
  assert.equal(report.groups.messages.complete,false);
  assert.equal(report.groups.publicMessages.complete,true);
  assert.ok(!JSON.stringify(report).includes("rooms/r/"));
});
