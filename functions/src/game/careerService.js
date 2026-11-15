"use strict";

const {HttpsError}=require("firebase-functions/v2/https");
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

async function getMyCareer(db,request){
  const uid=authUid(request);
  const body=request.data??{};
  if(typeof body!=="object"||Array.isArray(body)||Object.keys(body).length){
    throw new HttpsError("invalid-argument","자신의 전적만 조회할 수 있습니다.");
  }
  const deletion=await db.collection("accountDeletionJobs").doc(uid).get();
  if(deletion.exists){
    throw new HttpsError("permission-denied","계정 삭제 요청 중에는 전적을 조회할 수 없습니다.");
  }
  const indices=await db.collection("userRooms").doc(uid)
    .collection("rooms").limit(MAX_ROOMS+1).get();
  if(indices.size>MAX_ROOMS){
    throw new HttpsError("resource-exhausted",
      "참여한 방이 많아 전적을 모두 합산할 수 없습니다. 방별 기록을 확인해 주세요.");
  }
  const active=indices.docs.filter(row=>row.get("status")==="active");
  const verified=await Promise.all(active.map(async row=>{
    const room=db.collection("rooms").doc(row.id);
    const [roomDoc,member,stats]=await Promise.all([
      room.get(),room.collection("members").doc(uid).get(),
      room.collection("playerStats").doc(uid).get()
    ]);
    if(!roomDoc.exists||roomDoc.get("status")!=="active"||
       !member.exists||member.get("status")!=="active")return null;
    return stats.exists?stats.data():{};
  }));
  return aggregateMyCareer(verified.filter(Boolean));
}

module.exports={MAX_ROOMS,GAME_IDS,aggregateMyCareer,getMyCareer};
