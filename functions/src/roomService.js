const {randomBytes, createHash} = require("node:crypto");
const { HttpsError } = require("firebase-functions/v2/https");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");
const {
  ROOM_CAPACITY, PENDING_CAPACITY, INVITE_DURATION_MS,
  roomDraft, roomIdValue, parseJoinCode, authUid
} = require("./policy");
const {memberDisplayName,profileAvatar} = require("./userProfile");

const ROOM_ICONS=new Set(["부엉이","토끼","고양이","강아지","곰","여우","판다","펭귄"]);
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

function directoryFields(room,memberCount=room.memberCount) {
  return {roomIcon:ROOM_ICONS.has(room.roomIcon)?room.roomIcon:null,
    memberCount:Number.isInteger(memberCount)&&memberCount>=0&&memberCount<=ROOM_CAPACITY?memberCount:null,
    gameInProgress:typeof room.activeGameSessionId==="string"&&room.activeGameSessionId.length>0};
}

function roomDirectoryChanged(before,after){
  return ["roomIcon","memberCount","activeGameSessionId","status"].some(key=>before?.[key]!==after?.[key]);
}

async function syncRoomDirectory(db,roomId){
  const ref=db.collection("rooms").doc(roomId);
  return db.runTransaction(async tx=>{
    const room=await tx.get(ref);
    if(!room.exists||room.get("status")!=="active")return 0;
    const members=await tx.get(ref.collection("members").where("status","==","active").limit(ROOM_CAPACITY+1));
    if(members.size>ROOM_CAPACITY||members.size!==room.get("memberCount"))return 0;
    const indices=await Promise.all(members.docs.map(member=>tx.get(userIndex(db,member.id,roomId))));
    const fields=directoryFields(room.data());
    let changed=0;
    for(const index of indices){
      if(!index.exists||index.get("status")!=="active")continue;
      if(Object.entries(fields).some(([key,value])=>index.get(key)!==value)){
        tx.update(index.ref,fields);
        changed++;
      }
    }
    return changed;
  });
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
  await db.runTransaction(async tx=>{
    const [deletion,profile]=await Promise.all([
      tx.get(db.collection("accountDeletionJobs").doc(uid)),
      tx.get(db.collection("users").doc(uid))
    ]);
    if(deletion.exists){
      throw new HttpsError("permission-denied","계정 삭제 요청 중에는 방을 만들 수 없습니다.");
    }
    tx.create(roomRef,{
      ...draft,ownerId:uid,status:"active",
      memberCount:1,pendingCount:0,activeInviteHash:null,createdAt:now()
    });
    tx.create(memberRef,{displayName:memberDisplayName(profile,request.auth),
      avatar:profileAvatar(profile),status:"active",joinedAt:now()});
    tx.create(indexRef,{roomName:draft.name,status:"active",updatedAt:now(),...directoryFields({},1)});
  });
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
    const [room,actor,deletion] = await Promise.all([tx.get(roomRef),tx.get(actorRef),
      tx.get(db.collection("accountDeletionJobs").doc(uid))]);
    requireOwner(room,actor,uid);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 초대를 발급할 수 없습니다.");
    tx.create(roomRef.collection("invites").doc(hash),{createdBy:uid,expiresAt,createdAt:now()});
    tx.update(roomRef,{activeInviteHash:hash,activeInviteExpiresAt:expiresAt});
  });
  return {code:`${roomId}.${token}`,expiresAt:expiresAt.toMillis()};
}


