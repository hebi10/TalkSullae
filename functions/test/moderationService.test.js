"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {moderatorUid,parseDecision}=require("../src/moderationService");

const base={
  reportId:"a".repeat(64),requestId:"123e4567-e89b-42d3-a456-426614174000",
  status:"resolved",reason:"운영 기준에 따른 검토 완료"
};
test("only privileged Firebase custom claim grants moderator access",()=>{
  assert.equal(moderatorUid({auth:{uid:"mod",token:{talksullaeModerator:true}}}),"mod");
  assert.throws(()=>moderatorUid({auth:{uid:"guest",token:{talksullaeModerator:false}}}),
    e=>e.code==="permission-denied");
  assert.throws(()=>moderatorUid({data:{}}),e=>e.code==="unauthenticated");
});
test("moderation write requires complete non-forgeable request",()=>{
  assert.equal(parseDecision(base).status,"resolved");
  for(const value of [
    {...base,operatorUid:"somebody"},
    {...base,status:"deleted"},
    {...base,reportId:"short"},
    {...base,reason:""},
    {...base,requestId:"not-a-uuid"}
  ])assert.throws(()=>parseDecision(value));
});
