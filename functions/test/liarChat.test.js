"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const {sendLiarMessage}=require("../src/game/liarChatService");

const req=()=>({auth:{uid:"member"},data:{
 roomId:"12345678901234567890",
 sessionId:"a".repeat(64),
 requestId:"123e4567-e89b-42d3-a456-426614174001",
 text:"테스트 대화"
}});
test("liar chat does not trust client role, author UID, timestamp or topic",async()=>{
 for(const extra of [
  {role:"liar"},{senderUid:"owner"},{topic:"secret"},
  {createdAt:Date.now()},{alias:"고양이"},{text:""},
  {text:"가".repeat(501)},{text:"hello\u0000world"},
  {requestId:"fake"},{sessionId:"invalid"}
 ]){
   await assert.rejects(sendLiarMessage({},{
     ...req(),data:{...req().data,...extra}
   }),error=>error.code==="invalid-argument");
 }
 await assert.rejects(sendLiarMessage({},{
   auth:null,data:req().data
 }),error=>error.code==="unauthenticated");
});
