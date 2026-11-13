"use strict";

const {createHash}=require("node:crypto");
const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {getAuth}=require("firebase-admin/auth");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid,roomIdValue}=require("./policy");

const UUID=/^[a-f\d]{8}-[a-f\d]{4}-4[a-f\d]{3}-[89ab][a-f\d]{3}-[a-f\d]{12}$/i;
const USER_ID=/^[^/\u0000-\u001f]{1,128}$/u;
const HASH=value=>createHash("sha256").update(value).digest("hex");
const MAX_ROOMS=100;
const MAX_SESSIONS=100;
const RECENT_AUTH_SECONDS=300;
const ALLOWED_CATEGORIES=new Set(["abuse","child_safety","other"]);

function object(input,keys){
  return input&&typeof input==="object"&&!Array.isArray(input)&&
    Object.keys(input).every(x=>keys.includes(x));
}
function validateTarget(input,keys){
  if(!object(input,keys)||typeof input.targetUid!=="string"||
     !USER_ID.test(input.targetUid))throw new HttpsError("invalid-argument","대상 계정이 올바르지 않습니다.");
  return input.targetUid;
}
function validateUuid(s){
  if(typeof s!=="string"||!UUID.test(s))throw new HttpsError("invalid-argument","요청 ID 형식이 올바르지 않습니다.");
  return s.toLowerCase();
}

