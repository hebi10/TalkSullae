"use strict";

const {HttpsError}=require("firebase-functions/v2/https");
const {authUid,roomIdValue}=require("../policy");
const {aggregateMyCareer}=require("./careerService");
const {submissionId}=require("./sessionService");

const RECENT_LIMIT=30;
const GAME_IDS=["identity","liar","forbidden","balance"];
const SESSION_ID=/^[a-f0-9]{64}$/;

function parseRequest(data,uid){
  if(!data||typeof data!=="object"||Array.isArray(data)||
     Object.keys(data).some(key=>!["roomId","targetUid","includeComparison"].includes(key))||
     (Object.hasOwn(data,"includeComparison")&&typeof data.includeComparison!=="boolean")||
     typeof data.targetUid!=="string"||!data.targetUid.trim()||data.targetUid.length>128||
     data.targetUid.includes("/")||[".",".."].includes(data.targetUid)||data.targetUid===uid){
    throw new HttpsError("invalid-argument","같은 방의 다른 친구를 선택해 주세요.");
  }
  return {roomId:roomIdValue(data.roomId),targetUid:data.targetUid,includeComparison:data.includeComparison!==false};
}

function emptyComparison(){
  return {sharedGames:0,myHigher:0,friendHigher:0,ties:0,
    games:Object.fromEntries(GAME_IDS.map(id=>[id,0])),
    myIdentityAttempts:0,myIdentityHits:0,friendIdentityAttempts:0,friendIdentityHits:0,
    balanceCompared:0,balanceMatched:0};
}

function countPair(out,gameId,myScore,friendScore){
  if(!GAME_IDS.includes(gameId)||![myScore,friendScore].every(v=>Number.isSafeInteger(v)&&v>=0&&v<=10000))return false;
  out.sharedGames++;
  out.games[gameId]++;
  if(myScore>friendScore)out.myHigher++;
  else if(friendScore>myScore)out.friendHigher++;
  else out.ties++;
  return true;
}

function countIdentity(out,myUid,friendUid,myAlias,friendAlias,myMatches,friendMatches){
  if(typeof myAlias!=="string"||!myAlias||typeof friendAlias!=="string"||!friendAlias||myAlias===friendAlias)return;
  if(myMatches&&typeof myMatches==="object"&&Object.hasOwn(myMatches,friendAlias)&&typeof myMatches[friendAlias]==="string"){
    out.myIdentityAttempts++;
    if(myMatches[friendAlias]===friendUid)out.myIdentityHits++;
  }
  if(friendMatches&&typeof friendMatches==="object"&&Object.hasOwn(friendMatches,myAlias)&&typeof friendMatches[myAlias]==="string"){
    out.friendIdentityAttempts++;
    if(friendMatches[myAlias]===myUid)out.friendIdentityHits++;
  }
}

function countBalance(out,mine,theirs){
  if(!["A","B"].includes(mine)||!["A","B"].includes(theirs))return;
  out.balanceCompared++;
  if(mine===theirs)out.balanceMatched++;
}

