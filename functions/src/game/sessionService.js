"use strict";

const {createHash} = require("node:crypto");
const {FieldValue,Timestamp} = require("firebase-admin/firestore");
const {HttpsError} = require("firebase-functions/v2/https");
const {authUid,roomIdValue} = require("../policy");
const {
  CATALOG_SCHEMA_VERSION,getGameDescriptor,validateActionEnvelope,GameContractError
} = require("./contracts");
const {
  SCHEMA_VERSION,TERMINAL_PHASES,LOBBY_PHASES,participantIds,
  requireParticipant,changeLobby,assertReadyToStart,assertTransition,
  validateTimer,validatePublicState,productionRules,publicSnapshot
} = require("./engine");

const SESSION_ID_PATTERN=/^[a-f0-9]{64}$/;
const REQUEST_ID_PATTERN=/^[a-zA-Z0-9_-]{16,80}$/;
const CREATE_KEYS=new Set(["roomId","gameId","mode","requestId"]);
const ACTION_KEYS=Object.freeze({
  join:new Set(["kind"]),
  set_ready:new Set(["kind","ready"]),
  start:new Set(["kind"]),
  submit:new Set(["kind","choice"]),
  advance:new Set(["kind"]),
  cancel:new Set(["kind"])
});
const sha=value=>createHash("sha256").update(JSON.stringify(value)).digest("hex");
const nonemptyObject=value=>value!==null && typeof value==="object" &&
  !Array.isArray(value) && Object.getPrototypeOf(value)===Object.prototype;
const allowed=(obj,keys)=>nonemptyObject(obj)&&Object.keys(obj).every(k=>keys.has(k));

function parseCreate(value){
  if(!allowed(value,CREATE_KEYS) || Object.keys(value).length!==4){
    throw new HttpsError("invalid-argument","게임 생성 요청의 필드가 올바르지 않습니다.");
  }
  const roomId=roomIdValue(value.roomId);
  let game;
  try{game=getGameDescriptor(value.gameId);}catch(_){
    throw new HttpsError("invalid-argument","지원하지 않는 게임입니다.");
  }
  if(!game.modes.includes(value.mode) ||
    typeof value.requestId!=="string" ||
    !REQUEST_ID_PATTERN.test(value.requestId)){
    throw new HttpsError("invalid-argument","게임 모드 또는 요청 ID가 올바르지 않습니다.");
  }
  return {roomId,game,mode:value.mode,requestId:value.requestId};
}

function parseAction(value){
  let envelope;
  try{envelope=validateActionEnvelope(value);}catch(e){
    if(e instanceof GameContractError){
      throw new HttpsError("invalid-argument","게임 행동 요청의 필드가 올바르지 않습니다.");
    }
    throw e;
  }
  const payload=envelope.payload;
  if(typeof payload.kind!=="string" || !Object.hasOwn(ACTION_KEYS,payload.kind) ||
     !allowed(payload,ACTION_KEYS[payload.kind]) ||
     (payload.kind==="set_ready"&&typeof payload.ready!=="boolean") ||
     (payload.kind==="submit"&&!Object.hasOwn(payload,"choice"))){
    throw new HttpsError("invalid-argument","지원하지 않는 게임 행동 형식입니다.");
  }
  return envelope;
}

function validateSessionRef(value){
  if(!allowed(value,new Set(["roomId","sessionId"])) ||
     Object.keys(value).length!==2 ||
     typeof value.sessionId!=="string" || !SESSION_ID_PATTERN.test(value.sessionId)){
    throw new HttpsError("invalid-argument","게임 세션 참조가 올바르지 않습니다.");
  }
  return {roomId:roomIdValue(value.roomId),sessionId:value.sessionId};
}

function requireActive(room,member){
  if(!room.exists || room.get("status")!=="active" ||
     !member.exists || member.get("status")!=="active"){
    throw new HttpsError("permission-denied","활성화된 방 멤버만 게임을 이용할 수 있습니다.");
  }
}

function requireRoomOwner(room,uid){
  if(room.get("ownerId")!==uid){
    throw new HttpsError("permission-denied","방장만 게임을 시작하거나 취소할 수 있습니다.");
  }
}

function ensureValidSession(doc,env){
  if(!doc.exists)throw new HttpsError("not-found","게임 세션이 없습니다.");
  const session=doc.data();
  if(session.roomId!==env.roomId ||
     session.sessionId!==env.sessionId ||
     session.gameId!==env.gameId ||
     session.rulesVersion!==env.rulesVersion ||
     session.schemaVersion!==SCHEMA_VERSION){
    throw new HttpsError("failed-precondition","게임 규칙/데이터 버전이 일치하지 않습니다.");
  }
  return session;
}

