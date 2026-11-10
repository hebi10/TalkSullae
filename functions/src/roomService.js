const {randomBytes, createHash} = require("node:crypto");
const { HttpsError } = require("firebase-functions/v2/https");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");
const {
  ROOM_CAPACITY, PENDING_CAPACITY, INVITE_DURATION_MS,
  roomDraft, roomIdValue, parseJoinCode, authUid, memberName
} = require("./policy");

const now = () => FieldValue.serverTimestamp();
const hashToken = value => createHash("sha256").update(value).digest("hex");

function requireActive(room, member) {
  if (!room?.exists || room.data()?.status !== "active" ||
      !member?.exists || member.data()?.status !== "active") {
    throw new HttpsError("permission-denied", "이 방에 접근할 권한이 없습니다.");
  }
}

function requireOwner(room, member, uid) {
  requireActive(room, member);
  if (room.data().ownerId !== uid) {
    throw new HttpsError("permission-denied", "방장만 관리할 수 있습니다.");
  }
}

function userIndex(db, uid, roomId) {
  return db.collection("userRooms").doc(uid).collection("rooms").doc(roomId);
}

async function createRoom(db, request) {
  const uid = authUid(request);
  const draft = roomDraft(request.data);
  const roomRef = db.collection("rooms").doc();
  const memberRef = roomRef.collection("members").doc(uid);
  const indexRef = userIndex(db,uid,roomRef.id);
  const batch = db.batch();
  batch.create(roomRef,{
    ...draft,ownerId:uid,status:"active",
    memberCount:1,pendingCount:0,activeInviteHash:null,createdAt:now()
  });
  batch.create(memberRef,{displayName:memberName(request.auth),status:"active",joinedAt:now()});
  batch.create(indexRef,{roomName:draft.name,status:"active",updatedAt:now()});
  await batch.commit();
  return {roomId:roomRef.id};
}

async function createInvitation(db, request) {
  const uid = authUid(request);
  const roomId = roomIdValue(request.data?.roomId);
  const roomRef = db.collection("rooms").doc(roomId);
  const actorRef = roomRef.collection("members").doc(uid);
  const token = randomBytes(32).toString("base64url");
  const hash = hashToken(token);
  const expiresAt = Timestamp.fromMillis(Date.now()+INVITE_DURATION_MS);
  await db.runTransaction(async tx=>{
    const [room,actor] = await Promise.all([tx.get(roomRef),tx.get(actorRef)]);
    requireOwner(room,actor,uid);
    tx.create(roomRef.collection("invites").doc(hash),{createdBy:uid,expiresAt,createdAt:now()});
    tx.update(roomRef,{activeInviteHash:hash});
  });
  return {code:`${roomId}.${token}`,expiresAt:expiresAt.toMillis()};
}

async function requestJoin(db, request) {
  const uid = authUid(request);
  const {roomId,token} = parseJoinCode(request.data?.code);
  const roomRef = db.collection("rooms").doc(roomId);
  const inviteRef = roomRef.collection("invites").doc(hashToken(token));
  const memberRef = roomRef.collection("members").doc(uid);
  const indexRef = userIndex(db,uid,roomId);
  return db.runTransaction(async tx=>{
    const [room,invite,member] = await Promise.all([
      tx.get(roomRef),tx.get(inviteRef),tx.get(memberRef)
    ]);
    if (!room.exists || room.data().status !== "active" ||
        room.data().activeInviteHash !== inviteRef.id ||
        !invite.exists || invite.data().expiresAt.toMillis() <= Date.now()) {
      throw new HttpsError("permission-denied", "초대가 만료되었거나 취소되었습니다.");
    }
    const existing = member.exists ? member.data().status : null;
    if (existing === "removed" || existing === "rejected") {
      throw new HttpsError("permission-denied", "방장에게 다시 초대를 요청해 주세요.");
    }
    if (existing === "active" || existing === "pending") return {roomId,status:existing};
    const pending = room.data().requiresApproval;
    if (pending && room.data().pendingCount >= PENDING_CAPACITY) {
      throw new HttpsError("resource-exhausted", "대기 인원이 많아 잠시 후 다시 시도해 주세요.");
    }
    if (!pending && room.data().memberCount >= ROOM_CAPACITY) {
      throw new HttpsError("resource-exhausted", "방 정원이 가득 찼습니다.");
    }
    const status = pending ? "pending" : "active";
    tx.set(memberRef,{status,displayName:memberName(request.auth),joinedAt:now()});
    tx.set(indexRef,{status,roomName:room.data().name,updatedAt:now()});
    tx.update(roomRef,pending
      ? {pendingCount:FieldValue.increment(1)}
      : {memberCount:FieldValue.increment(1)});
    return {roomId,status};
  });
}

