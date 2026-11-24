"use strict";

const {createHash} = require("node:crypto");
const {FieldValue, Timestamp} = require("firebase-admin/firestore");
const {HttpsError} = require("firebase-functions/v2/https");
const {authUid, roomIdValue} = require("./policy");
const {AVATARS} = require("./userProfile");
const {ROOM_CHAT_DAYS,DAY_MS}=require("./retention");

const MAX_MESSAGE_LENGTH = 1000;
const MIN_SEND_INTERVAL_MS = 750;
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ALLOWED_FIELDS = new Set(["roomId", "requestId", "text"]);

function parseChatMessage(input) {
  if (input === null || typeof input !== "object" || Array.isArray(input) ||
      Object.keys(input).some(field => !ALLOWED_FIELDS.has(field))) {
    throw new HttpsError("invalid-argument", "채팅 요청 형식이 올바르지 않습니다.");
  }
  const roomId = roomIdValue(input.roomId);
  if (typeof input.requestId !== "string" || !UUID_V4.test(input.requestId)) {
    throw new HttpsError("invalid-argument", "메시지 요청 ID 형식이 올바르지 않습니다.");
  }
  if (typeof input.text !== "string") {
    throw new HttpsError("invalid-argument", "메시지는 텍스트로 입력해 주세요.");
  }
  const text = input.text.replace(/\r\n?/g, "\n").trim();
  if (text.length === 0 || text.length > MAX_MESSAGE_LENGTH ||
      /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(text)) {
    throw new HttpsError("invalid-argument", "메시지는 1~1000자 이내의 글로 입력해 주세요.");
  }
  return {roomId, requestId: input.requestId.toLowerCase(), text};
}

function messageIdFor(roomId, uid, requestId) {
  return createHash("sha256").update(JSON.stringify([roomId, uid, requestId])).digest("hex");
}

async function sendRoomMessage(db, request) {
  const uid = authUid(request);
  const {roomId, requestId, text} = parseChatMessage(request.data);
  const roomRef = db.collection("rooms").doc(roomId);
  const memberRef = roomRef.collection("members").doc(uid);
  const messageId = messageIdFor(roomId, uid, requestId);
  const messageRef = roomRef.collection("messages").doc(messageId);
  const rateRef = roomRef.collection("chatRateLimits").doc(uid);

  return db.runTransaction(async transaction => {
    const [room, member, existing, rate, deletion] = await Promise.all([
      transaction.get(roomRef),
      transaction.get(memberRef),
      transaction.get(messageRef),
      transaction.get(rateRef),
      transaction.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 채팅할 수 없습니다.");
    if (!room.exists || room.get("status") !== "active" ||
        !member.exists || member.get("status") !== "active") {
      throw new HttpsError("permission-denied", "활성 멤버만 이 방에서 채팅할 수 있습니다.");
    }

    if (existing.exists) {
      if (existing.get("senderUid") === uid && existing.get("deletedAt")) {
        return {messageId, deduplicated: true};
      }
      if (existing.get("senderUid") !== uid || existing.get("text") !== text) {
        throw new HttpsError("already-exists", "동일 요청 ID로 다른 메시지를 전송할 수 없습니다.");
      }
      return {messageId, deduplicated: true};
    }

    const nowMillis = Date.now();
    if (rate.exists) {
      const lastSentAt = rate.get("lastSentAt");
      if (!(lastSentAt instanceof Timestamp)) {
        throw new HttpsError("internal", "메시지 전송 상태를 확인할 수 없습니다.");
      }
      if (nowMillis < lastSentAt.toMillis() + MIN_SEND_INTERVAL_MS) {
        throw new HttpsError("resource-exhausted", "메시지를 너무 빠르게 보내고 있습니다.");
      }
    }

    const displayName = member.get("displayName");
    const senderName = typeof displayName === "string" && displayName.trim()
      ? displayName.trim().slice(0, 30)
      : "멤버";

    transaction.create(messageRef, {
      senderUid: uid,
      senderName,
      senderAvatar: AVATARS.has(member.get("avatar")) ? member.get("avatar") : "부엉이",
      text,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt: Timestamp.fromMillis(nowMillis+ROOM_CHAT_DAYS*DAY_MS)
    });
    transaction.set(rateRef, {lastSentAt: Timestamp.fromMillis(nowMillis)});
    return {messageId, deduplicated: false};
  });
}

async function deleteRoomMessage(db, request) {
  const uid = authUid(request);
  const input = request.data;
  if (!input || typeof input !== "object" || Array.isArray(input) ||
      Object.keys(input).some(key => !["roomId","messageId"].includes(key)) ||
      typeof input.messageId !== "string" || !/^[a-f0-9]{64}$/.test(input.messageId)) {
    throw new HttpsError("invalid-argument","삭제할 메시지를 확인해 주세요.");
  }
  const roomId = roomIdValue(input.roomId);
  const roomRef = db.collection("rooms").doc(roomId);
  const messageRef = roomRef.collection("messages").doc(input.messageId);
  const memberRef = roomRef.collection("members").doc(uid);
  const deletionRef = db.collection("accountDeletionJobs").doc(uid);
  return db.runTransaction(async tx => {
    const [room, member, message, deletion] = await Promise.all([
      tx.get(roomRef), tx.get(memberRef), tx.get(messageRef), tx.get(deletionRef)
    ]);
    if (deletion.exists || !room.exists || room.get("status") !== "active" ||
        !member.exists || member.get("status") !== "active") {
      throw new HttpsError("permission-denied","이 방의 활성 멤버만 메시지를 삭제할 수 있습니다.");
    }
    if (!message.exists || message.get("senderUid") !== uid) {
      throw new HttpsError("permission-denied","본인이 작성한 메시지만 삭제할 수 있습니다.");
    }
    if (message.get("deletedAt")) return {deleted:true,deduplicated:true};
    tx.update(messageRef,{text:"삭제된 메시지입니다.",deletedAt:FieldValue.serverTimestamp()});
    tx.update(roomRef,{chatDeletionRevision:FieldValue.increment(1)});
    return {deleted:true,deduplicated:false};
  });
}

module.exports = {
  deleteRoomMessage,
  MAX_MESSAGE_LENGTH,
  MIN_SEND_INTERVAL_MS,
  parseChatMessage,
  messageIdFor,
  sendRoomMessage
};