function validatePrivateAssignments(map,ids){
  if(!nonemptyObject(map)||Object.keys(map).some(uid=>!ids.includes(uid))){
    throw new HttpsError("failed-precondition","역할 배정 대상이 올바르지 않습니다.");
  }
  for(const value of Object.values(map)){
    if(!nonemptyObject(value) || JSON.stringify(value).length>2000){
      throw new HttpsError("failed-precondition","비밀 역할 데이터가 올바르지 않습니다.");
    }
  }
  return map;
}

function requireServerState(d){
  if(!nonemptyObject(d)||JSON.stringify(d).length>2000){
    throw new HttpsError("failed-precondition","서버 전용 게임 상태가 올바르지 않습니다.");
  }
  return d;
}

function requireScoreResult(result,participants){
  if(!nonemptyObject(result)||!nonemptyObject(result.scoresByUid) ||
     Object.keys(result.scoresByUid).length!==participants.length ||
     Object.keys(result.scoresByUid).some(uid=>!participants.includes(uid))){
    throw new HttpsError("failed-precondition","점수 정산 대상이 올바르지 않습니다.");
  }
  for(const uid of participants){
    const score=result.scoresByUid[uid];
    if(!Number.isSafeInteger(score)||score<0||score>10000){
      throw new HttpsError("failed-precondition","서버 점수 계산값이 올바르지 않습니다.");
    }
  }
  return result.scoresByUid;
}

function submissionId(uid,round,phase){return sha([uid,round,phase]);}

async function createGameSession(db,request){
  const uid=authUid(request);
  const {roomId,game,mode,requestId}=parseCreate(request.data);
  const roomRef=db.collection("rooms").doc(roomId);
  const memberRef=roomRef.collection("members").doc(uid);
  const sessionId=sha([roomId,uid,requestId]);
  const sessionRef=roomRef.collection("gameSessions").doc(sessionId);
  return db.runTransaction(async tx=>{
    const [room,member,existing]=await Promise.all([
      tx.get(roomRef),tx.get(memberRef),tx.get(sessionRef)
    ]);
    requireActive(room,member);
    requireRoomOwner(room,uid);
    if(existing.exists){
      const old=existing.data();
      if(old.createdBy!==uid || old.gameId!==game.id ||
         old.mode!==mode || old.createRequestId!==requestId){
        throw new HttpsError("already-exists","다른 게임 요청 ID와 충돌했습니다.");
      }
      return {roomId,sessionId,phase:old.phase,revision:old.revision,deduplicated:true};
    }
    const active=room.get("activeGameSessionId");
    if(typeof active==="string"&&active.length>0){
      const other=await tx.get(roomRef.collection("gameSessions").doc(active));
      if(!other.exists || !TERMINAL_PHASES.has(other.get("phase"))){
        throw new HttpsError("failed-precondition","이 방에서 이미 진행 중인 게임이 있습니다.");
      }
    }
    const newSession={
      schemaVersion:SCHEMA_VERSION, catalogSchemaVersion:CATALOG_SCHEMA_VERSION,
      roomId,sessionId,gameId:game.id,rulesVersion:game.rulesVersion,mode,
      createdBy:uid,createRequestId:requestId,
      phase:"waiting",revision:0,round:1,
      phaseEndsAt:null,startedAt:null,settlementId:null,
      participants:{[uid]:{ready:false}},submissionCount:0,
      publicState:{},createdAt:FieldValue.serverTimestamp(),
      updatedAt:FieldValue.serverTimestamp()
    };
    tx.create(sessionRef,newSession);
    tx.update(roomRef,{activeGameSessionId:sessionId});
    return {roomId,sessionId,phase:"waiting",revision:0,deduplicated:false};
  });
}

