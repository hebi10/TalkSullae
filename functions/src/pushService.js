"use strict";

const {createHash}=require("node:crypto");
const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid}=require("./policy");

const TOKEN_RE=/^[A-Za-z0-9:._~-]{50,4096}$/;
const DEVICE_RE=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_TOKENS=3;
const MAX_DEVICE_RECORDS=12;
const MAX_MEMBERS=20;
const COOLDOWN_MS=30000;
const idFor=value=>createHash("sha256").update(value).digest("hex");

function parseRegistration(data){
  if(!data||typeof data!=="object"||Array.isArray(data)||
     Object.keys(data).some(key=>!["token","enabled","deviceId","revision"].includes(key))||
     typeof data.token!=="string"||!TOKEN_RE.test(data.token)||
     typeof data.deviceId!=="string"||!DEVICE_RE.test(data.deviceId)||
     !Number.isSafeInteger(data.revision)||data.revision<1||
     typeof data.enabled!=="boolean"){
    throw new HttpsError("invalid-argument","푸시 알림 요청이 올바르지 않습니다.");
  }
  return {
    token:data.token,enabled:data.enabled,deviceId:data.deviceId,
    revision:data.revision
  };
}

async function registerPushToken(db,request){
  const uid=authUid(request);
  const input=parseRegistration(request.data);
  const ref=db.collection("users").doc(uid);
  const devices=ref.collection("pushDevices");
  const deviceRef=devices.doc(idFor(input.deviceId));
  return db.runTransaction(async tx=>{
    const [user,deleted,current,all,active]=await Promise.all([
      tx.get(ref),
      tx.get(db.collection("accountDeletionJobs").doc(uid)),
      tx.get(deviceRef),
      tx.get(devices.limit(MAX_DEVICE_RECORDS+1)),
      tx.get(devices.where("enabled","==",true).limit(MAX_TOKENS+1))
    ]);
    if(deleted.exists){
      throw new HttpsError("permission-denied","계정 삭제 진행 중에는 알림을 등록할 수 없습니다.");
    }
    if(!user.exists){
      throw new HttpsError("failed-precondition","프로필 설정을 완료해 주세요.");
    }
    if(!current.exists&&all.size>=MAX_DEVICE_RECORDS){
      throw new HttpsError("resource-exhausted","기기 등록 내역이 많아 운영자 확인이 필요합니다.");
    }
    const previousRevision=current.exists?current.get("revision"):0;
    if(!Number.isSafeInteger(previousRevision)||previousRevision<0){
      throw new HttpsError("failed-precondition","알림 기기 정보를 확인할 수 없습니다.");
    }
    if(input.revision<previousRevision){
      return {
        enabled:current.get("enabled")===true,
        revision:previousRevision,applied:false
      };
    }
    const previouslyEnabled=current.exists&&current.get("enabled")===true;
    if(input.revision===previousRevision&&previouslyEnabled!==input.enabled){
      throw new HttpsError("failed-precondition","이전 알림 변경 요청을 재사용할 수 없습니다.");
    }
    if(input.enabled&&!previouslyEnabled&&active.size>=MAX_TOKENS){
      throw new HttpsError("resource-exhausted","알림 기기는 최대 3대까지 등록할 수 있습니다.");
    }
    tx.set(deviceRef,{
      enabled:input.enabled,
      token:input.enabled?input.token:null,
      revision:input.revision,
      updatedAt:FieldValue.serverTimestamp()
    });
    return {enabled:input.enabled,revision:input.revision,applied:true};
  });
}

async function notifyOneUser(db,uid,roomId,kind,skipUid,messaging,now=Date.now){
  if(uid===skipUid)return 0;
  const roomRef=db.collection("rooms").doc(roomId);
  const memberRef=roomRef.collection("members").doc(uid);
  const devicesRef=db.collection("users").doc(uid).collection("pushDevices");
  const [member,deleted,block,devices]=await Promise.all([
    memberRef.get(),
    db.collection("accountDeletionJobs").doc(uid).get(),
    skipUid?db.collection("userSafety").doc(uid).collection("blocks").doc(skipUid).get():
      Promise.resolve({exists:false}),
    devicesRef.where("enabled","==",true).limit(MAX_TOKENS+1).get()
  ]);
  if(!member.exists||member.get("status")!=="active"||deleted.exists||block.exists||
     devices.empty||devices.size>MAX_TOKENS)return 0;
  const valid=devices.docs.filter(row=>{
    const token=row.get("token");
    return typeof token==="string"&&TOKEN_RE.test(token);
  });
  if(valid.length===0)return 0;
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
  const messages=valid.map(row=>({
    token:row.get("token"),
    data:{kind,roomId,recipientUid:uid},
    android:{priority:"normal"}
  }));
  // Data-only, recipient-verified notifications. Never include chat text,
  // real aliases, secret game roles, reports or authentication information.
  const response=await messaging.sendEach(messages);
  await Promise.all(response.responses.map(async(item,index)=>{
    if(item.success)return;
    const error=item.error?.code||"";
    if(["messaging/registration-token-not-registered",
        "messaging/invalid-registration-token"].includes(error)){
      const doc=valid[index];
      await db.runTransaction(async tx=>{
        const latest=await tx.get(doc.ref);
        if(latest.exists&&latest.get("token")===messages[index].token){
          tx.update(doc.ref,{
            token:null,updatedAt:FieldValue.serverTimestamp()
          });
        }
      });
    }
  }));
  return response.successCount;
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
  MAX_TOKENS,TOKEN_RE,DEVICE_RE,parseRegistration,registerPushToken,
  notifyRoomActivity,notifyApprovedMember
};