async function revokeInvitation(db,request) {
  const uid=authUid(request),data=request.data;
  if(!data||typeof data!=="object"||Array.isArray(data)||
     Object.keys(data).length!==2||Object.keys(data).some(key=>!["roomId","expectedHash"].includes(key))||
     typeof data.expectedHash!=="string"||!/^[a-f0-9]{64}$/.test(data.expectedHash)){
    throw new HttpsError("invalid-argument","취소할 초대 코드를 확인해 주세요.");
  }
  const roomId=roomIdValue(data.roomId);
  const ref=db.collection("rooms").doc(roomId);
  return db.runTransaction(async tx=>{
    const [room,actor,deletion]=await Promise.all([
      tx.get(ref),tx.get(ref.collection("members").doc(uid)),
      tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    requireOwner(room,actor,uid);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 초대를 관리할 수 없습니다.");
    const current=room.get("activeInviteHash");
    if(!current)return {revoked:true,changed:false};
    if(current!==data.expectedHash)throw new HttpsError("aborted","다른 기기에서 초대 코드가 변경됐습니다.");
    tx.update(ref,{activeInviteHash:null,activeInviteExpiresAt:null});
    return {revoked:true,changed:true};
  });
}

async function previewInvitation(db, request) {
  const uid = authUid(request);
  const {roomId,token} = parseJoinCode(request.data?.code);
  const roomRef = db.collection("rooms").doc(roomId);
  const inviteRef = roomRef.collection("invites").doc(hashToken(token));
  const [room,invite,deletion] = await Promise.all([
    roomRef.get(),inviteRef.get(),db.collection("accountDeletionJobs").doc(uid).get()
  ]);
  if(deletion.exists){
    throw new HttpsError("permission-denied","계정 삭제 요청 중에는 초대를 확인할 수 없습니다.");
  }
  if(!room.exists || room.get("status")!=="active" ||
     room.get("activeInviteHash")!==inviteRef.id || !invite.exists ||
     !(invite.get("expiresAt") instanceof Timestamp) ||
     invite.get("expiresAt").toMillis()<=Date.now()){
    throw new HttpsError("permission-denied","초대가 만료되었거나 취소되었습니다.");
  }
  return {
    roomId,
    roomName:room.get("name"),
    description:room.get("description")||"",
    memberCount:room.get("memberCount")||0,
    requiresApproval:room.get("requiresApproval")===true,
    expiresAtMillis:invite.get("expiresAt").toMillis()
  };
}

async function requestJoin(db, request) {
  const uid = authUid(request);
  const {roomId,token} = parseJoinCode(request.data?.code);
  const roomRef = db.collection("rooms").doc(roomId);
  const inviteRef = roomRef.collection("invites").doc(hashToken(token));
  const memberRef = roomRef.collection("members").doc(uid);
  const indexRef = userIndex(db,uid,roomId);
  return db.runTransaction(async tx=>{
    const [room,invite,member,deletion,profile] = await Promise.all([
      tx.get(roomRef),tx.get(inviteRef),tx.get(memberRef),
      tx.get(db.collection("accountDeletionJobs").doc(uid)),
      tx.get(db.collection("users").doc(uid))
    ]);
    if(deletion.exists){
      throw new HttpsError("permission-denied","계정 삭제 요청 중에는 방에 가입할 수 없습니다.");
    }
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
    tx.set(memberRef,{status,displayName:memberDisplayName(profile,request.auth),
      avatar:profileAvatar(profile),joinedAt:now()});
    tx.set(indexRef,{status,roomName:room.data().name,updatedAt:now(),
      ...(!pending?directoryFields(room.data(),room.get("memberCount")+1):{})});
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
  if (typeof targetUid !== "string" || !targetUid || targetUid.length > 128 || targetUid.includes("/")) {
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
    const [room,actor,target,deletion] = await Promise.all([
      tx.get(roomRef),tx.get(actorRef),tx.get(memberRef),
      tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    requireOwner(room,actor,uid);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 멤버를 관리할 수 없습니다.");
    if (!target.exists) throw new HttpsError("not-found", "대상 멤버가 없습니다.");
    const current = target.data().status;
    if (action==="approve" || action==="reject") {
      if (current!=="pending") throw new HttpsError("failed-precondition", "승인 대기 상태가 아닙니다.");
      if(action==="approve"){
        const deletion=await tx.get(db.collection("accountDeletionJobs").doc(targetUid));
        if(deletion.exists){
          throw new HttpsError("failed-precondition","계정 삭제 요청 중인 멤버는 승인할 수 없습니다.");
        }
      }
      if (action==="approve" && room.data().memberCount>=ROOM_CAPACITY) {
        throw new HttpsError("resource-exhausted", "방 정원이 가득 찼습니다.");
      }
      const status = action==="approve"?"active":"rejected";
      tx.update(memberRef,{status,updatedAt:now()});
      tx.set(indexRef,{status,roomName:room.data().name,updatedAt:now(),
        ...(action==="approve"?directoryFields(room.data(),room.get("memberCount")+1):{})});
      tx.update(roomRef,action==="approve"
        ? {pendingCount:FieldValue.increment(-1),memberCount:FieldValue.increment(1)}
        : {pendingCount:FieldValue.increment(-1)});
      return {status};
    }
    if (current!=="active") throw new HttpsError("failed-precondition", "활성 멤버가 아닙니다.");
    if (action==="transfer") {
      const deletion=await tx.get(db.collection("accountDeletionJobs").doc(targetUid));
      if(deletion.exists){
        throw new HttpsError("failed-precondition","삭제 요청 중인 계정에 방장 권한을 위임할 수 없습니다.");
      }
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
    if (isOwner && room.get("activeGameSessionId")) {
      throw new HttpsError("failed-precondition","진행 중인 게임을 먼저 종료해 주세요.");
    }
    if (isOwner && room.data().pendingCount>0) {
      throw new HttpsError("failed-precondition","가입 대기 요청을 먼저 처리한 후 방을 정리해 주세요.");
    }
    tx.update(memberRef,{status:"left",updatedAt:now()});
    tx.set(indexRef,{status:"left",roomName:room.data().name,updatedAt:now()});
    if (isOwner) {
      tx.update(roomRef,{status:"archived",activeInviteHash:null,activeInviteExpiresAt:null,memberCount:0});
    } else {
      tx.update(roomRef,active
        ? {memberCount:FieldValue.increment(-1)}
        : {pendingCount:FieldValue.increment(-1)});
    }
    return {status:"left"};
  });
}

function parseManagementRequest(input, allowed) {
  if (!input || typeof input!=="object" || Array.isArray(input) ||
      Object.keys(input).some(key=>!allowed.includes(key))) {
    throw new HttpsError("invalid-argument","방 관리 요청이 올바르지 않습니다.");
  }
  return roomIdValue(input.roomId);
}

async function currentMemberships(tx, roomRef, room) {
  const snapshot=await tx.get(roomRef.collection("members")
    .where("status","in",["active","pending"])
    .limit(ROOM_CAPACITY+PENDING_CAPACITY+1));
  const active=snapshot.docs.filter(doc=>doc.get("status")==="active").length;
  const pending=snapshot.docs.filter(doc=>doc.get("status")==="pending").length;
  if(active>ROOM_CAPACITY || pending>PENDING_CAPACITY ||
     active!==room.get("memberCount") || pending!==room.get("pendingCount")) {
    throw new HttpsError("failed-precondition","방의 멤버 상태를 다시 확인해 주세요.");
  }
  return snapshot.docs;
}

async function updateRoom(db,request) {
  const uid=authUid(request), input=request.data;
  const roomId=parseManagementRequest(input,["roomId","name","description","requiresApproval","expectedRevision"]);
  const draft=roomDraft(input);
  if(!Number.isSafeInteger(input.expectedRevision)||input.expectedRevision<0) {
    throw new HttpsError("invalid-argument","방 정보 버전이 올바르지 않습니다.");
  }
  const roomRef=db.collection("rooms").doc(roomId);
  return db.runTransaction(async tx=>{
    const [room,actor,deletion]=await Promise.all([
      tx.get(roomRef),tx.get(roomRef.collection("members").doc(uid)),
      tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    requireOwner(room,actor,uid);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 방을 변경할 수 없습니다.");
    const revision=room.get("roomRevision")??0;
    if(revision!==input.expectedRevision)throw new HttpsError("aborted","다른 기기에서 방 정보가 변경됐습니다. 다시 열어 주세요.");
    if(draft.name===room.get("name") && draft.description===(room.get("description")||"") &&
       draft.requiresApproval===room.get("requiresApproval"))return {revision,updated:false};
    const members=await currentMemberships(tx,roomRef,room);
    tx.update(roomRef,{...draft,roomRevision:revision+1,updatedAt:now()});
    if(draft.name!==room.get("name")){
      for(const member of members){
        tx.set(userIndex(db,member.id,roomId),{roomName:draft.name,status:member.get("status"),updatedAt:now()},{merge:true});
      }
    }
    return {revision:revision+1,updated:true};
  });
}

async function updateRoomIcon(db,request){
  const uid=authUid(request),input=request.data;
  const roomId=parseManagementRequest(input,["roomId","roomIcon","expectedRevision"]);
  if(!(input.roomIcon===null||ROOM_ICONS.has(input.roomIcon))||!Number.isSafeInteger(input.expectedRevision)||input.expectedRevision<0)
    throw new HttpsError("invalid-argument","방 아이콘 또는 버전이 올바르지 않습니다.");
  const ref=db.collection("rooms").doc(roomId);
  return db.runTransaction(async tx=>{
    const [room,member,deletion]=await Promise.all([tx.get(ref),tx.get(ref.collection("members").doc(uid)),tx.get(db.collection("accountDeletionJobs").doc(uid))]);
    requireOwner(room,member,uid);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 방을 변경할 수 없습니다.");
    const revision=room.get("roomRevision")??0;
    if(revision!==input.expectedRevision)throw new HttpsError("aborted","방 정보가 변경됐습니다. 다시 열어 주세요.");
    if((room.get("roomIcon")??null)===input.roomIcon)return {revision,updated:false};
    const members=await currentMemberships(tx,ref,room);
    tx.update(ref,{roomIcon:input.roomIcon,roomRevision:revision+1,updatedAt:now()});
    for(const member of members.filter(doc=>doc.get("status")==="active")){
      tx.set(userIndex(db,member.id,roomId),{roomIcon:input.roomIcon},{merge:true});
    }
    return {revision:revision+1,updated:true};
  });
}

async function archiveRoom(db,request) {
  const uid=authUid(request),input=request.data;
  const roomId=parseManagementRequest(input,["roomId","confirm"]);
  if(input.confirm!=="ARCHIVE")throw new HttpsError("invalid-argument","방 종료를 확인해 주세요.");
  const roomRef=db.collection("rooms").doc(roomId);
  return db.runTransaction(async tx=>{
    const [room,actor,deletion]=await Promise.all([
      tx.get(roomRef),tx.get(roomRef.collection("members").doc(uid)),
      tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    if(room.exists && room.get("ownerId")===uid && room.get("status")==="archived" &&
       actor.exists && actor.get("status")==="archived")return {status:"archived",deduplicated:true};
    requireOwner(room,actor,uid);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 방을 변경할 수 없습니다.");
    if(room.get("activeGameSessionId"))throw new HttpsError("failed-precondition","진행 중인 게임을 먼저 종료해 주세요.");
    const members=await currentMemberships(tx,roomRef,room);
    for(const member of members){
      tx.update(member.ref,{status:"archived",updatedAt:now()});
      tx.set(userIndex(db,member.id,roomId),{roomName:room.get("name"),status:"archived",updatedAt:now()});
    }
    tx.update(roomRef,{status:"archived",archivedAt:now(),activeInviteHash:null,
      activeInviteExpiresAt:null,memberCount:0,pendingCount:0});
    return {status:"archived",deduplicated:false};
  });
}

module.exports = {createRoom,createInvitation,revokeInvitation,previewInvitation,requestJoin,changeMember,leaveRoom,updateRoom,archiveRoom,updateRoomIcon,roomDirectoryChanged,syncRoomDirectory};
