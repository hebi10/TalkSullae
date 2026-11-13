"use strict";

const {randomInt}=require("node:crypto");
const {HttpsError}=require("firebase-functions/v2/https");

const WORDS=Object.freeze([
  "치킨","여행","커피","축구","초콜릿","영화","도서관","고양이",
  "바다","비행기","버스","사진","운동","노래","과자"
]);
const CHAT_MS=180000,REVEAL_MS=10000;

function shuffle(input,random=randomInt){
  const a=[...input];
  for(let i=a.length-1;i>0;i--){
    const j=random(i+1);
    if(!Number.isInteger(j)||j<0||j>i)throw Error("Invalid server RNG");
    [a[i],a[j]]=[a[j],a[i]];
  }
  return a;
}

function prepare({participants},random=randomInt){
  if(!Array.isArray(participants)||participants.length<3||participants.length>10||
     new Set(participants).size!==participants.length){
    throw new HttpsError("failed-precondition","금지어 채팅은 중복 없는 3~10명이 필요합니다.");
  }
  const assigned=shuffle(WORDS,random).slice(0,participants.length);
  const privateByUid=Object.fromEntries(participants.map((uid,i)=>
    [uid,{forbiddenWord:assigned[i]}]));
  return {
    phase:"playing",durationMs:CHAT_MS,
    publicState:{prompt:"자신의 금지어는 모릅니다. 다른 친구에게 말을 유도해 보세요.",
      eliminatedUids:[]},
    privateByUid
  };
}

function normalized(text){
  if(typeof text!=="string")return "";
  return text.normalize("NFKC").toLocaleLowerCase("ko-KR")
    .replace(/[\s\p{P}\p{S}]/gu,"");
}
function isForbidden(text,word){
  const body=normalized(text),needle=normalized(word);
  return needle.length>0&&body.includes(needle);
}
function inspectAssignments(session,assignments){
  const ids=Object.keys(session.participants||{});
  if(ids.length<3||ids.length>10||!assignments||
     Object.keys(assignments).length!==ids.length ||
     Object.keys(assignments).some(uid=>!ids.includes(uid)) ||
     ids.some(uid=>typeof assignments[uid]?.forbiddenWord!=="string"||
       !WORDS.includes(assignments[uid].forbiddenWord))){
    throw new HttpsError("failed-precondition","금지어 배정 데이터가 올바르지 않습니다.");
  }
  if(new Set(ids.map(uid=>assignments[uid].forbiddenWord)).size!==ids.length){
    throw new HttpsError("failed-precondition","금지어가 중복 배정됐습니다.");
  }
  return ids;
}
function requireEliminations(session){
  const list=session.publicState?.eliminatedUids;
  const ids=Object.keys(session.participants||{});
  if(!Array.isArray(list)||list.some(uid=>typeof uid!=="string"||!ids.includes(uid))||
     new Set(list).size!==list.length){
    throw new HttpsError("failed-precondition","탈락 기록의 무결성을 확인할 수 없습니다.");
  }
  return list;
}
function validateSubmission(){
  throw new HttpsError("failed-precondition","금지어 채팅에는 별도 투표가 없습니다.");
}
function score(session){
  const ids=Object.keys(session.participants||{});
  const eliminated=requireEliminations(session);
  return Object.fromEntries(ids.map(uid=>[uid,eliminated.includes(uid)?0:100]));
}
function advance({session,assignments={},memberNames={}}){
  inspectAssignments(session,assignments);
  if(session.phase==="playing"){
    const eliminated=requireEliminations(session);
    const scores=score(session);
    const lines=Object.keys(session.participants).map(uid=>{
      const raw=memberNames[uid];
      const name=typeof raw==="string"&&raw.trim()?
        raw.trim().replace(/[\r\n]/g," ").slice(0,24):"친구";
      const status=eliminated.includes(uid)?"탈락":"생존";
      return name+" · "+status+" · "+scores[uid]+"점";
    });
    return {phase:"reveal",durationMs:REVEAL_MS,
      publicState:{prompt:session.publicState.prompt,eliminatedUids:eliminated,
        publishedResult:lines.join("\n")}};
  }
  if(session.phase==="reveal"){
    return {phase:"finished",publicState:session.publicState,
      scoresByUid:score(session)};
  }
  throw new HttpsError("failed-precondition","금지어 게임 단계가 올바르지 않습니다.");
}
function projectForPlayer({session,actorUid,assignments}){
  if(session.phase==="finished"||session.phase==="cancelled")return {};
  if(session.phase==="waiting"||session.phase==="ready")return null;
  inspectAssignments(session,assignments);
  if(!Object.hasOwn(session.participants||{},actorUid)){
    throw new HttpsError("permission-denied","참가자만 금지어를 볼 수 있습니다.");
  }
  const others=Object.keys(session.participants).filter(uid=>uid!==actorUid)
    .map(uid=>({uid,word:assignments[uid].forbiddenWord}));
  return {otherWords:others,eliminated:requireEliminations(session).includes(actorUid)};
}

module.exports={
  gameId:"forbidden",rulesVersion:1,WORDS,CHAT_MS,REVEAL_MS,
  prepare,validateSubmission,advance,projectForPlayer,
  inspectAssignments,requireEliminations,normalized,isForbidden,score
};