async function setUserBlock(db,request){
  const uid=authUid(request);
  const targetUid=validateTarget(request.data,["targetUid","blocked"]);
  const blocked=request.data.blocked;
  if(targetUid===uid||typeof blocked!=="boolean"){
    throw new HttpsError("invalid-argument","본인 차단 또는 상태가 올바르지 않습니다.");
  }
  const ownerRef=db.collection("userSafety").doc(uid);
  const ref=ownerRef.collection("blocks").doc(targetUid);
  return db.runTransaction(async tx=>{
    const [owner,existing,deletion]=await Promise.all([
      tx.get(ownerRef),tx.get(ref),tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청이 진행 중입니다.");
    const count=owner.exists?(owner.get("blockCount")||0):0;
    if(!Number.isSafeInteger(count)||count<0||count>100){
      throw new HttpsError("failed-precondition","차단 목록을 확인할 수 없습니다.");
    }
    if(blocked && !existing.exists && count>=100){
      throw new HttpsError("resource-exhausted","차단 목록은 최대 100명입니다.");
    }
    if(blocked===existing.exists)return {targetUid,blocked};
    tx.set(ownerRef,{blockCount:FieldValue.increment(blocked?1:-1)},{merge:true});
    if(blocked)tx.create(ref,{createdAt:FieldValue.serverTimestamp()});
    else tx.delete(ref);
    return {targetUid,blocked};
  });
}

async function reportChatMessage(db,request,now=Date.now){
  const uid=authUid(request),data=request.data;
  if(!object(data,["roomId","messageId","requestId","category","details"]) ||
    typeof data.messageId!=="string"||!/^[a-f0-9]{64}$/.test(data.messageId) ||
    !ALLOWED_CATEGORIES.has(data.category) ||
    typeof data.details!=="string"||data.details.trim().length<5||
    data.details.length>1000)throw new HttpsError("invalid-argument","신고 항목이 올바르지 않습니다.");
  const roomId=roomIdValue(data.roomId);
  const requestId=validateUuid(data.requestId);
  const roomRef=db.collection("rooms").doc(roomId);
  const messageRef=roomRef.collection("messages").doc(data.messageId);
  const memberRef=roomRef.collection("members").doc(uid);
  const reportId=HASH(JSON.stringify(["chat-report",uid,requestId]));
  const reportRef=db.collection("safetyReports").doc(reportId);
  const rateRef=db.collection("safetyReportRateLimits").doc(HASH(uid));
  return db.runTransaction(async tx=>{
    const [room,member,message,previous,rate]=await Promise.all([
      tx.get(roomRef),tx.get(memberRef),tx.get(messageRef),tx.get(reportRef),tx.get(rateRef)
    ]);
    if(!room.exists||room.get("status")!=="active"||!member.exists||
       member.get("status")!=="active")throw new HttpsError("permission-denied","신고할 방에 접근할 수 없습니다.");
    if(!message.exists||typeof message.get("senderUid")!=="string"){
      throw new HttpsError("not-found","신고할 메시지가 없습니다.");
    }
    if(previous.exists){
      if(previous.get("actorUid")!==uid||previous.get("requestId")!==requestId||
         previous.get("roomId")!==roomId||previous.get("messageId")!==data.messageId||
         previous.get("category")!==data.category||previous.get("details")!==data.details.trim()){
        throw new HttpsError("already-exists","같은 요청 ID를 다른 신고에 재사용할 수 없습니다.");
      }
      return {reportId,deduplicated:true};
    }
    if(rate.exists){
      const last=rate.get("lastSubmittedAt");
      if(!(last instanceof Timestamp)||now()<last.toMillis()+60000){
        throw new HttpsError("resource-exhausted","신고는 1분 간격으로 제출해 주세요.");
      }
    }
    tx.create(reportRef,{
      actorUid:uid,requestId,roomId,messageId:data.messageId,
      reportedUid:message.get("senderUid"),
      evidence:{
        senderName:typeof message.get("senderName")==="string"
          ?message.get("senderName").slice(0,30):"멤버",
        text:typeof message.get("text")==="string"
          ?message.get("text").slice(0,1000):"",
        createdAt:message.get("createdAt")||null
      },
      category:data.category,details:data.details.trim(),
      status:"received",createdAt:FieldValue.serverTimestamp()
    });
    tx.set(rateRef,{lastSubmittedAt:Timestamp.fromMillis(now())});
    return {reportId,deduplicated:false};
  });
}

async function requestAccountDeletion(db,request,now=Date.now){
  const uid=authUid(request);
  if(!object(request.data,["confirm","expectedUid"])||
     request.data.confirm!=="DELETE"||request.data.expectedUid!==uid){
    throw new HttpsError("invalid-argument","계정 삭제를 다시 확인해 주세요.");
  }
  const authTime=Number(request.auth?.token?.auth_time);
  if(!Number.isSafeInteger(authTime)||
     Math.floor(now()/1000)-authTime>RECENT_AUTH_SECONDS ||
     Math.floor(now()/1000)<authTime){
    throw new HttpsError("failed-precondition","최근 Google 로그인으로 본인 확인이 필요합니다.");
  }
  const index=await db.collection("userRooms").doc(uid).collection("rooms").limit(MAX_ROOMS+1).get();
  if(index.size>MAX_ROOMS)throw new HttpsError("resource-exhausted","방이 많아 운영자 확인이 필요합니다.");
  for(const d of index.docs){
    const room=await db.collection("rooms").doc(d.id).get();
    if(room.exists&&room.get("status")==="active"&&room.get("ownerId")===uid){
      throw new HttpsError("failed-precondition","방장인 방은 먼저 다른 멤버에게 위임하거나 종료해 주세요.");
    }
    if(room.exists&&typeof room.get("activeGameSessionId")==="string"){
      const session=await room.ref.collection("gameSessions").doc(room.get("activeGameSessionId")).get();
      if(session.exists&&session.get("participants")?.[uid]&&
         !["finished","cancelled"].includes(session.get("phase"))){
        throw new HttpsError("failed-precondition","참여 중인 게임이 끝난 뒤 계정을 삭제할 수 있습니다.");
      }
    }
  }
  const ref=db.collection("accountDeletionJobs").doc(uid);
  await db.runTransaction(async tx=>{
    const old=await tx.get(ref);
    if(old.exists&&["queued","processing","completed"].includes(old.get("status")))return;
    tx.set(ref,{status:"queued",requestedAt:FieldValue.serverTimestamp(),attempts:0});
  });
  return {status:"queued"};
}

async function removeMessages(db,ref,uid,field="senderUid"){
  let count=0;
  while(true){
    const snapshots=await ref.where(field,"==",uid).limit(100).get();
    if(snapshots.empty)break;
    const batch=db.batch();
    for(const row of snapshots.docs)batch.delete(row.ref);
    await batch.commit();
    count+=snapshots.size;
    if(count>10000)throw new Error("User deletion requires manual high-volume review");
  }
  return count;
}

// Server-only account removal. All user-authored message payloads are purged before Auth deletion.
// Failures leave a retryable queued job; successful deletion is not claimed until Auth is gone.
async function processOneAccountDeletion(db,uid,auth=getAuth()){
  const jobRef=db.collection("accountDeletionJobs").doc(uid);
  const job=await jobRef.get();
  if(!job.exists||!["queued","processing"].includes(job.get("status")))return {status:"skipped"};
  await jobRef.update({status:"processing",attempts:FieldValue.increment(1)});
  const userRooms=db.collection("userRooms").doc(uid);
  const roomIndex=await userRooms.collection("rooms").limit(MAX_ROOMS+1).get();
  if(roomIndex.size>MAX_ROOMS)throw new Error("Manual review: too many rooms");
  const refs=[];
  for(const item of roomIndex.docs){
    const roomRef=db.collection("rooms").doc(item.id);
    const room=await roomRef.get();
    if(!room.exists)continue;
    if(room.get("status")==="active"&&room.get("ownerId")===uid) {
      throw new Error("Manual review: user owns active room");
    }
    const sessions=await roomRef.collection("gameSessions").limit(MAX_SESSIONS+1).get();
    if(sessions.size>MAX_SESSIONS)throw new Error("Manual review: too many sessions");
    // Recheck at deletion time, not only when the user requested removal.
    // A game may have begun between queuing and scheduled processing.
    if(sessions.docs.some(s=>
       s.get("participants")?.[uid] &&
       !["finished","cancelled"].includes(s.get("phase")))){
      throw new Error("Account still participates in an active game");
    }
    refs.push({roomRef,room,sessions});
  }
  for(const {roomRef,sessions} of refs){
    await removeMessages(db,roomRef.collection("messages"),uid);
    for(const session of sessions.docs){
      const sessionRef=session.ref;
      const assignmentRef=sessionRef.collection("privateAssignments").doc(uid);
      const assignment=await assignmentRef.get();
      const alias=assignment.exists?assignment.get("data")?.alias:null;
      if(session.get("gameId")==="identity" && typeof alias==="string"){
        // Identity messages deliberately contain no sender UID. Resolve their
        // server-only alias before deleting the assignment, within that session only.
        await removeMessages(db,sessionRef.collection("publicMessages"),alias,"alias");
      }else{
        await removeMessages(db,sessionRef.collection("publicMessages"),uid);
      }
      // Sensitive role/submission documents are server-only; shared results
      // remain immutable until the separate shared-history privacy review.
      await assignmentRef.delete();
      const ballots=await sessionRef.collection("privateSubmissions").where("actorUid","==",uid).get();
      for(let i=0;i<ballots.docs.length;i+=100){
        const batch=db.batch();
        for(const d of ballots.docs.slice(i,i+100))batch.delete(d.ref);
        await batch.commit();
      }
      const receipts=await sessionRef.collection("processedActions").where("actorUid","==",uid).get();
      for(let i=0;i<receipts.docs.length;i+=100){
        const batch=db.batch();
        for(const d of receipts.docs.slice(i,i+100))batch.delete(d.ref);
        await batch.commit();
      }
    }
    await db.runTransaction(async tx=>{
      const [room,member]=await Promise.all([
        tx.get(roomRef),tx.get(roomRef.collection("members").doc(uid))
      ]);
      if(!room.exists||!member.exists)return;
      if(room.get("status")==="active"&&room.get("ownerId")===uid){
        throw new Error("Manual review: user became owner during deletion");
      }
      const current=member.get("status");
      if(current==="active"||current==="pending"){
        const prop=current==="active"?"memberCount":"pendingCount";
        tx.update(roomRef,{[prop]:FieldValue.increment(-1)});
      }
      tx.delete(member.ref);
    });
  }
  await db.recursiveDelete(userRooms);
  await db.recursiveDelete(db.collection("userSafety").doc(uid));
  const reported=await db.collection("safetyReports").where("actorUid","==",uid).get();
  for(const d of reported.docs){
    await d.ref.update({actorUid:"deleted-account",requestId:FieldValue.delete()});
  }
  const rateRef=db.collection("safetyReportRateLimits").doc(HASH(uid));
  await rateRef.delete();
  try{await auth.deleteUser(uid);}catch(error){
    if(error.code!=="auth/user-not-found")throw error;
  }
  await jobRef.delete();
  return {status:"completed"};
}

async function processAccountDeletionQueue(db,auth=getAuth()){
  const jobs=await db.collection("accountDeletionJobs").where("status","in",["queued","processing"]).limit(5).get();
  const summary={completed:0,failed:0};
  for(const doc of jobs.docs){
    try{
      const result=await processOneAccountDeletion(db,doc.id,auth);
      if(result.status==="completed")summary.completed++;
    }catch(error){
      summary.failed++;
      const attempts=(doc.get("attempts")||0)+1;
      const manual=/Manual review|Account still participates/.test(String(error.message||""));
      await doc.ref.update({
        status:manual?"manual-review":attempts>=3?"needs-attention":"queued",
        lastError:manual?"manual-review-required":"retry-required",
        updatedAt:FieldValue.serverTimestamp()
      });
    }
  }
  return summary;
}

module.exports={
  setUserBlock,reportChatMessage,requestAccountDeletion,
  processOneAccountDeletion,processAccountDeletionQueue
};
