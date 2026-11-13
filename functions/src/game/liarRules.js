"use strict";

const {randomInt}=require("node:crypto");
const {HttpsError}=require("firebase-functions/v2/https");

const TOPICS=Object.freeze(["여행","영화","치킨","커피","바다","축구","도서관","놀이공원"]);
const CHAT_MS=180000;
const VOTE_MS=60000;
const REVOTE_MS=45000;
const GUESS_MS=45000;
const REVEAL_MS=10000;
const fail=message=>{throw new HttpsError("invalid-argument",message);};
const idsOf=session=>Object.keys(session.participants||{});
const has=(object,key)=>Object.prototype.hasOwnProperty.call(object,key);

function prepare({participants},random=randomInt){
  if(!Array.isArray(participants)||participants.length<4||participants.length>10||
     new Set(participants).size!==participants.length){
    throw new HttpsError("failed-precondition","라이어 게임은 4~10명의 고유 참가자가 필요합니다.");
  }
  const liarIndex=random(participants.length);
  const topicIndex=random(TOPICS.length);
  if(!Number.isInteger(liarIndex)||liarIndex<0||liarIndex>=participants.length||
     !Number.isInteger(topicIndex)||topicIndex<0||topicIndex>=TOPICS.length){
    throw new Error("Invalid server random index");
  }
  const topic=TOPICS[topicIndex];
  const privateByUid=Object.fromEntries(participants.map((uid,index)=>
    [uid,index===liarIndex?{role:"liar"}:{role:"citizen",topic}]));
  return {
    phase:"playing",durationMs:CHAT_MS,
    publicState:{prompt:"주제 단어를 직접 말하지 않고 서로 힌트를 나눠 주세요."},
    privateByUid
  };
}

function inspectAssignments(session,assignments){
  const ids=idsOf(session);
  if(ids.length<4||ids.length>10||!assignments ||
     Object.keys(assignments).length!==ids.length){
    throw new HttpsError("failed-precondition","라이어 역할 데이터가 완전하지 않습니다.");
  }
  const liars=ids.filter(uid=>assignments[uid]?.role==="liar");
  const citizens=ids.filter(uid=>assignments[uid]?.role==="citizen");
  const topics=new Set(citizens.map(uid=>assignments[uid]?.topic));
  if(liars.length!==1||citizens.length!==ids.length-1||
     topics.size!==1||!TOPICS.includes([...topics][0])||
     has(assignments[liars[0]],"topic")){
    throw new HttpsError("failed-precondition","라이어 역할 또는 주제의 무결성이 잘못되었습니다.");
  }
  return {liarUid:liars[0],topic:[...topics][0],ids};
}

function validateSubmission({session,actorUid,choice,personalData}){
  if(session.phase!=="voting"||typeof choice!=="string"||
     choice.length>40||!has(session.participants||{},actorUid)){
    fail("현재 단계의 선택이 올바르지 않습니다.");
  }
  const stage=session.publicState?.notice;
  const choices=session.publicState?.options;
  if(!Array.isArray(choices)||!choices.includes(choice))fail("허용되지 않은 선택입니다.");
  if(stage==="guess"){
    if(personalData?.role!=="liar") {
      throw new HttpsError("permission-denied","주제 추리는 라이어만 제출할 수 있습니다.");
    }
    return {topicChoice:choice};
  }
  if(!["vote","revote"].includes(stage)||choice===actorUid||
     !["citizen","liar"].includes(personalData?.role)){
    fail("자신을 투표하거나 이 단계에서 제출할 수 없습니다.");
  }
  return {targetUid:choice};
}

function tally(ballots,choices,ids){
  const scores=Object.fromEntries(choices.map(uid=>[uid,0]));
  for(const voter of ids){
    const selected=ballots?.[voter]?.targetUid;
    if(typeof selected==="string" && selected!==voter && has(scores,selected)){
      scores[selected]++;
    }
  }
  const highest=Math.max(...Object.values(scores));
  const leaders=choices.filter(uid=>scores[uid]===highest);
  return {leaders,scores,highest};
}

function decision(session,history){
  const ids=idsOf(session);
  const first=tally(history?.[1],ids,ids);
  if(first.leaders.length===1){
    return {target:first.leaders[0],round:1,tied:false};
  }
  const second=tally(history?.[2],first.leaders,ids);
  return {target:second.leaders.length===1?second.leaders[0]:null,
    round:2,tied:second.leaders.length!==1};
}

