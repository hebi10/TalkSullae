"use strict";

const {randomInt} = require("node:crypto");
const {HttpsError} = require("firebase-functions/v2/https");

const ALIASES = Object.freeze([
  "고양이","토끼","부엉이","여우","강아지",
  "곰","사슴","호랑이","펭귄","코알라"
]);
const PROMPTS = Object.freeze([
  "우리 중 무인도에서 가장 오래 살아남을 사람은?",
  "우리 중 여행에서 길을 잃을 것 같은 사람은?",
  "우리 중 비밀을 가장 잘 지킬 것 같은 사람은?",
  "다시 학생이 된다면 가장 먼저 하고 싶은 일은?",
  "처음 만났을 때 친구들의 첫인상은 어땠나요?"
]);
const CHAT_MS=180_000;
const GUESS_MS=90_000;
const REVEAL_MS=10_000;
const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);

function fail(reason) {
  throw new HttpsError("invalid-argument",reason);
}
function shuffle(input,random=randomInt){
  const items=[...input];
  for(let i=items.length-1;i>0;i--){
    const j=random(i+1);
    if(!Number.isInteger(j)||j<0||j>i)throw new Error("Invalid server RNG");
    [items[i],items[j]]=[items[j],items[i]];
  }
  return items;
}
function prepare({participants},random=randomInt){
  if(!Array.isArray(participants)||participants.length<4||participants.length>10 ||
     new Set(participants).size!==participants.length){
    throw new HttpsError("failed-precondition","너 누구야?는 중복 없는 4~10명이 필요합니다.");
  }
  const aliases=shuffle(ALIASES,random).slice(0,participants.length);
  const privateByUid=Object.fromEntries(participants.map((uid,i)=>
    [uid,{alias:aliases[i]}]));
  const prompt=PROMPTS[random(PROMPTS.length)];
  return {
    phase:"playing",durationMs:CHAT_MS,
    publicState:{prompt,options:aliases},
    privateByUid
  };
}
function requirePersonal(alias,session,actorUid){
  const list=session.publicState?.options;
  if(typeof alias!=="string"||!Array.isArray(list)||
     !list.includes(alias)||!own(session.participants,actorUid)){
    throw new HttpsError("failed-precondition","익명 역할을 확인하지 못했습니다.");
  }
}
function validateSubmission({session,actorUid,choice,personalData}){
  if(session.phase!=="voting")fail("정체 추리 단계가 아닙니다.");
  const alias=personalData?.alias;
  requirePersonal(alias,session,actorUid);
  const allPlayers=Object.keys(session.participants);
  const allAliases=session.publicState.options;
  const expectedAliases=allAliases.filter(v=>v!==alias);
  const expectedTargets=allPlayers.filter(v=>v!==actorUid);
  if(!choice || typeof choice!=="object"||Array.isArray(choice)||
     Object.getPrototypeOf(choice)!==Object.prototype ||
     Object.keys(choice).length!==expectedAliases.length){
    fail("나를 제외한 모든 익명 별칭의 정체를 선택해 주세요.");
  }
  const matches={};
  for(const selectedAlias of expectedAliases){
    const target=choice[selectedAlias];
    if(!own(choice,selectedAlias) || typeof target!=="string"||
       !expectedTargets.includes(target))fail("선택한 정체가 잘못되었습니다.");
    matches[selectedAlias]=target;
  }
  if(new Set(Object.values(matches)).size!==expectedTargets.length) {
    fail("각 친구는 한 번만 선택할 수 있습니다.");
  }
  return {matches};
}
function requireAssignments(assignments,session){
  const ids=Object.keys(session.participants);
  const options=session.publicState.options;
  if(!assignments||Object.keys(assignments).length!==ids.length||
     ids.some(uid=>!assignments[uid]||!options.includes(assignments[uid].alias))||
     new Set(Object.values(assignments).map(v=>v.alias)).size!==ids.length){
    throw new HttpsError("failed-precondition","게임 비밀 역할의 무결성을 확인할 수 없습니다.");
  }
}
function computeScores(session,submissions,assignments){
  requireAssignments(assignments,session);
  const ids=Object.keys(session.participants);
  const byAlias=Object.fromEntries(ids.map(uid=>[assignments[uid].alias,uid]));
  const scoresByUid=Object.fromEntries(ids.map(uid=>[uid,0]));
  const fooled=Object.fromEntries(ids.map(uid=>[uid,0]));
  for(const voter of ids){
    const guessed=submissions[voter]?.matches;
    if(!guessed)continue;
    for(const [alias,target] of Object.entries(guessed)){
      const realUid=byAlias[alias];
      if(!realUid||realUid===voter||typeof target!=="string"||
         !ids.includes(target))continue;
      if(realUid===target)scoresByUid[voter]+=100;
      else fooled[realUid]+=10;
    }
  }
  for(const uid of ids){
    scoresByUid[uid]+=Math.min(fooled[uid],50);
  }
  return scoresByUid;
}
function publicReveal(session,assignments,memberNames,scores){
  requireAssignments(assignments,session);
  const byAlias=Object.entries(assignments)
    .map(([uid,v])=>({alias:v.alias,uid}))
    .sort((a,b)=>session.publicState.options.indexOf(a.alias)-session.publicState.options.indexOf(b.alias));
  const lines=byAlias.map(({alias,uid})=>{
    const raw=memberNames?.[uid];
    const safeName=typeof raw==="string"&&raw.trim()
      ?raw.trim().replace(/[\n\r]/g," ").slice(0,24):"멤버";
    return `${alias} = ${safeName} (${scores[uid]}점)`;
  });
  return lines.join("\n");
}
function advance({session,submissions={},assignments={},memberNames={}}){
  const pub=session.publicState;
  if(session.phase==="playing"){
    return {
      phase:"voting",durationMs:GUESS_MS,
      publicState:{prompt:pub.prompt,options:pub.options}
    };
  }
  if(session.phase==="voting"){
    const scores=computeScores(session,submissions,assignments);
    return {
      phase:"reveal",durationMs:REVEAL_MS,
      publicState:{
        prompt:pub.prompt,options:pub.options,
        publishedResult:publicReveal(session,assignments,memberNames,scores)
      }
    };
  }
  if(session.phase==="reveal"){
    const scores=computeScores(session,submissions,assignments);
    return {
      phase:"finished",
      publicState:pub,
      scoresByUid:scores
    };
  }
  throw new HttpsError("failed-precondition","진행할 수 없는 게임 단계입니다.");
}
function projectForPlayer({session,personalData}){
  if(session.phase==="finished"||session.phase==="cancelled")return {};
  if(!personalData)return null;
  const alias=personalData.alias;
  if(typeof alias!=="string"||!session.publicState.options?.includes(alias)){
    throw new HttpsError("failed-precondition","비밀 별칭 데이터가 올바르지 않습니다.");
  }
  return {alias};
}

module.exports={
  gameId:"identity",
  rulesVersion:1,
  prepare,
  validateSubmission,
  advance,
  projectForPlayer,
  shuffle,
  computeScores,
  publicReveal,
  CHAT_MS,GUESS_MS,REVEAL_MS,
  ALIASES,PROMPTS
};
