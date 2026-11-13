"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const controls=require("../src/safetyControls");
const {expiry,DAY_MS,ROOM_CHAT_DAYS,GAME_CHAT_DAYS}=require("../src/retention");

const uid="user";
const auth={auth:{uid,token:{auth_time:Math.floor(Date.now()/1000)}}};
const validReport={
  roomId:"12345678901234567890",messageId:"a".repeat(64),
  requestId:"123e4567-e89b-42d3-a456-426614174001",
  category:"abuse",details:"악의적인 메시지입니다."
};
test("block input denies self-block, spoofed UID and unsupported fields",async()=>{
 for(const input of [
   {targetUid:"user",blocked:true},{targetUid:"other",blocked:"yes"},
   {targetUid:"other",blocked:true,ownerUid:"forged"},
   {targetUid:"bad/uid",blocked:true},
   {blocked:true},{targetUid:"",blocked:false}
 ]){
   await assert.rejects(controls.setUserBlock({}, {...auth,data:input}),
     error=>error.code==="invalid-argument");
 }
 await assert.rejects(controls.setUserBlock({},{
   auth:null,data:{targetUid:"other",blocked:true}
 }),error=>error.code==="unauthenticated");
});
test("message reports require verified evidence id, room, request uuid and bounded text",async()=>{
 for(const change of [
  {roomId:"invalid"},{messageId:"abc"},{category:"unsupported"},
  {details:"no"},{details:"x".repeat(1001)},
  {requestId:"bad"},{senderUid:"spoof"},
  {createdAt:Date.now()},{actorUid:"other"}
 ]){
   await assert.rejects(controls.reportChatMessage({}, {
     ...auth,data:{...validReport,...change}
   }),error=>error.code==="invalid-argument");
 }
});
test("account deletion refuses unauthenticated, stale sessions, and missing confirmation",async()=>{
 for(const data of [{confirm:"YES"},{confirm:"DELETE",uid:"other"},{}]){
   await assert.rejects(controls.requestAccountDeletion({}, {...auth,data}),
     error=>error.code==="invalid-argument");
 }
 await assert.rejects(controls.requestAccountDeletion({},{
   auth:{uid,token:{auth_time:1}},data:{confirm:"DELETE",expectedUid:uid}
 }),error=>error.code==="failed-precondition");
 await assert.rejects(controls.requestAccountDeletion({},{
   auth:{uid,token:{}},data:{confirm:"DELETE",expectedUid:uid}
 }),error=>error.code==="failed-precondition");
});
test("retention windows are deterministic and server owned",()=>{
 const now=1762935600000;
 assert.equal(expiry(now,ROOM_CHAT_DAYS).toMillis(),now+90*DAY_MS);
 assert.equal(expiry(now,GAME_CHAT_DAYS).toMillis(),now+7*DAY_MS);
});
