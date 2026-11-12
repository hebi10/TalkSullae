"use strict";

const {before, after, test} = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {sendRoomMessage} = require("../src/chatService");
const {createRoom, requestJoin, createInvitation, changeMember} = require("../src/roomService");

let app;
let db;
const auth = (uid, name = uid) => ({auth: {uid, token: {name}}});
const uuid = value => "123e4567-e89b-42d3-a456-" + String(value).padStart(12, "0");

before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Firestore emulator is required");
  app = initializeApp({projectId: "demo-talksullae"}, "game01-chat-tests");
  db = getFirestore(app);
});
after(async () => { if (app) await deleteApp(app); });

async function roomWithOwner() {
  const {roomId} = await createRoom(db, {
    ...auth("owner", "방장"), data: {name: "채팅 테스트방", description: "", requiresApproval: true}
  });
  return roomId;
}
const message = (roomId, index, text = "첫 메시지") => ({
  ...auth("owner"), data: {roomId, requestId: uuid(index), text}
});

test("authenticated room owner sends server-timestamped message exactly once", async () => {
  const roomId = await roomWithOwner();
  const first = await sendRoomMessage(db, message(roomId, 1));
  assert.equal(first.deduplicated, false);
  const second = await sendRoomMessage(db, message(roomId, 1));
  assert.equal(second.messageId, first.messageId);
  assert.equal(second.deduplicated, true);
  const list = await db.collection("rooms").doc(roomId).collection("messages").get();
  assert.equal(list.size, 1);
  const written = list.docs[0].data();
  assert.equal(written.senderUid, "owner");
  assert.equal(written.senderName, "방장");
  assert.equal(written.text, "첫 메시지");
  assert.ok(written.createdAt && written.createdAt.toMillis() > 0);
  assert.equal(written.role, undefined);
  assert.equal(written.clientTimestamp, undefined);
});

test("retry with same request ID but different text is rejected", async () => {
  const roomId = await roomWithOwner();
  await sendRoomMessage(db, message(roomId, 2));
  await assert.rejects(sendRoomMessage(db, message(roomId, 2, "변조 메시지")));
  const list = await db.collection("rooms").doc(roomId).collection("messages").get();
  assert.equal(list.size, 1);
});

test("non-member and pending member cannot send messages", async () => {
  const roomId = await roomWithOwner();
  await assert.rejects(sendRoomMessage(db, {...auth("stranger"), data: {
    roomId, requestId: uuid(3), text: "외부인"
  }}));
  const invite = await createInvitation(db, {...auth("owner"), data: {roomId}});
  const joined = await requestJoin(db, {...auth("waiting"), data: {code: invite.code}});
  assert.equal(joined.status, "pending");
  await assert.rejects(sendRoomMessage(db, {...auth("waiting"), data: {
    roomId, requestId: uuid(4), text: "대기자"
  }}));
});

test("revoked member loses permission and owner cannot forge another sender", async () => {
  const roomId = await roomWithOwner();
  const invite = await createInvitation(db, {...auth("owner"), data: {roomId}});
  await requestJoin(db, {...auth("member"), data: {code: invite.code}});
  await changeMember(db, {...auth("owner"), data: {roomId, targetUid: "member", action: "approve"}});
  await sendRoomMessage(db, {...auth("member"), data: {
    roomId, requestId: uuid(5), text: "가입 후 채팅"
  }});
  await changeMember(db, {...auth("owner"), data: {roomId, targetUid: "member", action: "remove"}});
  await assert.rejects(sendRoomMessage(db, {...auth("member"), data: {
    roomId, requestId: uuid(6), text: "강퇴 후 채팅"
  }}));
  await assert.rejects(sendRoomMessage(db, {...auth("member"), data: {
    roomId, requestId: uuid(5), text: "가입 후 채팅"
  }}));
});

test("rate guard refuses bursts with new request IDs", async () => {
  const roomId = await roomWithOwner();
  await sendRoomMessage(db, message(roomId, 7));
  await db.collection("rooms").doc(roomId).collection("chatRateLimits").doc("owner")
    .set({lastSentAt: Timestamp.fromMillis(Date.now() + 5000)});
  await assert.rejects(sendRoomMessage(db, message(roomId, 8, "연속 메시지")), error =>
    error?.code === "resource-exhausted");
  const list = await db.collection("rooms").doc(roomId).collection("messages").get();
  assert.equal(list.size, 1);
});

test("concurrent identical retries create one document", async () => {
  const roomId = await roomWithOwner();
  const request = message(roomId, 9, "동시 요청");
  const responses = await Promise.all([
    sendRoomMessage(db, request), sendRoomMessage(db, request)
  ]);
  assert.equal(responses[0].messageId, responses[1].messageId);
  const list = await db.collection("rooms").doc(roomId).collection("messages").get();
  assert.equal(list.size, 1);
});
