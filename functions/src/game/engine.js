"use strict";

const {HttpsError} = require("firebase-functions/v2/https");
const {getGameDescriptor, GAME_PHASES, GAME_MODES} = require("./contracts");

const SCHEMA_VERSION = 1;
const LOBBY_PHASES = new Set(["waiting", "ready"]);
const TERMINAL_PHASES = new Set(["finished", "cancelled"]);
const ALLOWED_TRANSITIONS = Object.freeze({
  waiting: ["ready", "cancelled"],
  ready: ["waiting", "assigning", "playing", "voting", "cancelled"],
  assigning: ["playing", "cancelled"],
  playing: ["voting", "reveal", "cancelled"],
  voting: ["voting", "reveal", "cancelled"],
  reveal: ["finished", "cancelled"],
  finished: [],
  cancelled: []
});

function requirePhase(phase) {
  if(!GAME_PHASES.includes(phase)){
    throw new HttpsError("failed-precondition","지원하지 않는 게임 상태입니다.");
  }
}

function assertTransition(from,to){
  requirePhase(from);
  requirePhase(to);
  if(!ALLOWED_TRANSITIONS[from].includes(to)){
    throw new HttpsError("failed-precondition","이 게임 단계로 이동할 수 없습니다.");
  }
}

function participantIds(session){
  const list=session.participants;
  if(!list || typeof list!=="object" || Array.isArray(list)){
    throw new HttpsError("failed-precondition","참가자 정보가 올바르지 않습니다.");
  }
  return Object.keys(list);
}

function requireParticipant(session,uid){
  if(!Object.hasOwn(session.participants||{},uid)){
    throw new HttpsError("permission-denied","게임 참가자만 이 작업을 실행할 수 있습니다.");
  }
}

function lobbySnapshot(session){
  const descriptor=getGameDescriptor(session.gameId,session.rulesVersion);
  const ids=participantIds(session);
  const ready=ids.length>=descriptor.minPlayers &&
    ids.every(uid=>session.participants[uid]?.ready===true);
  return {phase:ready?"ready":"waiting",count:ids.length,ready};
}

function changeLobby(session, uid, kind, ready){
  if(!LOBBY_PHASES.has(session.phase)){
    throw new HttpsError("failed-precondition","이미 시작된 게임은 로비를 수정할 수 없습니다.");
  }
  const game=getGameDescriptor(session.gameId,session.rulesVersion);
  const participants={...session.participants};
  if(kind==="join"){
    if(Object.hasOwn(participants,uid))return {participants,phase:session.phase,changed:false};
    if(Object.keys(participants).length>=game.maxPlayers){
      throw new HttpsError("resource-exhausted","게임 참가 인원이 가득 찼습니다.");
    }
    participants[uid]={ready:false};
  }else if(kind==="set_ready"){
    requireParticipant(session,uid);
    if(typeof ready!=="boolean"){
      throw new HttpsError("invalid-argument","준비 상태를 확인해 주세요.");
    }
    if(participants[uid].ready===ready)return {participants,phase:session.phase,changed:false};
    participants[uid]={ready};
  }else{
    throw new HttpsError("invalid-argument","지원하지 않는 로비 작업입니다.");
  }
  const phase=lobbySnapshot({...session,participants}).phase;
  if(phase!==session.phase)assertTransition(session.phase,phase);
  return {participants,phase,changed:true};
}

function assertReadyToStart(session){
  const result=lobbySnapshot(session);
  if(result.phase!=="ready" || session.phase!=="ready"){
    throw new HttpsError("failed-precondition","최소 인원과 모든 참가자의 준비가 필요합니다.");
  }
}

function validateTimer(durationMs){
  if(!Number.isSafeInteger(durationMs) || durationMs<5_000 || durationMs>24*60*60*1000){
    throw new HttpsError("failed-precondition","서버 게임 제한 시간이 잘못되었습니다.");
  }
  return durationMs;
}

