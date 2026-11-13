"use strict";

const {test} = require("node:test");
const assert = require("node:assert/strict");
const {
  changeLobby, lobbySnapshot,assertTransition,assertReadyToStart,
  validateTimer,validatePublicState,validatePhasePublicState,makeRuleRegistry,productionRules,publicSnapshot
} = require("../src/game/engine");
const {parseCreate,parseAction,validateSessionRef,submissionId} = require("../src/game/sessionService");

const roomId="12345678901234567890";
const sessionId="a".repeat(64);
const requestId="123e4567-e89b-42d3-a456-426614174001";
const session=()=>({
 schemaVersion:1,roomId,sessionId,gameId:"balance",rulesVersion:1,
 mode:"realtime",phase:"waiting",revision:0,round:1,
 participants:{owner:{ready:false}},phaseEndsAt:null,
 publicState:{},privateAssignments:{owner:{secret:"test"}}
});

test("lobby supports join, ready, unready without losing room participant identity",()=>{
  const first=changeLobby(session(),"member","join");
  assert.equal(first.phase,"waiting");
  assert.equal(first.participants.member.ready,false);
  const owner=changeLobby({...session(),participants:first.participants},"owner","set_ready",true);
  assert.equal(owner.phase,"waiting");
  const next=changeLobby({...session(),participants:owner.participants},"member","set_ready",true);
  assert.equal(next.phase,"ready");
  assertReadyToStart({...session(),phase:"ready",participants:next.participants});
  const rev=changeLobby({...session(),phase:"ready",participants:next.participants},"member","set_ready",false);
  assert.equal(rev.phase,"waiting");
});

test("lobby enforces minimum players, capacity and participant permissions",()=>{
  assert.throws(()=>assertReadyToStart(session()),/최소 인원/);
  assert.throws(()=>changeLobby(session(),"intruder","set_ready",true),/참가자/);
  assert.throws(()=>changeLobby({...session(),phase:"playing"},"other","join"),/시작된/);
  const ten=Object.fromEntries(Array.from({length:10},(_,i)=>["p"+i,{ready:true}]));
  assert.throws(()=>changeLobby({...session(),participants:ten},"next","join"),/가득/);
  assert.equal(lobbySnapshot({...session(),participants:ten}).ready,true);
});

test("phase transitions permit optional skip, but block terminal rewind",()=>{
  assertTransition("ready","playing");
  assertTransition("playing","voting");
  assertTransition("voting","voting");
  assertTransition("voting","reveal");
  assertTransition("reveal","finished");
  assert.throws(()=>assertTransition("finished","playing"),/이동/);
  assert.throws(()=>assertTransition("waiting","reveal"),/이동/);
  assert.throws(()=>assertTransition("playing","playing"),/이동/);
});

test("server timer has bounded durations and cannot accept zero or unbounded",()=>{
  assert.equal(validateTimer(5000),5000);
  assert.equal(validateTimer(86400000),86400000);
  assert.throws(()=>validateTimer(0));
  assert.throws(()=>validateTimer(100));
  assert.throws(()=>validateTimer(86400001));
});

test("public session projection is an allowlist excluding identities and role secrets",()=>{
  const projection=publicSnapshot(session());
  assert.equal(projection.gameId,"balance");
  assert.equal(projection.deadlineEpochMillis,null);
  assert.equal(projection.privateAssignments,undefined);
  assert.equal(projection.createdBy,undefined);
  assert.throws(()=>validatePublicState({privateAssignments:{owner:"secret"}}),/비공개/);
  assert.throws(()=>validatePublicState({options:["x".repeat(101)]}),/선택지/);
  assert.throws(()=>validatePhasePublicState({publishedResult:"비밀"}, "playing"),/결과 공개 전/);
  assert.deepEqual(validatePhasePublicState({publishedResult:"공개"}, "reveal"),
    {publishedResult:"공개"});
});

test("only audited identity@1 is registered; future games remain fail-closed",()=>{
  assert.equal(productionRules.supported("identity",1),true);
  assert.equal(productionRules.require("identity",1).gameId,"identity");
  for(const id of ["liar","forbidden","balance"]){
    assert.equal(productionRules.supported(id,1),false);
    assert.throws(()=>productionRules.require(id,1),/다음 개발 단계/);
  }
  assert.throws(()=>makeRuleRegistry([{gameId:"identity",rulesVersion:1}]));
});

test("create and action requests reject supplied server identities or invalid versions",()=>{
  const valid={roomId,gameId:"balance",mode:"async",requestId};
  assert.equal(parseCreate(valid).game.rulesVersion,1);
  assert.throws(()=>parseCreate({...valid,actorUid:"admin"}));
  assert.throws(()=>parseCreate({...valid,mode:"unsupported"}));
  assert.throws(()=>parseCreate({...valid,gameId:"invented"}));
  const env={roomId,sessionId,gameId:"balance",rulesVersion:1,
    expectedRevision:0,requestId,payload:{kind:"join"}};
  assert.equal(parseAction(env).payload.kind,"join");
  for(const change of [
    {score:2000},{uid:"admin"},{rulesVersion:2},{expectedRevision:-1},
    {payload:{kind:"set_ready",ready:"true"}},
    {payload:{kind:"start",role:"owner"}},
    {payload:{kind:"server_result",score:999}}
  ])assert.throws(()=>parseAction({...env,...change}));
  assert.deepEqual(validateSessionRef({roomId,sessionId}),{roomId,sessionId});
  assert.throws(()=>validateSessionRef({roomId,sessionId,uid:"u"}));
  assert.equal(submissionId("u",1,"voting"),submissionId("u",1,"voting"));
  assert.notEqual(submissionId("u",1,"voting"),submissionId("u",2,"voting"));
});
