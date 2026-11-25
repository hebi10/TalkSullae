"use strict";

const {createHash,randomUUID}=require("node:crypto");
const {FieldValue,Timestamp,FieldPath}=require("firebase-admin/firestore");
const {getAuth}=require("firebase-admin/auth");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid,roomIdValue}=require("./policy");
const {removeSender}=require("./chatUnread");

const UUID=/^[a-f\d]{8}-[a-f\d]{4}-4[a-f\d]{3}-[89ab][a-f\d]{3}-[a-f\d]{12}$/i;
const USER_ID=/^[^/\u0000-\u001f]{1,128}$/u;
const HASH=value=>createHash("sha256").update(value).digest("hex");
const MAX_ROOMS=100;
const DELETE_ROOM_BATCH=10;
const DELETE_ROWS=100;
const DELETE_SEASONS=40;
const DELETE_SESSIONS=20;
const DELETE_BUDGET_MS=45_000;
const DELETE_STAGES=["personal","messages","seasons","sessions","membership"];
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
    const [room,member,message,previous,rate,deletion]=await Promise.all([
      tx.get(roomRef),tx.get(memberRef),tx.get(messageRef),tx.get(reportRef),tx.get(rateRef),
      tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 신고를 등록할 수 없습니다.");
    if(!room.exists||room.get("status")!=="active"||!member.exists||
       member.get("status")!=="active")throw new HttpsError("permission-denied","신고할 방에 접근할 수 없습니다.");
    if(previous.exists){
      if(previous.get("actorUid")!==uid||previous.get("requestId")!==requestId||
         previous.get("roomId")!==roomId||previous.get("messageId")!==data.messageId||
         previous.get("category")!==data.category||previous.get("details")!==data.details.trim()){
        throw new HttpsError("already-exists","같은 요청 ID를 다른 신고에 재사용할 수 없습니다.");
      }
      return {reportId,deduplicated:true};
    }
    if(!message.exists||typeof message.get("senderUid")!=="string"){
      throw new HttpsError("not-found","신고할 메시지가 없습니다.");
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
  const ref=db.collection("accountDeletionJobs").doc(uid);
  const indexQuery=db.collection("userRooms").doc(uid)
    .collection("rooms").where("status","in",["active","pending"]).limit(MAX_ROOMS+1);
  return db.runTransaction(async tx=>{
    // Read room index, room state, active sessions and job in this same
    // transaction; all join/create/game transactions read the job document.
    // A conflicting concurrent join is retried rather than stranded in a
    // waiting game after the deletion request was acknowledged.
    const [existing,index]=await Promise.all([tx.get(ref),tx.get(indexQuery)]);
    if(existing.exists && ["queued","processing","completed","manual-review","needs-attention"].includes(existing.get("status"))){
      return {status:"queued",jobStatus:existing.get("status")};
    }
    if(index.size>MAX_ROOMS){
      throw new HttpsError("resource-exhausted","방이 많아 운영자 확인이 필요합니다.");
    }
    const rooms=await Promise.all(index.docs.map(item=>
      tx.get(db.collection("rooms").doc(item.id))));
    const sessions=await Promise.all(rooms.map(room=>
      room.exists && typeof room.get("activeGameSessionId")==="string"
        ?tx.get(room.ref.collection("gameSessions").doc(room.get("activeGameSessionId")))
        :Promise.resolve(null)));
    for(let i=0;i<rooms.length;i++){
      const room=rooms[i],session=sessions[i];
      if(room.exists&&room.get("status")==="active"&&room.get("ownerId")===uid){
        throw new HttpsError("failed-precondition","방장인 방은 먼저 다른 멤버에게 위임하거나 종료해 주세요.");
      }
      if(session?.exists && session.get("participants")?.[uid] &&
         !["finished","cancelled"].includes(session.get("phase"))){
        throw new HttpsError("failed-precondition","참여 중인 게임이 끝난 뒤 계정을 삭제할 수 있습니다.");
      }
    }
    tx.set(ref,{status:"queued",requestedAt:FieldValue.serverTimestamp(),attempts:0});
    return {status:"queued"};
  });
}

// Bounded, idempotent drains: only owned records, never another member's data.
async function drainRows(db,query){
  const page=await query.limit(DELETE_ROWS+1).get();
  if(page.empty)return true;
  const batch=db.batch();
  for(const row of page.docs.slice(0,DELETE_ROWS))batch.delete(row.ref);
  await batch.commit();
  return page.size<=DELETE_ROWS;
}

async function documentPage(collection,cursor,limit){
  let query=collection.orderBy(FieldPath.documentId()).limit(limit+1);
  if(cursor!==null)query=query.startAfter(cursor);
  const page=await query.get();
  return {docs:page.docs.slice(0,limit),more:page.size>limit};
}

function roomProgress(index){
  const value=index.get("deletionProgress");
  if(value===undefined)return {stage:"personal",cursor:null};
  if(!value||!DELETE_STAGES.includes(value.stage)||
     !(value.cursor===null||(typeof value.cursor==="string"&&value.cursor.length<=1500&&!value.cursor.includes("/")))){
    throw new Error("Manual review: invalid deletion checkpoint");
  }
  return {stage:value.stage,cursor:value.cursor};
}

async function assertNoActiveParticipation(roomRef,room,uid){
  if(room.get("status")==="active"&&room.get("ownerId")===uid)
    throw new Error("Manual review: user owns active room");
  // The active pointer is authoritative; additionally check legacy/orphaned
  // nonterminal sessions before touching any private records.
  const pointer=room.get("activeGameSessionId");
  if(typeof pointer==="string"){
    const active=await roomRef.collection("gameSessions").doc(pointer).get();
    if(active.exists&&Object.hasOwn(active.get("participants")||{},uid)&&
       !["finished","cancelled"].includes(active.get("phase")))
      throw new Error("Account still participates in an active game");
  }
  const active=await roomRef.collection("gameSessions")
    .where("phase","in",["waiting","ready","assigning","playing","voting","reveal"]).limit(21).get();
  if(active.size>20)throw new Error("Manual review: inconsistent active sessions");
  if(active.docs.some(session=>Object.hasOwn(session.get("participants")||{},uid)))
    throw new Error("Account still participates in an active game");
}

// Remove direct identifiers from terminal public game history; keep game totals and other members\' scores.
async function anonymizeTerminalGame(db,sessionRef,uid){
  await db.runTransaction(async tx=>{
    const resultRef=sessionRef.collection("results").doc("final");
    const [session,result]=await Promise.all([tx.get(sessionRef),tx.get(resultRef)]);
    if(!session.exists)return;
    const participants=session.get("participants")||{};
    const hasParticipant=Object.hasOwn(participants,uid);
    const hasScore=result.exists&&Object.hasOwn(result.get("scoresByUid")||{},uid);
    const isCreator=session.get("createdBy")===uid;
    if(!hasParticipant&&!hasScore&&!isCreator)return;
    if(!["finished","cancelled"].includes(session.get("phase")))throw new Error("Account still participates in an active game");
    // A fresh per-session label is not derived from the original UID. No reverse mapping is stored.
    const anonymous="deleted-"+randomUUID();
    const replaceKey=map=>Object.fromEntries(Object.entries(map).map(([key,value])=>[key===uid?anonymous:key,value]));
    const changes={};
    if(hasParticipant)changes.participants=replaceKey(participants);
    if(isCreator){changes.createdBy=anonymous;changes.createRequestId=FieldValue.delete();}
    const pub=session.get("publicState");
    if(pub&&typeof pub==="object"&&!Array.isArray(pub)){
      const state={...pub};
      for(const key of ["options","eliminatedUids"]){
        if(Array.isArray(state[key]))state[key]=state[key].map(value=>value===uid?anonymous:value);
      }
      if(hasParticipant&&typeof state.publishedResult==="string"){
        // Historic free-form result strings contain display names; exact substitution is unsafe.
        state.publishedResult="개인정보 보호를 위해 일부 공개 결과가 정리되었습니다.";
      }
      changes.publicState=state;
    }
    if(Object.keys(changes).length)tx.update(sessionRef,changes);
    if(hasScore)tx.update(resultRef,{scoresByUid:replaceKey(result.get("scoresByUid"))});
  });
}

async function cleanupSession(db,session,uid){
  const ref=session.ref;
  if(!["finished","cancelled"].includes(session.get("phase"))){
    if(Object.hasOwn(session.get("participants")||{},uid)||session.get("createdBy")===uid)
      throw new Error("Account still participates in an active game");
    // New sessions cannot include a queued account. Do not mutate somebody
    // else's live round or private role assignments.
    return true;
  }
  const assignmentRef=ref.collection("privateAssignments").doc(uid);
  const assignment=await assignmentRef.get();
  const alias=assignment.exists?assignment.get("data")?.alias:null;
  const messages=ref.collection("publicMessages");
  const query=session.get("gameId")==="identity"&&typeof alias==="string"
    ?messages.where("alias","==",alias):messages.where("senderUid","==",uid);
  if(!await drainRows(db,query))return false;
  if(!await drainRows(db,ref.collection("privateSubmissions").where("actorUid","==",uid)))return false;
  if(!await drainRows(db,ref.collection("processedActions").where("actorUid","==",uid)))return false;
  await anonymizeTerminalGame(db,ref,uid);
  // Retain the alias until all related anonymous messages and records are gone.
  const batch=db.batch();
  batch.delete(assignmentRef);
  batch.delete(ref.collection("gameChatRateLimits").doc(uid));
  await batch.commit();
  return true;
}

async function cleanupRoom(db,index,uid,deadline){
  const roomRef=db.collection("rooms").doc(index.id);
  const room=await roomRef.get();
  if(!room.exists){await index.ref.delete();return true;}
  await assertNoActiveParticipation(roomRef,room,uid);
  let progress=roomProgress(index);
  async function checkpoint(stage,cursor=null){
    progress={stage,cursor};
    await index.ref.update({deletionProgress:progress});
  }
  // A checkpoint is advanced only after all writes for that page have
  // succeeded. Retrying a failed page repeats safe, idempotent cleanup.
  if(progress.stage==="personal"){
    const stats=roomRef.collection("playerStats").doc(uid);
    if(!await drainRows(db,stats.collection("games")))return false;
    await stats.delete();
    await roomRef.collection("recentCompletedGameSessions").doc(uid).delete();
    await checkpoint("messages");
  }
  if(progress.stage==="messages"){
    if(!await drainRows(db,roomRef.collection("messages").where("senderUid","==",uid)))return false;
    await roomRef.collection("chatRateLimits").doc(uid).delete();
    await checkpoint("seasons");
  }
  if(progress.stage==="seasons"){
    const page=await documentPage(roomRef.collection("seasons"),progress.cursor,DELETE_SEASONS);
    const batch=db.batch();
    for(const season of page.docs)batch.delete(season.ref.collection("scores").doc(uid));
    if(page.docs.length)await batch.commit();
    if(page.more){
      await checkpoint("seasons",page.docs.at(-1).id);
      return false;
    }
    await checkpoint("sessions");
  }
  if(progress.stage==="sessions"){
    const page=await documentPage(roomRef.collection("gameSessions"),progress.cursor,DELETE_SESSIONS);
    for(const session of page.docs){
      if(Date.now()>=deadline)return false;
      if(!await cleanupSession(db,session,uid))return false;
      await checkpoint("sessions",session.id);
    }
    if(page.more)return false;
    await checkpoint("membership");
  }
  if(progress.stage!=="membership")throw new Error("Manual review: invalid deletion stage");
  await db.runTransaction(async tx=>{
    const [current,member]=await Promise.all([
      tx.get(roomRef),tx.get(roomRef.collection("members").doc(uid))
    ]);
    if(!current.exists){tx.delete(index.ref);return;}
    if(current.get("status")==="active"&&current.get("ownerId")===uid)
      throw new Error("Manual review: user became owner during deletion");
    const status=member.exists?member.get("status"):null;
    const changes={...removeSender(current.data(),uid)};
    if(status==="active"||status==="pending")
      changes[status==="active"?"memberCount":"pendingCount"]=FieldValue.increment(-1);
    if(current.get("status")==="archived"&&current.get("ownerId")===uid)changes.ownerId=null;
    if(Object.keys(changes).length)tx.update(roomRef,changes);
    if(member.exists)tx.delete(member.ref);
    tx.delete(index.ref);
  });
  return true;
}

// Server-only account removal. Successful deletion is not claimed until Auth
// deletion and every room checkpoint have completed.
async function processOneAccountDeletion(db,uid,auth=getAuth()){
  const jobRef=db.collection("accountDeletionJobs").doc(uid);
  const job=await jobRef.get();
  if(!job.exists||!["queued","processing"].includes(job.get("status")))return {status:"skipped"};
  await jobRef.update({status:"processing",attempts:FieldValue.increment(1)});
  const deadline=Date.now()+DELETE_BUDGET_MS;
  const userRooms=db.collection("userRooms").doc(uid);
  const roomIndex=await userRooms.collection("rooms").limit(DELETE_ROOM_BATCH).get();
  for(const item of roomIndex.docs){
    if(Date.now()>=deadline)break;
    if(!await cleanupRoom(db,item,uid,deadline))break;
  }
  const remaining=await userRooms.collection("rooms").limit(1).get();
  if(!remaining.empty){
    await jobRef.update({status:"processing",attempts:0,updatedAt:FieldValue.serverTimestamp()});
    return {status:"processing"};
  }
  await db.recursiveDelete(userRooms);
  // Remove profile, per-device FCM tokens and notification cooldowns together.
  await db.recursiveDelete(db.collection("users").doc(uid));
  await db.recursiveDelete(db.collection("userSafety").doc(uid));
  const reported=await db.collection("safetyReports").where("actorUid","==",uid).limit(DELETE_ROWS+1).get();
  if(!reported.empty){
    const batch=db.batch();
    for(const d of reported.docs.slice(0,DELETE_ROWS))
      batch.update(d.ref,{actorUid:"deleted-account",requestId:FieldValue.delete()});
    await batch.commit();
  }
  if(reported.size>DELETE_ROWS){
    await jobRef.update({status:"processing",attempts:0,updatedAt:FieldValue.serverTimestamp()});
    return {status:"processing"};
  }
  const rateRef=db.collection("safetyReportRateLimits").doc(HASH(uid));
  await rateRef.delete();
  try{await auth.deleteUser(uid);}catch(error){
    if(error.code!=="auth/user-not-found")throw error;
  }
  // Keep a minimal tombstone longer than the maximum Firebase ID-token
  // lifetime, so tokens on other devices cannot recreate account data.
  await jobRef.set({
    status:"completed",completedAt:FieldValue.serverTimestamp(),
    expiresAt:Timestamp.fromMillis(Date.now()+2*60*60*1000),
    attempts:FieldValue.delete(),lastError:FieldValue.delete()
  },{merge:true});
  return {status:"completed"};
}

async function processAccountDeletionQueue(db,auth=getAuth()){
  // Remove only expired tombstones; incomplete jobs stay visible for recovery.
  const old=await db.collection("accountDeletionJobs")
    .where("expiresAt","<=",Timestamp.fromMillis(Date.now()))
    .limit(100).get();
  for(const expired of old.docs){
    if(expired.get("status")==="completed")await expired.ref.delete();
  }
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
