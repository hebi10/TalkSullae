"use strict";

const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");

const GAME_MAX_SCORES=Object.freeze({
  liar:120,forbidden:100,balance:120
});
const GAMES=new Set(["identity","liar","forbidden","balance"]);
const SEASON_RE=/^20\d{2}-(0[1-9]|1[0-2])$/;
const KST_OFFSET_MS=9*60*60*1000;

function currentSeason(nowMillis=Date.now()){
  if(!Number.isSafeInteger(nowMillis)||nowMillis<0){
    throw new Error("Invalid settlement timestamp");
  }
  return new Date(nowMillis+KST_OFFSET_MS).toISOString().slice(0,7);
}
function seasonStatus(season,nowMillis=Date.now()){
  if(!SEASON_RE.test(season))throw new HttpsError("invalid-argument","시즌 ID가 올바르지 않습니다.");
  const current=currentSeason(nowMillis);
  if(season>current)throw new HttpsError("failed-precondition","미래 시즌은 조회할 수 없습니다.");
  return season<current?"closed":"active";
}
function maxRawScore(gameId,count){
  if(!GAMES.has(gameId)||!Number.isSafeInteger(count)||count<2||count>10){
    throw new HttpsError("failed-precondition","정산할 게임/참가 인원이 올바르지 않습니다.");
  }
  if(gameId==="identity")return (count-1)*100+Math.min((count-1)*10,50);
  return GAME_MAX_SCORES[gameId];
}
function normalizedPoints(gameId,score,count,win){
  const max=maxRawScore(gameId,count);
  if(!Number.isSafeInteger(score)||score<0||score>max){
    throw new HttpsError("failed-precondition","게임 정산 점수가 허용 범위를 벗어났습니다.");
  }
  return 5+Math.round(score/max*100)+(win?10:0);
}
function winnerMap(gameId,scoreByUid,participants){
  const high=Math.max(...participants.map(uid=>scoreByUid[uid]));
  return Object.fromEntries(participants.map(uid=>{
    const score=scoreByUid[uid];
    return [uid,gameId==="identity"?score>0&&score===high:score>=100];
  }));
}

function safeNumber(value,label){
  if(value===undefined||value===null)return 0;
  if(!Number.isSafeInteger(value)||value<0||value>10000000){
    throw new HttpsError("failed-precondition",label+" 누적 정보가 잘못되었습니다.");
  }
  return value;
}
function achievementsAfter(previous,next){
  const result={first_game:true};
  if(next.totalGames>=10)result.games_10=true;
  if(next.totalWins>=5)result.wins_5=true;
  if(next.currentStreak>=3)result.streak_3=true;
  if(next.bestStreak>=5)result.streak_5=true;
  if(next.liarWins>=5)result.liar_wins_5=true;
  if(next.identityWins>=10)result.identity_wins_10=true;
  // Already earned achievements remain set by the Firestore merge.
  return result;
}

// Reads all per-player stats BEFORE scheduling any transactional write. Each
// settlement is tied to results/final and a create-only ledger in the same
// transaction: replayed requests cannot pay out points a second time.
async function prepareSettlement(tx,roomRef,{
  gameId,sessionId,settlementId,scoreByUid,participants,nowMillis
}){
  if(!GAMES.has(gameId)||typeof sessionId!=="string"||
     typeof settlementId!=="string"||!Array.isArray(participants)||
     participants.length<2||participants.length>10||
     new Set(participants).size!==participants.length||
     Object.keys(scoreByUid||{}).length!==participants.length||
     participants.some(uid=>!Object.hasOwn(scoreByUid,uid))){
    throw new HttpsError("failed-precondition","정산 데이터가 올바르지 않습니다.");
  }
  const seasonId=currentSeason(nowMillis);
  const seasonRef=roomRef.collection("seasons").doc(seasonId);
  const ledgers=roomRef.collection("recordSettlements").doc(sessionId);
  const summaryRef=roomRef.collection("stats").doc("summary");
  const memberRefs=participants.map(uid=>roomRef.collection("playerStats").doc(uid));
  const previous=await Promise.all(memberRefs.map(ref=>tx.get(ref)));
  const wins=winnerMap(gameId,scoreByUid,participants);
  const settlements=participants.map((uid,index)=>{
    const rawScore=scoreByUid[uid],win=wins[uid],snapshot=previous[index];
    const previousData=snapshot.exists?snapshot.data():{};
    const totalGames=safeNumber(previousData.totalGames,"전적")+1;
    const totalWins=safeNumber(previousData.totalWins,"승리")+Number(win);
    const streak=win?safeNumber(previousData.currentStreak,"연승")+1:0;
    const bestStreak=Math.max(streak,safeNumber(previousData.bestStreak,"연승"));
    const previousGames=previousData.games?.[gameId]||{};
    const gameWins=safeNumber(previousGames.wins,"게임 승리")+Number(win);
    const liarWins=safeNumber(previousData.games?.liar?.wins,"라이어 승리")+
      Number(gameId==="liar"&&win);
    const identityWins=safeNumber(previousData.games?.identity?.wins,"정체 승리")+
      Number(gameId==="identity"&&win);
    const seasonPoints=normalizedPoints(gameId,rawScore,participants.length,win);
    const achievements=achievementsAfter(previousData,{
      totalGames,totalWins,currentStreak:streak,bestStreak,liarWins,identityWins
    });
    return {uid,rawScore,win,seasonPoints,streak,bestStreak,achievements};
  });
  const settledAt=Timestamp.fromMillis(nowMillis);
  return ()=>{
    tx.create(ledgers,{
      settlementId,gameId,seasonId,participantCount:participants.length,
      settledAt
    });
    tx.set(summaryRef,{
      totalGames:FieldValue.increment(1),
      lastGameAt:settledAt,games:{[gameId]:FieldValue.increment(1)}
    },{merge:true});
    tx.set(seasonRef,{
      seasonId,lastGameAt:settledAt,
      gameCount:FieldValue.increment(1)
    },{merge:true});
    for(const item of settlements){
      const uid=item.uid;
      const playerRef=roomRef.collection("playerStats").doc(uid);
      tx.set(playerRef,{
        totalGames:FieldValue.increment(1),
        totalWins:FieldValue.increment(item.win?1:0),
        totalRawScore:FieldValue.increment(item.rawScore),
        totalSeasonPoints:FieldValue.increment(item.seasonPoints),
        currentStreak:item.streak,bestStreak:item.bestStreak,
        lastPlayedAt:settledAt,
        games:{[gameId]:{
          plays:FieldValue.increment(1),
          wins:FieldValue.increment(item.win?1:0),
          rawScore:FieldValue.increment(item.rawScore)
        }},
        achievements:item.achievements
      },{merge:true});
      tx.set(seasonRef.collection("scores").doc(uid),{
        uid,seasonId,
        seasonPoints:FieldValue.increment(item.seasonPoints),
        gamesPlayed:FieldValue.increment(1),
        wins:FieldValue.increment(item.win?1:0),
        lastPlayedAt:settledAt
      },{merge:true});
      tx.create(playerRef.collection("games").doc(sessionId),{
        sessionId,gameId,seasonId,rawScore:item.rawScore,
        seasonPoints:item.seasonPoints,
        win:item.win,settledAt
      });
    }
    tx.create(roomRef.collection("recordEvents").doc(sessionId),{
      sessionId,gameId,seasonId,
      participantCount:participants.length,settledAt
    });
  };
}

module.exports={
  currentSeason,seasonStatus,maxRawScore,normalizedPoints,winnerMap,
  achievementsAfter,prepareSettlement
};
