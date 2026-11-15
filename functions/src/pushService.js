"use strict";

const {createHash}=require("node:crypto");
const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid}=require("./policy");

const TOKEN_RE=/^[A-Za-z0-9:._~-]{50,4096}$/;
const MAX_TOKENS=3;
const MAX_MEMBERS=20;
const COOLDOWN_MS=30000;
const tokenId=token=>createHash("sha256").update(token).digest("hex");

function parseRegistration(data){
  if(!data||typeof data!=="object"||Array.isArray(data)||
     Object.keys(data).some(key=>!["token","enabled"].includes(key))||
     typeof data.token!=="string"||!TOKEN_RE.test(data.token)||
     typeof data.enabled!=="boolean"){
    throw new HttpsError("invalid-argument","푸시 알림 요청이 올바르지 않습니다.");
  }
  return {token:data.token,enabled:data.enabled};
}

async function registerPushToken(db,request){
  const uid=authUid(request);
  const {token,enabled}=parseRegistration(request.data);
  const ref=db.collection("users").doc(uid);
  const tokenRef=ref.collection("pushTokens").doc(tokenId(token));
  await db.runTransaction(async tx=>{
    const [user,deleted,current,all]=await Promise.all([
      tx.get(ref),
      tx.get(db.collection("accountDeletionJobs").doc(uid)),
      tx.get(tokenRef),
      tx.get(ref.collection("pushTokens").limit(MAX_TOKENS+1))
    ]);
    if(deleted.exists)throw new HttpsError("permission-denied","계정 삭제 진행 중에는 알림을 등록할 수 없습니다.");
    if(enabled&&!user.exists)throw new HttpsError("failed-precondition","프로필 설정을 완료해 주세요.");
    if(enabled&&!current.exists&&all.size>=MAX_TOKENS){
      throw new HttpsError("resource-exhausted","등록된 기기는 최대 3대입니다.");
    }
    if(enabled){
      tx.set(tokenRef,{token,enabled:true,updatedAt:FieldValue.serverTimestamp()});
    }else{
      tx.delete(tokenRef);
    }
  });
  return {enabled};
}

async function notifyOneUser(db,uid,roomId,kind,skipUid,messaging,now=Date.now){
  if(uid===skipUid)return 0;
  const roomRef=db.collection("rooms").doc(roomId);
  const memberRef=roomRef.collection("members").doc(uid);
  const tokensRef=db.collection("users").doc(uid).collection("pushTokens");
  const [member,deleted,block,tokens]=await Promise.all([
    memberRef.get(),
    db.collection("accountDeletionJobs").doc(uid).get(),
    skipUid?db.collection("userSafety").doc(uid).collection("blocks").doc(skipUid).get():
      Promise.resolve({exists:false}),
    tokensRef.limit(MAX_TOKENS+1).get()
  ]);
  if(!member.exists||member.get("status")!=="active"||deleted.exists||block.exists||
     tokens.empty||tokens.size>MAX_TOKENS)return 0;
  const cooldown=db.collection("users").doc(uid)
    .collection("pushCooldowns").doc(roomId+"-"+kind);
  const time=now();
  const claimed=await db.runTransaction(async tx=>{
    const previous=await tx.get(cooldown);
    const last=previous.exists?previous.get("at"):null;
    if(last instanceof Timestamp&&time-last.toMillis()<COOLDOWN_MS)return false;
    tx.set(cooldown,{at:Timestamp.fromMillis(time)});
    return true;
  });
  if(!claimed)return 0;
  const messages=tokens.docs.map(doc=>({
    token:doc.get("token"),
    data:{kind,roomId,recipientUid:uid},
    android:{priority:"normal"}
  }));
  // Data-only notifications: the device verifies the current signed-in UID
  // before showing a generic local notification. No chat body or hidden game role.
  const result=await messaging.sendEach(messages);
  await Promise.all(result.responses.map(async(response,i)=>{
    if(response.success)return;
    const code=response.error?.code||"";
    if(["messaging/registration-token-not-registered",
        "messaging/invalid-registration-token"].includes(code)){
      await tokens.docs[i].ref.delete();
    }
  }));
  return result.successCount;
}

async function notifyRoomActivity(db,roomId,kind,skipUid,messaging,now=Date.now){
  if(!/^[A-Za-z0-9]{20}$/.test(roomId)||!["message","game","approved"].includes(kind)){
    return {sent:0,skipped:"invalid-source"};
  }
  const roomRef=db.collection("rooms").doc(roomId);
  const [room,members]=await Promise.all([
    roomRef.get(),roomRef.collection("members").where("status","==","active")
      .limit(MAX_MEMBERS+1).get()
  ]);
  if(!room.exists||room.get("status")!=="active"||members.size>MAX_MEMBERS){
    return {sent:0,skipped:"room-not-active"};
  }
  let sent=0;
  for(const member of members.docs){
    sent+=await notifyOneUser(db,member.id,roomId,kind,skipUid,messaging,now);
  }
  return {sent};
}

async function notifyApprovedMember(db,roomId,uid,messaging,now=Date.now){
  if(!/^[A-Za-z0-9]{20}$/.test(roomId)||typeof uid!=="string"||!uid||
     uid.includes("/"))return {sent:0};
  const room=await db.collection("rooms").doc(roomId).get();
  if(!room.exists||room.get("status")!=="active")return {sent:0};
  return {sent:await notifyOneUser(db,uid,roomId,"approved",null,messaging,now)};
}

module.exports={
  MAX_TOKENS,TOKEN_RE,parseRegistration,registerPushToken,
  notifyRoomActivity,notifyApprovedMember
};
