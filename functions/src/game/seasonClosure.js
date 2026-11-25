"use strict";

const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid,roomIdValue}=require("../policy");
const SEASON=/^20\d{2}-(0[1-9]|1[0-2])$/;
const OFFSET=9*60*60*1000;
const GRACE_MS=10*60*1000;

function seasonEnd(season){
  if(typeof season!=="string"||!SEASON.test(season))
    throw new HttpsError("invalid-argument","시즌 ID가 올바르지 않습니다.");
  const [year,month]=season.split("-").map(Number);
  return Date.UTC(year,month,1)-OFFSET;
}
function closeAt(season){return seasonEnd(season)+GRACE_MS;}
function seasonQueueRef(db,roomId,season){
  return db.collection("seasonCloseQueue").doc(roomId+"_"+season);
}
function counter(value){
  if(!Number.isSafeInteger(value)||value<0)throw new HttpsError("failed-precondition","시즌 점수 기록을 확인할 수 없습니다.");
  return value;
}

// Transactional snapshot of only aggregate fields; winner identities remain
// in the existing score rows and are not duplicated into the closure record.
async function prepareClosure(tx,seasonRef,now,known){
  const season=known??await tx.get(seasonRef);
  if(!season.exists)return {status:"empty"};
  if(season.get("seasonId")!==seasonRef.id)
    throw new HttpsError("failed-precondition","시즌 문서가 일치하지 않습니다.");
  const previous=season.get("finalResult");
  if(previous!==undefined){
    if(!previous||previous.version!==1||previous.status!=="closed"||
      !Number.isSafeInteger(previous.winnerCount)||previous.winnerCount<0||
      !Number.isSafeInteger(previous.gameCount)||previous.gameCount<0||
      !Number.isSafeInteger(previous.closedAtMillis)||previous.closedAtMillis<closeAt(seasonRef.id)||
      (previous.winnerCount===0?previous.winningScore!==null:
        !Number.isSafeInteger(previous.winningScore)||previous.winningScore<=0))
      throw new HttpsError("failed-precondition","시즌 마감 기록이 올바르지 않습니다.");
    return previous;
  }
  if(now<seasonEnd(seasonRef.id))return {status:"active"};
  if(now<closeAt(seasonRef.id))return {status:"closing"};
  const scores=seasonRef.collection("scores");
  const top=await tx.get(scores.orderBy("seasonPoints","desc").limit(1));
  const highest=top.empty?0:counter(top.docs[0].get("seasonPoints"));
  const winnerCount=highest>0?counter((await tx.get(scores.where("seasonPoints","==",highest).count())).data().count):0;
  return {version:1,status:"closed",winningScore:winnerCount>0?highest:null,winnerCount,
    gameCount:counter(season.get("gameCount")??0),closedAtMillis:now};
}

async function closeRoomSeason(db,roomId,seasonId,now=Date.now(),uid=null){
  seasonEnd(seasonId);
  const roomRef=db.collection("rooms").doc(roomId),ref=roomRef.collection("seasons").doc(seasonId);
  const queue=seasonQueueRef(db,roomId,seasonId);
  return db.runTransaction(async tx=>{
    const [room,queued]=await Promise.all([tx.get(roomRef),tx.get(queue)]);
    if(uid!==null){
      const [member,deletion]=await Promise.all([
        tx.get(roomRef.collection("members").doc(uid)),
        tx.get(db.collection("accountDeletionJobs").doc(uid))
      ]);
      if(!room.exists||room.get("status")!=="active"||!member.exists||
        member.get("status")!=="active"||deletion.exists)
        throw new HttpsError("permission-denied","현재 같은 방 멤버만 시즌 기록을 조회할 수 있습니다.");
    }
    if(!room.exists){if(queued.exists)tx.delete(queue);return {status:"empty"};}
    const season=await tx.get(ref);
    const result=await prepareClosure(tx,ref,now,season);
    if(result.status==="closed"&&!season.get("finalResult"))
      tx.update(ref,{finalResult:result});
    if(queued.exists&&(result.status==="closed"||result.status==="empty"))tx.delete(queue);
    return result;
  });
}

async function getRoomSeasonClosure(db,request,now=Date.now){
  const uid=authUid(request),body=request.data;
  if(!body||typeof body!=="object"||Array.isArray(body)||Object.keys(body).length!==2||
     Object.keys(body).some(key=>!["roomId","seasonId"].includes(key)))
    throw new HttpsError("invalid-argument","시즌 조회 요청이 올바르지 않습니다.");
  return closeRoomSeason(db,roomIdValue(body.roomId),body.seasonId,now(),uid);
}

async function closeDueSeasons(db,now=Date.now){
  const at=now();
  const due=await db.collection("seasonCloseQueue").where("closeAt","<=",Timestamp.fromMillis(at))
    .orderBy("closeAt").limit(20).get();
  const summary={closed:0,empty:0,failed:0};
  for(const row of due.docs){
    try{
      const roomId=roomIdValue(row.get("roomId")),season=row.get("seasonId");
      if(row.id!==seasonQueueRef(db,roomId,season).id||at<closeAt(season))
        throw new HttpsError("failed-precondition","시즌 마감 대기열이 일치하지 않습니다.");
      const result=await closeRoomSeason(db,roomId,season,at);
      if(result.status==="closed"||result.status==="empty")summary[result.status]++;
      else summary.failed++;
    }catch(_){summary.failed++;}
  }
  return summary;
}

// Called by account cleanup: capture the historical winning score before
// removing any individual score from a past season.
async function removeSeasonScore(db,seasonRef,uid,now=Date.now()){
  return db.runTransaction(async tx=>{
    const season=await tx.get(seasonRef);
    const scoreRef=seasonRef.collection("scores").doc(uid);
    const score=await tx.get(scoreRef);
    if(!score.exists)return true;
    if(season.exists&&SEASON.test(seasonRef.id)&&season.get("seasonId")===seasonRef.id){
      const result=await prepareClosure(tx,seasonRef,now,season);
      if(result.status==="closing")return false;
      if(result.status==="closed"&&!season.get("finalResult")){
        tx.update(seasonRef,{finalResult:result});
        const roomId=seasonRef.parent.parent.id;
        tx.delete(seasonQueueRef(db,roomId,seasonRef.id));
      }
    }
    tx.delete(scoreRef);
    return true;
  });
}

module.exports={seasonEnd,closeAt,seasonQueueRef,prepareClosure,closeRoomSeason,getRoomSeasonClosure,closeDueSeasons,removeSeasonScore};
