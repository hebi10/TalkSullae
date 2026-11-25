"use strict";

const {HttpsError}=require("firebase-functions/v2/https");
const {FieldPath}=require("firebase-admin/firestore");
const {authUid}=require("../policy");

const MAX_ROOMS=50;
const GAME_IDS=["identity","liar","forbidden","balance"];

function count(value,label){
  if(value==null)return 0;
  if(!Number.isSafeInteger(value)||value<0){
    throw new HttpsError("failed-precondition",label+"의 데이터가 올바르지 않습니다.");
  }
  return value;
}

function aggregateMyCareer(records){
  const result={
    roomCount:0,totalGames:0,totalWins:0,bestStreak:0,
    totalSeasonPoints:0,achievements:[],
    games:Object.fromEntries(GAME_IDS.map(id=>[id,{plays:0,wins:0}]))
  };
  const achievements=new Set();
  for(const record of records){
    result.roomCount++;
    result.totalGames+=count(record.totalGames,"경기 수");
    result.totalWins+=count(record.totalWins,"승리");
    result.totalSeasonPoints+=count(record.totalSeasonPoints,"시즌 점수");
    result.bestStreak=Math.max(result.bestStreak,count(record.bestStreak,"최고 연승"));
    if(!Number.isSafeInteger(result.totalGames)||!Number.isSafeInteger(result.totalWins)||
       !Number.isSafeInteger(result.totalSeasonPoints)){
      throw new HttpsError("failed-precondition","누적 기록을 확인할 수 없습니다.");
    }
    for(const id of GAME_IDS){
      const data=record.games?.[id]||{};
      result.games[id].plays+=count(data.plays,id+" 경기");
      result.games[id].wins+=count(data.wins,id+" 승리");
    }
    for(const [id,obtained] of Object.entries(record.achievements||{})){
      if(obtained===true && /^[a-z0-9_]{1,60}$/.test(id))achievements.add(id);
    }
  }
  result.achievements=[...achievements].sort();
  return result;
}

const RETAINED_PAGE_SIZE=25;
const RETAINED_STATES=["active","left","removed","archived"];

function careerRequest(value){
  const body=value??{};
  if(typeof body!=="object"||Array.isArray(body))throw new HttpsError("invalid-argument","자신의 전적만 조회할 수 있습니다.");
  if(Object.keys(body).length===0)return {retained:false,cursor:null};
  if(body.scope!=="retained"||Object.keys(body).some(key=>!["scope","cursor"].includes(key))||
    (Object.hasOwn(body,"cursor")&&(typeof body.cursor!=="string"||!/^[^/\u0000-\u001f]{1,128}$/u.test(body.cursor)||
    [".",".."].includes(body.cursor)))){
    throw new HttpsError("invalid-argument","개인 전적 조회 범위가 올바르지 않습니다.");
  }
  return {retained:true,cursor:body.cursor??null};
}

async function getMyCareer(db,request){
  const uid=authUid(request),{retained,cursor}=careerRequest(request.data);
  return db.runTransaction(async tx=>{
    const deletion=await tx.get(db.collection("accountDeletionJobs").doc(uid));
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 전적을 조회할 수 없습니다.");
    const index=db.collection("userRooms").doc(uid).collection("rooms");
    let query=retained?index.where("status","in",RETAINED_STATES).orderBy(FieldPath.documentId())
      .limit(RETAINED_PAGE_SIZE+1):index.where("status","==","active").limit(MAX_ROOMS+1);
    if(retained&&cursor!==null)query=query.startAfter(cursor);
    const snapshot=await tx.get(query);
    if(!retained&&snapshot.size>MAX_ROOMS)throw new HttpsError("resource-exhausted",
      "참여한 방이 많아 전적을 모두 합산할 수 없습니다. 방별 기록을 확인해 주세요.");
    const docs=retained?snapshot.docs.slice(0,RETAINED_PAGE_SIZE):snapshot.docs;
    const verified=await Promise.all(docs.map(async row=>{
      const room=db.collection("rooms").doc(row.id);
      const [roomDoc,member,stats]=await Promise.all([
        tx.get(room),tx.get(room.collection("members").doc(uid)),tx.get(room.collection("playerStats").doc(uid))
      ]);
      const status=row.get("status");
      if(!roomDoc.exists||!member.exists||member.get("status")!==status)return null;
      const active=status==="active"&&roomDoc.get("status")==="active";
      if(!active&&(!retained||!RETAINED_STATES.includes(status)||status==="active"||
        !["active","archived"].includes(roomDoc.get("status"))||!stats.exists))return null;
      // Only the authenticated user's own counters are projected; no room names,
      // other players' records, individual game messages or private ballots.
      return {active,data:stats.exists?stats.data():{}};
    }));
    const valid=verified.filter(Boolean),summary=aggregateMyCareer(valid.map(item=>item.data));
    if(!retained)return summary;
    return {...summary,recordedRoomCount:summary.roomCount,
      roomCount:valid.filter(item=>item.active).length,scope:"retained",
      nextCursor:snapshot.size>RETAINED_PAGE_SIZE?docs.at(-1).id:null};
  },{readOnly:true});
}

module.exports={MAX_ROOMS,RETAINED_PAGE_SIZE,GAME_IDS,aggregateMyCareer,getMyCareer,careerRequest};
