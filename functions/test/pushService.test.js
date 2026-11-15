"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {parseRegistration}=require("../src/pushService");
const deviceId="123e4567-e89b-42d3-a456-426614174000";

test("push registration binds a device and a consent revision",()=>{
  const token="a".repeat(80)+":Firebase";
  assert.deepEqual(
    parseRegistration({token,enabled:true,deviceId,revision:1}),
    {token,enabled:true,deviceId,revision:1}
  );
  assert.equal(parseRegistration({token,enabled:false,deviceId,revision:2}).enabled,false);
});
test("rejects forged UID, malformed UUID and invalid consent generations",()=>{
  const token="a".repeat(80);
  for(const data of [
    {token,enabled:true,uid:"other",deviceId,revision:1},
    {token,enabled:"yes",deviceId,revision:1},
    {token:"short",enabled:true,deviceId,revision:1},
    {token,enabled:true,deviceId:"not-a-uuid",revision:1},
    {token,enabled:true,deviceId,revision:0},
    {token,enabled:true,deviceId,revision:1.2},
    {enabled:true,deviceId,revision:1}
  ])assert.throws(()=>parseRegistration(data));
});