async function gameSessionAction(db,request,rules=productionRules,now=Date.now){
  const uid=authUid(request);
  const env=parseAction(request.data);
  const roomRef=db.collection("rooms").doc(env.roomId);
  const memberRef=roomRef.collection("members").doc(uid);
  const sessionRef=roomRef.collection("gameSessions").doc(env.sessionId);
  const receiptRef=sessionRef.collection("processedActions").doc(sha([uid,env.requestId]));
  const fingerprint=sha(env);
  return db.runTransaction(async tx=>{
    const [room,member,doc,receipt]=await Promise.all([
      tx.get(roomRef),tx.get(memberRef),tx.get(sessionRef),tx.get(receiptRef)
    ]);
    requireActive(room,member);
    const session=ensureValidSession(doc,env);
    if(receipt.exists){
      if(receipt.get("actorUid")!==uid || receipt.get("fingerprint")!==fingerprint){
        throw new HttpsError("already-exists","요청 ID가 이미 다른 게임 행동에 사용되었습니다.");
      }
      return {...receipt.get("result"),deduplicated:true};
    }
    if(env.expectedRevision!==session.revision){
      throw new HttpsError("aborted","게임 상태가 변경됐습니다. 최신 상태를 다시 불러오세요.");
    }
    const kind=env.payload.kind;
    if(TERMINAL_PHASES.has(session.phase)){
      throw new HttpsError("failed-precondition","이미 종료된 게임입니다.");
    }
    if(room.get("activeGameSessionId")!==env.sessionId){
      throw new HttpsError("failed-precondition","현재 활성 게임 세션이 아닙니다.");
    }

    const nextRevision=session.revision+1;
    let changes={};
    let phase=session.phase;
    let extraWrites=[];
    let unchanged=false;
    const participants=participantIds(session);
    const timestamp=now();

    if(kind==="join" || kind==="set_ready"){
      const update=changeLobby(session,uid,kind,env.payload.ready);
      if(!update.changed)unchanged=true;
      else{
        phase=update.phase;
        changes={participants:update.participants,phase};
      }
    }else if(kind==="cancel"){
      requireRoomOwner(room,uid);
      phase="cancelled";
      changes={phase,phaseEndsAt:null};
      extraWrites.push(()=>tx.update(roomRef,{activeGameSessionId:null}));
    }else if(kind==="start"){
      requireRoomOwner(room,uid);
      assertReadyToStart(session);
      const checks=await Promise.all(participants.map(id=>
        tx.get(roomRef.collection("members").doc(id))));
      if(checks.some(s=>!s.exists||s.get("status")!=="active")){
        throw new HttpsError("failed-precondition","활성 멤버가 아닌 참가자가 있습니다.");
      }
      const rule=rules.require(session.gameId,session.rulesVersion);
      const setup=rule.prepare({session,participants:[...participants],nowMillis:timestamp});
      if(!nonemptyObject(setup)||!["assigning","playing","voting"].includes(setup.phase)){
        throw new HttpsError("failed-precondition","게임 시작 상태가 올바르지 않습니다.");
      }
      assertTransition(session.phase,setup.phase);
      validateTimer(setup.durationMs);
      const privateByUid=validatePrivateAssignments(setup.privateByUid||{},participants);
      phase=setup.phase;
      changes={
        phase,phaseEndsAt:Timestamp.fromMillis(timestamp+setup.durationMs),
        publicState:validatePublicState(setup.publicState),
        startedAt:Timestamp.fromMillis(timestamp),submissionCount:0
      };
      for(const [playerId,data] of Object.entries(privateByUid)){
        const personalRef=sessionRef.collection("privateAssignments").doc(playerId);
        extraWrites.push(()=>tx.create(personalRef,{
          ...{data},updatedAt:FieldValue.serverTimestamp()
        }));
      }
    }else if(kind==="submit"){
      requireParticipant(session,uid);
      if(!["playing","voting"].includes(session.phase)){
        throw new HttpsError("failed-precondition","이 단계에서는 제출할 수 없습니다.");
      }
      if(!session.phaseEndsAt || session.phaseEndsAt.toMillis()<=timestamp){
        throw new HttpsError("deadline-exceeded","제출 시간이 종료되었습니다.");
      }
      const submissionRef=sessionRef.collection("privateSubmissions")
        .doc(submissionId(uid,session.round,session.phase));
      const existing=await tx.get(submissionRef);
      if(existing.exists){
        throw new HttpsError("already-exists","이미 이 단계에 제출했습니다.");
      }
      const rule=rules.require(session.gameId,session.rulesVersion);
      const clean=requireServerState(rule.validateSubmission({
        session,actorUid:uid,choice:env.payload.choice
      }));
      changes={submissionCount:(session.submissionCount||0)+1};
      extraWrites.push(()=>tx.create(submissionRef,{
        actorUid:uid,round:session.round,phase:session.phase,
        data:clean,submittedAt:FieldValue.serverTimestamp()
      }));
    }else if(kind==="advance"){
      requireParticipant(session,uid);
      if(!session.phaseEndsAt || !["assigning","playing","voting","reveal"].includes(session.phase)){
        throw new HttpsError("failed-precondition","마감 가능한 단계가 아닙니다.");
      }
      const submissions={};
      if(["playing","voting"].includes(session.phase)){
        const docs=await Promise.all(participants.map(playerId=>
          tx.get(sessionRef.collection("privateSubmissions")
            .doc(submissionId(playerId,session.round,session.phase)))));
        docs.forEach((snap,index)=>{
          if(snap.exists)submissions[participants[index]]=snap.get("data");
        });
      }
      const allSubmitted=["playing","voting"].includes(session.phase) &&
        participants.length>0 && Object.keys(submissions).length===participants.length;
      if(session.phaseEndsAt.toMillis()>timestamp && !allSubmitted){
        throw new HttpsError("failed-precondition","게임 단계의 제한 시간이 남아 있습니다.");
      }
      const rule=rules.require(session.gameId,session.rulesVersion);
      const moved=rule.advance({session,submissions,nowMillis:timestamp});
      if(!nonemptyObject(moved)||typeof moved.phase!=="string"){
        throw new HttpsError("failed-precondition","게임 전이 응답이 잘못되었습니다.");
      }
      assertTransition(session.phase,moved.phase);
      const nextRound=moved.round===undefined?session.round:moved.round;
      if(!Number.isSafeInteger(nextRound)||nextRound<session.round||
         nextRound>99||nextRound>session.round+1||
         (moved.phase===session.phase && nextRound===session.round)){
        throw new HttpsError("failed-precondition","게임 라운드 전이가 올바르지 않습니다.");
      }
      phase=moved.phase;
      changes={phase,round:nextRound,publicState:validatePublicState(moved.publicState),
        submissionCount:0};
      if(phase==="finished"){
        const scores=requireScoreResult(moved,participants);
        if(session.settlementId!==null){
          throw new HttpsError("failed-precondition","이미 점수가 확정된 게임입니다.");
        }
        const settlementId=sha([env.roomId,env.sessionId,"final",session.rulesVersion]);
        changes.settlementId=settlementId;
        changes.phaseEndsAt=null;
        extraWrites.push(()=>tx.create(sessionRef.collection("results").doc("final"),{
          settlementId,gameId:session.gameId,rulesVersion:session.rulesVersion,
          scoresByUid:scores,createdAt:FieldValue.serverTimestamp()
        }));
        extraWrites.push(()=>tx.update(roomRef,{activeGameSessionId:null}));
      }else{
        validateTimer(moved.durationMs);
        changes.phaseEndsAt=Timestamp.fromMillis(timestamp+moved.durationMs);
      }
    }
    if(!unchanged){
      changes.revision=nextRevision;
      changes.updatedAt=FieldValue.serverTimestamp();
      tx.update(sessionRef,changes);
    }
    for(const write of extraWrites)write();
    const response={roomId:env.roomId,sessionId:env.sessionId,
      phase,revision:unchanged?session.revision:nextRevision};
    tx.create(receiptRef,{
      actorUid:uid,fingerprint,kind,result:response,
      createdAt:FieldValue.serverTimestamp()
    });
    return {...response,deduplicated:false};
  });
}