function result(session,history,assignments,memberNames){
  const {liarUid,topic,ids}=inspectAssignments(session,assignments);
  const voted=decision(session,history);
  const guess=history?.[session.round]?.[liarUid]?.topicChoice;
  const caught=!voted.tied&&voted.target===liarUid;
  const liarWon=!caught||guess===topic;
  const scoresByUid=Object.fromEntries(ids.map(uid=>{
    const correctVote=history?.[voted.round]?.[uid]?.targetUid===liarUid;
    const teamWin=liarWon?(uid===liarUid):(uid!==liarUid);
    return [uid,(teamWin?100:0)+(uid!==liarUid&&correctVote?20:0)];
  }));
  const safeName=uid=>{
    const value=memberNames?.[uid];
    return typeof value==="string"&&value.trim()
      ?value.trim().replace(/[\r\n]/g," ").slice(0,24):"친구";
  };
  const method=voted.tied?"재투표 동점":caught?"라이어 적발":"라이어 지목 실패";
  const lines=[
    "라이어: "+safeName(liarUid),
    "비밀 주제: "+topic,
    "판정: "+method,
    caught?"라이어의 주제 답: "+(guess||"미제출"):"주제 추리 기회 없음",
    "승리: "+(liarWon?"라이어":"일반 참가자"),
    "점수: "+ids.map(uid=>safeName(uid)+" "+scoresByUid[uid]+"점").join(", ")
  ];
  return {liarWon,scoresByUid,publishedResult:lines.join("\n")};
}

function advance({session,submissionsByRound={},assignments={},memberNames={}}){
  const ids=idsOf(session);
  const pub=session.publicState||{};
  if(session.phase==="playing"){
    return {phase:"voting",durationMs:VOTE_MS,
      publicState:{prompt:"라이어라고 생각하는 사람을 투표해 주세요.",
        notice:"vote",options:ids}};
  }
  if(session.phase==="voting"){
    const {liarUid}=inspectAssignments(session,assignments);
    if(pub.notice==="guess"){
      const outcome=result(session,submissionsByRound,assignments,memberNames);
      return {phase:"reveal",durationMs:REVEAL_MS,
        publicState:{publishedResult:outcome.publishedResult}};
    }
    if(!["vote","revote"].includes(pub.notice)){
      throw new HttpsError("failed-precondition","지원하지 않는 라이어 투표 단계입니다.");
    }
    const options=pub.options;
    const votes=tally(submissionsByRound[session.round],options,ids);
    if(votes.leaders.length>1 && pub.notice==="vote"){
      return {phase:"voting",round:session.round+1,durationMs:REVOTE_MS,
        publicState:{prompt:"동점 후보만 다시 투표해 주세요.",
          notice:"revote",options:votes.leaders}};
    }
    if(votes.leaders.length===1 && votes.leaders[0]===liarUid){
      return {phase:"voting",round:session.round+1,durationMs:GUESS_MS,
        publicState:{prompt:"라이어에게 마지막 주제 추리 기회가 주어졌습니다.",
          notice:"guess",options:TOPICS.slice()}};
    }
    const outcome=result(session,submissionsByRound,assignments,memberNames);
    return {phase:"reveal",durationMs:REVEAL_MS,
      publicState:{publishedResult:outcome.publishedResult}};
  }
  if(session.phase==="reveal"){
    const outcome=result(session,submissionsByRound,assignments,memberNames);
    return {phase:"finished",publicState:{publishedResult:outcome.publishedResult},
      scoresByUid:outcome.scoresByUid};
  }
  throw new HttpsError("failed-precondition","라이어 게임 단계를 변경할 수 없습니다.");
}

function projectForPlayer({session,personalData}){
  if(session.phase==="finished"||session.phase==="cancelled")return {};
  if(!personalData)return null;
  if(personalData.role==="liar"&&!has(personalData,"topic"))return {role:"liar"};
  if(personalData.role==="citizen"&&TOPICS.includes(personalData.topic)){
    return {role:"citizen",topic:personalData.topic};
  }
  throw new HttpsError("failed-precondition","라이어 역할 정보가 올바르지 않습니다.");
}

module.exports={
  gameId:"liar",rulesVersion:1,TOPICS,CHAT_MS,VOTE_MS,REVOTE_MS,GUESS_MS,REVEAL_MS,
  prepare,validateSubmission,advance,projectForPlayer,inspectAssignments,
  tally,decision,result
};