async function getRoomFriendRecords(db,request){
  const uid=authUid(request);
  const {roomId,targetUid,includeComparison}=parseRequest(request.data,uid);
  const roomRef=db.collection("rooms").doc(roomId);
  const myMember=roomRef.collection("members").doc(uid);
  const friendMember=roomRef.collection("members").doc(targetUid);
  const statsRef=roomRef.collection("playerStats").doc(targetUid);
  const ownHistory=roomRef.collection("playerStats").doc(uid).collection("games");
  const friendHistory=statsRef.collection("games");
  // All reads occur in one read-only transaction, with the same membership
  // snapshot. Raw assignments, votes, names and individual game IDs are never returned.
  return db.runTransaction(async tx=>{
    const [room,mine,theirs,myDeletion,theirDeletion]=await Promise.all([
      tx.get(roomRef),tx.get(myMember),tx.get(friendMember),
      tx.get(db.collection("accountDeletionJobs").doc(uid)),
      tx.get(db.collection("accountDeletionJobs").doc(targetUid))
    ]);
    if(!room.exists||room.get("status")!=="active"||!mine.exists||mine.get("status")!=="active"||
       !theirs.exists||theirs.get("status")!=="active"||myDeletion.exists||theirDeletion.exists){
      throw new HttpsError("permission-denied","현재 같은 방의 활성 멤버 전적만 확인할 수 있습니다.");
    }
    const stats=await tx.get(statsRef);
    const aggregate=aggregateMyCareer([stats.exists?stats.data():{}]);
    const friend={totalGames:aggregate.totalGames,totalWins:aggregate.totalWins,
      totalSeasonPoints:aggregate.totalSeasonPoints,bestStreak:aggregate.bestStreak,
      games:aggregate.games,achievements:aggregate.achievements};
    if(!includeComparison)return {friend,comparison:emptyComparison(),reviewedGames:0,
      capped:false,recentLimit:RECENT_LIMIT,comparisonLoaded:false};
    const history=await tx.get(ownHistory.orderBy("settledAt","desc").limit(RECENT_LIMIT+1));
    const recent=history.docs.slice(0,RECENT_LIMIT).filter(doc=>SESSION_ID.test(doc.id));
    const paired=await Promise.all(recent.map(doc=>tx.get(friendHistory.doc(doc.id))));
    const candidates=recent.flatMap((doc,i)=>paired[i].exists?[{mine:doc,theirs:paired[i]}]:[]);
    const sessions=await Promise.all(candidates.map(pair=>tx.get(roomRef.collection("gameSessions").doc(pair.mine.id))));
    const comparison=emptyComparison();
    for(let i=0;i<candidates.length;i++){
      const pair=candidates[i],session=sessions[i];
      const gameId=pair.mine.get("gameId"),round=session.get("round");
      const participants=session.get("participants");
      if(!session.exists||session.get("phase")!=="finished"||session.get("roomId")!==roomId||
         session.get("sessionId")!==pair.mine.id||session.get("gameId")!==gameId||
         pair.theirs.get("gameId")!==gameId||!participants||
         !Object.hasOwn(participants,uid)||!Object.hasOwn(participants,targetUid)||
         !Number.isSafeInteger(round)||round<1||round>99)continue;
      if(!countPair(comparison,gameId,pair.mine.get("rawScore"),pair.theirs.get("rawScore")))continue;
      const ref=session.ref;
      if(gameId==="identity"){
        const [a,b,voteA,voteB]=await Promise.all([
          tx.get(ref.collection("privateAssignments").doc(uid)),
          tx.get(ref.collection("privateAssignments").doc(targetUid)),
          tx.get(ref.collection("privateSubmissions").doc(submissionId(uid,round,"voting"))),
          tx.get(ref.collection("privateSubmissions").doc(submissionId(targetUid,round,"voting")))
        ]);
        countIdentity(comparison,uid,targetUid,a.get("data")?.alias,b.get("data")?.alias,
          voteA.exists&&voteA.get("actorUid")===uid?voteA.get("data")?.matches:null,
          voteB.exists&&voteB.get("actorUid")===targetUid?voteB.get("data")?.matches:null);
      }else if(gameId==="balance"){
        const [a,b]=await Promise.all([
          tx.get(ref.collection("privateSubmissions").doc(submissionId(uid,round,"playing"))),
          tx.get(ref.collection("privateSubmissions").doc(submissionId(targetUid,round,"playing")))
        ]);
        if(a.exists&&b.exists&&a.get("actorUid")===uid&&b.get("actorUid")===targetUid){
          countBalance(comparison,a.get("data")?.ownChoice,b.get("data")?.ownChoice);
        }
      }
    }
    // Do not expose another member’s private votes or choices through a tiny sample.
    const identitySuppressed=comparison.friendIdentityAttempts<3;
    const balanceSuppressed=comparison.balanceCompared<3;
    if(identitySuppressed){comparison.friendIdentityAttempts=0;comparison.friendIdentityHits=0;}
    if(balanceSuppressed){comparison.balanceCompared=0;comparison.balanceMatched=0;}
    return {friend,comparison,reviewedGames:Math.min(history.size,RECENT_LIMIT),
      capped:history.size>RECENT_LIMIT,recentLimit:RECENT_LIMIT,comparisonLoaded:true,
      identitySuppressed,balanceSuppressed};
  },{readOnly:true});
}

module.exports={RECENT_LIMIT,parseRequest,emptyComparison,countPair,countIdentity,countBalance,getRoomFriendRecords};