async function changeMember(db, request) {
  const uid = authUid(request);
  const roomId = roomIdValue(request.data?.roomId);
  const targetUid = request.data?.targetUid;
  const action = request.data?.action;
  if (typeof targetUid !== "string" || !targetUid || targetUid.includes("/")) {
    throw new HttpsError("invalid-argument", "대상 멤버가 잘못되었습니다.");
  }
  if (!["approve","reject","remove","transfer"].includes(action)) {
    throw new HttpsError("invalid-argument", "지원하지 않는 관리 작업입니다.");
  }
  if (targetUid === uid) throw new HttpsError("failed-precondition", "자기 자신에게 실행할 수 없습니다.");

  const roomRef = db.collection("rooms").doc(roomId);
  const actorRef = roomRef.collection("members").doc(uid);
  const memberRef = roomRef.collection("members").doc(targetUid);
  const indexRef = userIndex(db,targetUid,roomId);
  return db.runTransaction(async tx=>{
    const [room,actor,target] = await Promise.all([
      tx.get(roomRef),tx.get(actorRef),tx.get(memberRef)
    ]);
    requireOwner(room,actor,uid);
    if (!target.exists) throw new HttpsError("not-found", "대상 멤버가 없습니다.");
    const current = target.data().status;
    if (action==="approve" || action==="reject") {
      if (current!=="pending") throw new HttpsError("failed-precondition", "승인 대기 상태가 아닙니다.");
      if (action==="approve" && room.data().memberCount>=ROOM_CAPACITY) {
        throw new HttpsError("resource-exhausted", "방 정원이 가득 찼습니다.");
      }
      const status = action==="approve"?"active":"rejected";
      tx.update(memberRef,{status,updatedAt:now()});
      tx.set(indexRef,{status,roomName:room.data().name,updatedAt:now()});
      tx.update(roomRef,action==="approve"
        ? {pendingCount:FieldValue.increment(-1),memberCount:FieldValue.increment(1)}
        : {pendingCount:FieldValue.increment(-1)});
      return {status};
    }
    if (current!=="active") throw new HttpsError("failed-precondition", "활성 멤버가 아닙니다.");
    if (action==="transfer") {
      tx.update(roomRef,{ownerId:targetUid});
      return {status:"owner-transferred"};
    }
    tx.update(memberRef,{status:"removed",updatedAt:now()});
    tx.set(indexRef,{status:"removed",roomName:room.data().name,updatedAt:now()});
    tx.update(roomRef,{memberCount:FieldValue.increment(-1)});
    return {status:"removed"};
  });
}

async function leaveRoom(db, request) {
  const uid = authUid(request);
  const roomId = roomIdValue(request.data?.roomId);
  const roomRef = db.collection("rooms").doc(roomId);
  const memberRef = roomRef.collection("members").doc(uid);
  const indexRef = userIndex(db,uid,roomId);
  return db.runTransaction(async tx=>{
    const [room,member]=await Promise.all([tx.get(roomRef),tx.get(memberRef)]);
    if (!room.exists || !member.exists ||
        !["active","pending"].includes(member.data().status)) {
      throw new HttpsError("failed-precondition","현재 가입 상태가 아닙니다.");
    }
    const active = member.data().status==="active";
    const isOwner = room.data().ownerId===uid;
    if (isOwner && room.data().memberCount>1) {
      throw new HttpsError("failed-precondition","먼저 다른 멤버에게 방장을 위임해 주세요.");
    }
    tx.update(memberRef,{status:"left",updatedAt:now()});
    tx.set(indexRef,{status:"left",roomName:room.data().name,updatedAt:now()});
    if (isOwner) {
      tx.update(roomRef,{status:"archived",activeInviteHash:null,memberCount:0});
    } else {
      tx.update(roomRef,active
        ? {memberCount:FieldValue.increment(-1)}
        : {pendingCount:FieldValue.increment(-1)});
    }
    return {status:"left"};
  });
}

module.exports = {createRoom,createInvitation,requestJoin,changeMember,leaveRoom};
