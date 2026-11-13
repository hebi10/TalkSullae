"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {sendForbiddenMessage}=require("../src/game/forbiddenChatService");

const valid={roomId:"12345678901234567890",sessionId:"a".repeat(64),
 requestId:"123e4567-e89b-42d3-a456-426614174001",text:"금지어 테스트"};
test("forbidden chat rejects client author, assignments and sender timestamps",async()=>{
 for(const changed of [
  {senderUid:"other"},{forbiddenWord:"치킨"},{role:"admin"},
  {triggered:true},{createdAt:Date.now()},{text:""},
  {text:"x".repeat(501)},{requestId:"bad"},{sessionId:"invalid"}
 ]){
   await assert.rejects(sendForbiddenMessage({},{
     auth:{uid:"owner"},data:{...valid,...changed}
   }),error=>error.code==="invalid-argument");
 }
 await assert.rejects(sendForbiddenMessage({},{
   auth:null,data:valid
 }),error=>error.code==="unauthenticated");
});
