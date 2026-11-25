"use strict";

const {test} = require("node:test");
const assert = require("node:assert/strict");
const {parseChatMessage, messageIdFor, MAX_MESSAGE_LENGTH} = require("../src/chatService");
const {Timestamp}=require("firebase-admin/firestore");
const {nextActivity,unreadFor,readBaseline}=require("../src/chatUnread");

const example = () => ({
  roomId: "12345678901234567890",
  requestId: "183c9c18-46a1-423d-87d0-2cfa436afab9",
  text: "친구들 안녕하세요!"
});

test("parses normalized bounded message with UUID v4 idempotency key", () => {
  const parsed = parseChatMessage({...example(), text: "  첫 줄\r\n둘째 줄  "});
  assert.equal(parsed.text, "첫 줄\n둘째 줄");
  assert.equal(parsed.requestId, example().requestId);
  assert.equal(MAX_MESSAGE_LENGTH, 1000);
});

test("rejects forged metadata, binary bodies, control characters, and oversize", () => {
  for (const input of [
    {...example(), uid: "spoofed"},
    {...example(), createdAt: 0},
    {...example(), senderName: "admin"},
    {...example(), senderUid: "admin"},
    {...example(), text: ""},
    {...example(), text: "  "},
    {...example(), text: "x".repeat(1001)},
    {...example(), text: "\u0000"},
    {...example(), text: ["wrong"]},
    {...example(), requestId: "123"},
    {...example(), roomId: "../outside"},
    null
  ]) {
    assert.throws(() => parseChatMessage(input));
  }
});

test("message id is stable for a retry and unique for different account/room", () => {
  const {roomId, requestId} = example();
  const a = messageIdFor(roomId, "uid-1", requestId);
  assert.match(a, /^[a-f0-9]{64}$/);
  assert.equal(a, messageIdFor(roomId, "uid-1", requestId));
  assert.notEqual(a, messageIdFor(roomId, "uid-2", requestId));
  assert.notEqual(a, messageIdFor("another-room", "uid-1", requestId));
});

test("shared unread sequence removes own sends, prunes departed counters and rejects inconsistent baselines",()=>{
 const now=Date.now(),base={lastChatAt:Timestamp.fromMillis(now),unreadEpoch:2,unreadSequence:10,unreadSentCounts:{me:3,departed:2}};
 const current=nextActivity(base,["me","friend"],"me",now);
 assert.deepEqual(current.counts,{me:4});
 const before={chatReadEpoch:2,chatReadSequence:6,chatReadOwnSent:2,unreadCarry:1};
 assert.equal(unreadFor(current,before,"me"),4);
 assert.equal(unreadFor(current,readBaseline(current,"me"),"me"),0);
 assert.throws(()=>unreadFor(current,{...before,chatReadOwnSent:7},"me"));
});
