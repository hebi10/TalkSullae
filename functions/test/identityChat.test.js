"use strict";

const {test}=require("node:test");
const assert=require("node:assert/strict");
const {parseIdentityChat,MAX_LENGTH,MIN_INTERVAL}=require("../src/game/identityChatService");

const sample=()=>({
  roomId:"12345678901234567890",
  sessionId:"a".repeat(64),
  requestId:"123e4567-e89b-42d3-a456-426614174001",
  text:"  익명 인사!  "
});

test("server anonymous chat normalizes text and accepts a stable UUID v4",()=>{
  const parsed=parseIdentityChat(sample());
  assert.equal(parsed.text,"익명 인사!");
  assert.equal(MAX_LENGTH,500);
  assert.equal(MIN_INTERVAL,750);
  assert.equal(parseIdentityChat({...sample(),text:"가".repeat(500)}).text.length,500);
});
test("rejects public author identity, client timestamps, excess text and control chars",()=>{
  for(const changed of [
    {senderUid:"spoof"},{alias:"고양이"},{createdAt:Date.now()},
    {role:"liar"},{text:""},{text:"가".repeat(501)},
    {text:"hi\u0000bad"},{text:123},{sessionId:"wrong"},
    {requestId:"not-uuid"}
  ]){
    assert.throws(()=>parseIdentityChat({...sample(),...changed}));
  }
  assert.throws(()=>parseIdentityChat(null));
});
