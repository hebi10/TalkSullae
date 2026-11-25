"use strict";

const {before, after, test} = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {sendRoomMessage,deleteRoomMessage,markRoomChatRead} = require("../src/chatService");
const {createRoom, requestJoin, createInvitation, changeMember, leaveRoom} = require("../src/roomService");
const {activity,unreadFor}=require("../src/chatUnread");

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

test("only the author can delete a message and retries never resurrect its text", async () => {
  const roomId = await roomWithOwner();
  const invite = await createInvitation(db,{...auth("owner"),data:{roomId}});
  await requestJoin(db,{...auth("delete-guest"),data:{code:invite.code}});
  await changeMember(db,{...auth("owner"),data:{roomId,targetUid:"delete-guest",action:"approve"}});
  const req = message(roomId,10,"삭제 전 원문");
  const {messageId} = await sendRoomMessage(db,req);
  const data={roomId,messageId};
  await assert.rejects(deleteRoomMessage(db,{...auth("delete-guest"),data}),e=>e.code==="permission-denied");
  const first=await deleteRoomMessage(db,{...auth("owner"),data});
  assert.equal(first.deduplicated,false);
  const second=await deleteRoomMessage(db,{...auth("owner"),data});
  assert.equal(second.deduplicated,true);
  await sendRoomMessage(db,req);
  const roomRef=db.collection("rooms").doc(roomId);
  const messageRef=roomRef.collection("messages").doc(messageId);
  assert.equal((await messageRef.get()).get("text"),"삭제된 메시지입니다.");
  assert.ok((await messageRef.get()).get("deletedAt"));
  assert.equal((await roomRef.get()).get("chatDeletionRevision"),1);
});

test("unread count excludes own sends, deduplicates retries, and rejects stale or unauthorized read receipts",async()=>{
  const roomId=await roomWithOwner();
  const invite=await createInvitation(db,{...auth("owner"),data:{roomId}});
  await requestJoin(db,{...auth("unread-friend"),data:{code:invite.code}});
  await changeMember(db,{...auth("owner"),data:{roomId,targetUid:"unread-friend",action:"approve"}});
  await requestJoin(db,{...auth("unread-pending"),data:{code:invite.code}});
  const index=uid=>db.collection("userRooms").doc(uid).collection("rooms").doc(roomId);
  const request=message(roomId,21,"새 메시지");
  const first=await sendRoomMessage(db,request);
  await sendRoomMessage(db,request);
  const roomRef=db.collection("rooms").doc(roomId);
  const unread=async uid=>unreadFor(activity((await roomRef.get()).data()),(await index(uid).get()).data(),uid);
  assert.equal(await unread("owner"),0);
  const friendIndex=await index("unread-friend").get();
  assert.equal(await unread("unread-friend"),1);
  assert.equal(friendIndex.get("unreadSchema"),1);
  assert.equal((await roomRef.get()).get("lastChatMessageId"),first.messageId);
  assert.equal(friendIndex.get("text"),undefined);
  assert.equal((await index("unread-pending").get()).get("unreadCount"),undefined);
  const read={roomId,messageId:first.messageId};
  await assert.rejects(markRoomChatRead(db,{...auth("outsider"),data:read}),e=>e.code==="permission-denied");
  await assert.rejects(markRoomChatRead(db,{...auth("unread-pending"),data:read}),e=>e.code==="permission-denied");
  const second=await sendRoomMessage(db,{...auth("unread-friend"),data:{roomId,requestId:uuid(22),text:"답장"}});
  assert.equal(await unread("owner"),1);
  assert.equal(await unread("unread-friend"),1);
  assert.equal((await index("unread-friend").get()).updateTime.toMillis(),friendIndex.updateTime.toMillis());
  assert.deepEqual(await markRoomChatRead(db,{...auth("unread-friend"),data:read}),{marked:false,changed:false});
  assert.equal(await unread("unread-friend"),1);
  const latest={roomId,messageId:second.messageId};
  assert.deepEqual(await markRoomChatRead(db,{...auth("unread-friend"),data:latest}),{marked:true,changed:true});
  assert.deepEqual(await markRoomChatRead(db,{...auth("unread-friend"),data:latest}),{marked:true,changed:false});
  assert.equal(await unread("owner"),1);
  assert.equal(await unread("unread-friend"),0);
  await changeMember(db,{...auth("owner"),data:{roomId,targetUid:"unread-friend",action:"remove"}});
  await assert.rejects(markRoomChatRead(db,{...auth("unread-friend"),data:latest}),e=>e.code==="permission-denied");
});

test("sequence migration preserves legacy unread, new members start at join, and inactivity resets without fanout",async()=>{
 const roomId=await roomWithOwner(),ref=db.collection("rooms").doc(roomId);
 const invite=await createInvitation(db,{...auth("owner"),data:{roomId}});
 const join=async uid=>{
   await requestJoin(db,{...auth(uid),data:{code:invite.code}});
   await changeMember(db,{...auth("owner"),data:{roomId,targetUid:uid,action:"approve"}});
 };
 await join("r3-unread-legacy");
 const idx=uid=>db.collection("userRooms").doc(uid).collection("rooms").doc(roomId);
 const last=Timestamp.fromMillis(Date.now()-1000);
 await ref.update({lastChatAt:last,lastChatMessageId:"a".repeat(64)});
 await idx("r3-unread-legacy").update({unreadCount:7,lastChatAt:last,lastChatMessageId:"a".repeat(64)});
 const first=await sendRoomMessage(db,message(roomId,41,"이관 후 메시지"));
 const value=async uid=>unreadFor(activity((await ref.get()).data()),(await idx(uid).get()).data(),uid);
 assert.equal(await value("r3-unread-legacy"),8);
 await join("r3-unread-new");
 assert.equal(await value("r3-unread-new"),0);
 await leaveRoom(db,{...auth("r3-unread-legacy"),data:{roomId}});
 await join("r3-unread-legacy");
 assert.equal(await value("r3-unread-legacy"),0);
 await ref.update({lastChatAt:Timestamp.fromMillis(Date.now()-91*24*60*60*1000)});
 await ref.collection("chatRateLimits").doc("owner").delete();
 const before=(await idx("r3-unread-new").get()).data();
 const next=await sendRoomMessage(db,message(roomId,42,"장기 공백 후"));
 assert.notEqual(first.messageId,next.messageId);
 assert.equal((await ref.get()).get("unreadEpoch"),2);
 assert.deepEqual((await idx("r3-unread-new").get()).data(),before);
 assert.equal(await value("r3-unread-new"),1);
 assert.equal(await value("r3-unread-legacy"),1);
 assert.equal(await value("owner"),0);
});
