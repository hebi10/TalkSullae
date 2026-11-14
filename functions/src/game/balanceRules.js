"use strict";

const {randomInt}=require("node:crypto");
const {HttpsError}=require("firebase-functions/v2/https");

const QUESTIONS=Object.freeze([
 ["평생 여름","평생 겨울"],
 ["친구와 해외여행","혼자 여행"],
 ["인터넷 없이 일주일","게임 없이 일주일"],
 ["미래 보기","과거 바꾸기"],
 ["도시 여행","자연 여행"],
 ["아침형 생활","저녁형 생활"]
]);
const CHOICES=Object.freeze(["A","B"]);
const REALTIME_CHOICE_MS=90000;
const REALTIME_PREDICT_MS=90000;
const ASYNC_MS=86400000;
const REVEAL_MS=10000;

function fail(msg){throw new HttpsError("invalid-argument",msg);}
function ids(session){return Object.keys(session.participants||{});}
function prepare({session,participants},random=randomInt){
  if(!Array.isArray(participants)||participants.length<2||
     participants.length>10||new Set(participants).size!==participants.length){
    throw new HttpsError("failed-precondition","밸런스 예측은 중복 없는 2~10명이 필요합니다.");
  }
  const choiceIndex=random(QUESTIONS.length);
  if(!Number.isInteger(choiceIndex)||choiceIndex<0||choiceIndex>=QUESTIONS.length){
    throw new Error("Invalid server random index");
  }
  return {
    phase:"playing",
    durationMs:session.mode==="async"?ASYNC_MS:REALTIME_CHOICE_MS,
    publicState:{
      prompt:"어느 쪽을 선택하시겠습니까?",
      options:[...QUESTIONS[choiceIndex]],
      notice:"choose"
    },
    privateByUid:{}
  };
}
function validateSubmission({session,actorUid,choice}){
  const players=ids(session);
  if(!players.includes(actorUid))throw new HttpsError("permission-denied","게임 참가자가 아닙니다.");
  if(session.phase==="playing"&&session.publicState?.notice==="choose"){
    if(!CHOICES.includes(choice))fail("A 또는 B 중 선택해 주세요.");
    return {ownChoice:choice};
  }
  if(session.phase==="voting"&&session.publicState?.notice==="predict"){
    if(!choice||typeof choice!=="object"||Array.isArray(choice)||
       Object.getPrototypeOf(choice)!==Object.prototype||
       Object.keys(choice).length!==2 ||
       !Object.hasOwn(choice,"targetUid")||!Object.hasOwn(choice,"prediction")||
       typeof choice.targetUid!=="string"||
       !players.includes(choice.targetUid)||choice.targetUid===actorUid||
       !CHOICES.includes(choice.prediction)){
      fail("예측할 다른 친구와 A/B 선택을 지정해 주세요.");
    }
    return {targetUid:choice.targetUid,prediction:choice.prediction};
  }
  throw new HttpsError("failed-precondition","현재 제출할 수 없는 단계입니다.");
}

function calculate(session,submissionsByPhase,memberNames={}){
  const players=ids(session);
  const own=submissionsByPhase?.playing||{};
  const predicted=submissionsByPhase?.voting||{};
  const safeName=uid=>{
    const name=memberNames?.[uid];
    return typeof name==="string"&&name.trim()
      ?name.trim().replace(/[\r\n]/g," ").slice(0,24):"친구";
  };
  const scoresByUid={};
  const choicesByUid={};
  let a=0,b=0;
  for(const uid of players){
    const choice=own[uid]?.ownChoice;
    if(choice!==undefined&&!CHOICES.includes(choice)){
      throw new HttpsError("failed-precondition","저장된 선택이 잘못되었습니다.");
    }
    if(choice==="A")a++;
    if(choice==="B")b++;
    choicesByUid[uid]=choice||null;
  }
  for(const uid of players){
    const guess=predicted[uid];
    if(guess!==undefined&&(typeof guess?.targetUid!=="string"||
       !players.includes(guess.targetUid)||guess.targetUid===uid||
       !CHOICES.includes(guess.prediction))){
      throw new HttpsError("failed-precondition","서버 예측 기록이 잘못되었습니다.");
    }
    scoresByUid[uid]=(choicesByUid[uid]?20:0)+
      (guess&&choicesByUid[guess.targetUid]===guess.prediction?100:0);
  }
  const lines=[
    "A: "+session.publicState.options[0]+" ("+a+"명)",
    "B: "+session.publicState.options[1]+" ("+b+"명)",
    ...players.map(uid=>{
      const chosen=choicesByUid[uid]||"미제출";
      const prediction=predicted[uid];
      const truth=prediction?.targetUid?choicesByUid[prediction.targetUid]:null;
      const hit=prediction&&truth===prediction.prediction?"예측 적중":"예측 실패/기권";
      return safeName(uid)+": "+chosen+" / "+hit+" / "+scoresByUid[uid]+"점";
    })
  ];
  return {scoresByUid,publishedResult:lines.join("\n")};
}
function advance({session,submissionsByPhase={},memberNames={}}){
  const pub=session.publicState;
  if(session.phase==="playing"){
    return {
      phase:"voting",
      durationMs:session.mode==="async"?ASYNC_MS:REALTIME_PREDICT_MS,
      publicState:{prompt:pub.prompt,options:pub.options,notice:"predict"}
    };
  }
  if(session.phase==="voting"){
    const outcome=calculate(session,submissionsByPhase,memberNames);
    return {phase:"reveal",durationMs:REVEAL_MS,
      publicState:{...pub,publishedResult:outcome.publishedResult}};
  }
  if(session.phase==="reveal"){
    const outcome=calculate(session,submissionsByPhase,memberNames);
    return {phase:"finished",publicState:pub,scoresByUid:outcome.scoresByUid};
  }
  throw new HttpsError("failed-precondition","밸런스 게임 단계를 변경할 수 없습니다.");
}
function projectForPlayer({session}){
  if(["finished","cancelled"].includes(session.phase))return {};
  return {};
}
module.exports={
  gameId:"balance",rulesVersion:1,QUESTIONS,CHOICES,
  REALTIME_CHOICE_MS,REALTIME_PREDICT_MS,ASYNC_MS,REVEAL_MS,
  prepare,validateSubmission,calculate,advance,projectForPlayer
};
