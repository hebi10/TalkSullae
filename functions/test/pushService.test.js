"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {parseRegistration}=require("../src/pushService");

test("push registration accepts a device token with consent only",()=>{
  const token="a".repeat(80)+":Firebase";
  assert.deepEqual(parseRegistration({token,enabled:true}),{token,enabled:true});
  assert.deepEqual(parseRegistration({token,enabled:false}),{token,enabled:false});
});
test("push request rejects arbitrary UIDs, nonboolean consent and malformed tokens",()=>{
  const token="a".repeat(80);
  for(const data of [
    {token,enabled:true,uid:"other"},
    {token,enabled:"yes"},
    {token:"short",enabled:true},
    {token:"a".repeat(80)+"\n",enabled:true},
    {enabled:true}
  ])assert.throws(()=>parseRegistration(data));
});