async function getMyGameSession(db,request,rules=productionRules){
  const uid=authUid(request);
  const {roomId,sessionId}=validateSessionRef(request.data);
  const roomRef=db.collection("rooms").doc(roomId);
  const sessionRef=roomRef.collection("gameSessions").doc(sessionId);
  const [room,member,snapshot]=await Promise.all([
    roomRef.get(),roomRef.collection("members").doc(uid).get(),sessionRef.get()
  ]);
  requireActive(room,member);
  if(!snapshot.exists)throw new HttpsError("not-found","게임 세션이 없습니다.");
  const session=snapshot.data();
  if(session.schemaVersion!==SCHEMA_VERSION){
    throw new HttpsError("failed-precondition","업데이트가 필요한 세션 데이터입니다.");
  }
  requireParticipant(session,uid);
  let personal=null;
  if(rules.supported(session.gameId,session.rulesVersion)){
    const rule=rules.require(session.gameId,session.rulesVersion);
    const doc=await sessionRef.collection("privateAssignments").doc(uid).get();
    const personalData=doc.exists?doc.get("data"):null;
    personal=rule.projectForPlayer({session,actorUid:uid,personalData});
    if(personal!==null&&!nonemptyObject(personal)){
      throw new HttpsError("internal","게임 개인 상태가 잘못되었습니다.");
    }
  }
  return {public:publicSnapshot(session),personal};
}

module.exports={
  parseCreate,parseAction,validateSessionRef,submissionId,
  createGameSession,gameSessionAction,getMyGameSession
};