function validatePublicState(state){
  if(state===undefined)return {};
  if(state===null || typeof state!=="object" || Array.isArray(state)){
    throw new HttpsError("failed-precondition","공개 게임 상태 형식이 올바르지 않습니다.");
  }
  const allowed=new Set(["prompt","notice","options","publishedResult","eliminatedUids"]);
  if(Object.keys(state).some(key=>!allowed.has(key)) || JSON.stringify(state).length>4000){
    throw new HttpsError("failed-precondition","비공개 필드를 공개 게임 상태에 저장할 수 없습니다.");
  }
  for(const [key,value] of Object.entries(state)){
    if(key==="eliminatedUids"){
      if(!Array.isArray(value)||value.length>10||
         value.some(item=>typeof item!=="string"||item.length>128)||
         new Set(value).size!==value.length){
        throw new HttpsError("failed-precondition","금지어 탈락 상태가 올바르지 않습니다.");
      }
    }else if(key==="options"){
      if(!Array.isArray(value) || value.length>20 ||
         value.some(item=>typeof item!=="string"||item.length>100)){
        throw new HttpsError("failed-precondition","공개 선택지가 올바르지 않습니다.");
      }
    }else if(typeof value!=="string" || value.length>1000){
      throw new HttpsError("failed-precondition","공개 게임 문구가 올바르지 않습니다.");
    }
  }
  return state;
}

function validatePhasePublicState(value,phase){
  const state=validatePublicState(value);
  if(!["reveal","finished"].includes(phase) &&
     Object.hasOwn(state,"publishedResult")){
    throw new HttpsError("failed-precondition","결과 공개 전 비밀 정보를 표시할 수 없습니다.");
  }
  return state;
}

function ruleKey(id,version){return id+"@"+version;}

function makeRuleRegistry(definitions=[]){
  const registered=new Map();
  for(const rule of definitions){
    if(!rule || typeof rule.gameId!=="string" ||
      !Number.isSafeInteger(rule.rulesVersion) ||
      typeof rule.prepare!=="function" ||
      typeof rule.validateSubmission!=="function" ||
      typeof rule.advance!=="function" ||
      typeof rule.projectForPlayer!=="function"){
      throw new Error("Invalid server game rule module");
    }
    getGameDescriptor(rule.gameId,rule.rulesVersion);
    const key=ruleKey(rule.gameId,rule.rulesVersion);
    if(registered.has(key))throw new Error("Duplicate rule module");
    registered.set(key,Object.freeze({...rule}));
  }
  return Object.freeze({
    require(id,version){
      getGameDescriptor(id,version);
      const rule=registered.get(ruleKey(id,version));
      if(!rule){
        throw new HttpsError("failed-precondition",
          "해당 게임의 서버 규칙은 다음 개발 단계에서 연결됩니다.");
      }
      return rule;
    },
    supported(id,version){return registered.has(ruleKey(id,version));}
  });
}

// Only audited identity@1 and liar@1 are activated. Others remain fail-closed.
const identityRules=require("./identityRules");
const liarRules=require("./liarRules");
const forbiddenRules=require("./forbiddenRules");
const balanceRules=require("./balanceRules");
const productionRules=makeRuleRegistry([identityRules,liarRules,forbiddenRules,balanceRules]);

function publicSnapshot(doc){
  requirePhase(doc.phase);
  return {
    schemaVersion:SCHEMA_VERSION,
    roomId:doc.roomId,
    sessionId:doc.sessionId,
    gameId:doc.gameId,
    rulesVersion:doc.rulesVersion,
    mode:doc.mode,
    phase:doc.phase,
    revision:doc.revision,
    round:doc.round,
    participants:doc.participants,
    deadlineEpochMillis:doc.phaseEndsAt?.toMillis?.()||null,
    publicState:validatePhasePublicState(doc.publicState,doc.phase)
  };
}

module.exports={
  SCHEMA_VERSION, LOBBY_PHASES, TERMINAL_PHASES, ALLOWED_TRANSITIONS,
  assertTransition, requireParticipant, participantIds, lobbySnapshot,
  changeLobby, assertReadyToStart, validateTimer, validatePublicState,validatePhasePublicState,
  makeRuleRegistry,productionRules,publicSnapshot
};
