"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore,Timestamp}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const game=require("../src/game/sessionService");
const identityChat=require("../src/game/identityChatService");
const {productionRules}=require("../src/game/engine");
const identity=require("../src/game/identityRules");

let app,db;
const auth=(uid,name=uid)=>({auth:{uid,token:{name}}});
const reqId=n=>"game-03-request-"+String(n).padStart(12,"0");
const messageReqId=n=>"123e4567-e89b-42d3-a456-"+String(n).padStart(12,"0");

async function makeRoom(){
  const {roomId}=await rooms.createRoom(db,{...auth("owner","민지"),data:{
    name:"너 누구야? 실제 테스트방",description:"",requiresApproval:false
  }});
  const invite=await rooms.createInvitation(db,{...auth("owner"),data:{roomId}});
  const members=[
    ["owner","민지"],["player2","지훈"],["player3","수빈"],["player4","도윤"]
  ];
  for(const [uid,name] of members.slice(1)){
    await rooms.requestJoin(db,{...auth(uid,name),data:{code:invite.code}});
  }
  return {roomId,members};
}
function action(roomId,sessionId,uid,rev,kind,extras={},n=1,nowMillis=1762935600000){
  return game.gameSessionAction(db,{...auth(uid),data:{
    roomId,sessionId,gameId:"identity",rulesVersion:1,
    expectedRevision:rev,requestId:reqId(n),payload:{kind,...extras}
  }},productionRules,()=>nowMillis);
}
async function startRoom(roomId,members){
  const {sessionId}=await game.createGameSession(db,{...auth("owner"),data:{
    roomId,gameId:"identity",mode:"realtime",requestId:reqId(1)
  }});
  let revision=0;
  let i=30;
  for(const [uid] of members.slice(1)){
    const result=await action(roomId,sessionId,uid,revision,"join",{},i++);
    revision=result.revision;
  }
  for(const [uid] of members){
    const result=await action(roomId,sessionId,uid,revision,"set_ready",{ready:true},i++);
    revision=result.revision;
  }
  const started=await action(roomId,sessionId,"owner",revision,"start",{},i++);
  assert.equal(started.phase,"playing");
  return {sessionId,revision:started.revision,started};
}

before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore emulator is required");
  app=initializeApp({projectId:"demo-talksullae"},"game-03-identity-emulator");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("four players complete real anonymous chat, unique guesses, reveal and settle once",async()=>{
  const {roomId,members}=await makeRoom();
  const {sessionId,revision}=await startRoom(roomId,members);
  const sessionRef=db.collection("rooms").doc(roomId).collection("gameSessions").doc(sessionId);
  const record=await sessionRef.get();
  assert.equal(record.get("phase"),"playing");
  assert.equal(record.get("publicState.options").length,4);
  assert.equal(Object.keys(record.get("participants")).length,4);
  assert.equal(record.get("publicState.publishedResult"),undefined);
  const assignments={};
  for(const [uid] of members){
    const privateDoc=await sessionRef.collection("privateAssignments").doc(uid).get();
    assignments[uid]=privateDoc.get("data").alias;
    const own=await game.getMyGameSession(db,{...auth(uid),data:{roomId,sessionId}});
    assert.equal(own.personal.alias,assignments[uid]);
    assert.ok(!JSON.stringify(own.public).includes("realUid"));
  }
  assert.equal(new Set(Object.values(assignments)).size,4);
  const anon=await identityChat.sendIdentityMessage(db,{...auth("owner"),data:{
    roomId,sessionId,requestId:messageReqId(1),text:"가장 오래 살아남는 친구는 누구일까요?"
  }},()=>1762935601000);
  const repeated=await identityChat.sendIdentityMessage(db,{...auth("owner"),data:{
    roomId,sessionId,requestId:messageReqId(1),text:"가장 오래 살아남는 친구는 누구일까요?"
  }},()=>1762935601001);
  assert.equal(anon.messageId,repeated.messageId);
  assert.equal(repeated.deduplicated,true);
  const publicMessage=await sessionRef.collection("publicMessages").doc(anon.messageId).get();
  assert.equal(publicMessage.get("alias"),assignments.owner);
  assert.equal(publicMessage.get("senderUid"),undefined);
  assert.ok(!JSON.stringify(publicMessage.data()).includes("owner"));
  await assert.rejects(identityChat.sendIdentityMessage(db,{...auth("outsider"),data:{
    roomId,sessionId,requestId:messageReqId(2),text:"외부인"
  }},()=>1762935601002),e=>e.code==="permission-denied");

  const transition=await action(roomId,sessionId,"owner",revision,"advance",{},60,1762935780001);
  assert.equal(transition.phase,"voting");
  let rev=transition.revision;
  let n=65;
  for(const [uid] of members){
    const ownAlias=assignments[uid];
    const choice=Object.fromEntries(
      Object.entries(assignments).filter(([targetUid])=>targetUid!==uid)
      .map(([targetUid,alias])=>[alias,targetUid])
    );
    const result=await action(roomId,sessionId,uid,rev,"submit",{choice},n++,1762935780100);
    rev=result.revision;
    const view=await game.getMyGameSession(db,{...auth(uid),data:{roomId,sessionId}});
    assert.equal(view.personal.submitted,true);
    assert.equal(view.personal.alias,ownAlias);
  }
  const revealed=await action(roomId,sessionId,"player2",rev,"advance",{},n++,1762935780200);
  assert.equal(revealed.phase,"reveal");
  const revealDoc=await sessionRef.get();
  assert.ok(revealDoc.get("publicState.publishedResult").includes("민지"));
  assert.ok(revealDoc.get("publicState.publishedResult").includes("도윤"));
  assert.equal((await sessionRef.collection("results").get()).size,0);

  const finished=await action(roomId,sessionId,"player2",revealed.revision,
    "advance",{},n++,1762935790300);
  assert.equal(finished.phase,"finished");
  const results=await sessionRef.collection("results").doc("final").get();
  assert.equal(results.exists,true);
  for(const [uid] of members)assert.equal(results.get("scoresByUid."+uid),300);
  const replay=await action(roomId,sessionId,"player2",revealed.revision,
    "advance",{},n-1,1762935790400);
  assert.equal(replay.deduplicated,true);
  assert.equal((await sessionRef.collection("results").get()).size,1);
  assert.equal((await db.collection("rooms").doc(roomId).get()).get("activeGameSessionId"),null);
});

test("anonymous chat rate-limits bursts without losing idempotent retry",async()=>{
  const {roomId,members}=await makeRoom();
  const {sessionId}=await startRoom(roomId,members);
  const call=(n,time)=>identityChat.sendIdentityMessage(db,{...auth("owner"),data:{
    roomId,sessionId,requestId:messageReqId(n),text:"메시지 "+n
  }},()=>time);
  const first=await call(50,1762935601000);
  assert.equal(first.deduplicated,false);
  await assert.rejects(call(51,1762935601100),e=>e.code==="resource-exhausted");
  const again=await call(50,1762935601101);
  assert.equal(again.deduplicated,true);
  const next=await call(52,1762935601750);
  assert.equal(next.deduplicated,false);
  const docs=await db.collection("rooms").doc(roomId)
    .collection("gameSessions").doc(sessionId)
    .collection("publicMessages").get();
  assert.equal(docs.size,2);
});

test("ten distinct members can start while an eleventh participant is rejected",async()=>{
  const {roomId,members}=await makeRoom();
  const inv=await rooms.createInvitation(db,{...auth("owner"),data:{roomId}});
  for(let i=5;i<=11;i++){
    const uid="player"+i;
    await rooms.requestJoin(db,{...auth(uid,"친구"+i),data:{code:inv.code}});
  }
  const {sessionId}=await game.createGameSession(db,{...auth("owner"),data:{
    roomId,gameId:"identity",mode:"realtime",requestId:reqId(96)
  }});
  let rev=0;
  const ten=[...members.map(x=>x[0]),...Array.from({length:6},(_,i)=>"player"+(i+5))];
  for(const uid of ten.slice(1)){
    const joined=await action(roomId,sessionId,uid,rev,"join",{},100+rev);
    rev=joined.revision;
  }
  await assert.rejects(action(roomId,sessionId,"player11",rev,
    "join",{},150),e=>e.code==="resource-exhausted");
  for(const uid of ten){
    const ready=await action(roomId,sessionId,uid,rev,"set_ready",{ready:true},200+rev);
    rev=ready.revision;
  }
  const begun=await action(roomId,sessionId,"owner",rev,"start",{},250);
  assert.equal(begun.phase,"playing");
  const snap=await db.collection("rooms").doc(roomId).collection("gameSessions").doc(sessionId).get();
  assert.equal(snap.get("publicState.options").length,10);
  const assignments=await snap.ref.collection("privateAssignments").get();
  assert.equal(assignments.size,10);
  assert.equal(new Set(assignments.docs.map(d=>d.get("data.alias"))).size,10);
});

test("server clock blocks late anonymous chat and conflicting identity votes",async()=>{
  const {roomId,members}=await makeRoom();
  const {sessionId,revision}=await startRoom(roomId,members);
  await assert.rejects(identityChat.sendIdentityMessage(db,{...auth("owner"),data:{
    roomId,sessionId,requestId:messageReqId(3),text:"늦은 메시지"
  }},()=>1762935780001),e=>e.code==="failed-precondition");
  const advance=await action(roomId,sessionId,"owner",revision,"advance",{},81,1762935780001);
  const badVote={};
  await assert.rejects(action(roomId,sessionId,"owner",advance.revision,"submit",
    {choice:badVote},82,1762935780100),e=>e.code==="invalid-argument");
  const player=await game.getMyGameSession(db,{...auth("owner"),data:{roomId,sessionId}});
  assert.equal(player.personal.submitted,false);
});

test("server scheduled sweep advances abandoned stages without client clocks",async()=>{
  const {roomId,members}=await makeRoom();
  const {sessionId}=await startRoom(roomId,members);
  const advance=await game.expireDueGameSessions(db,productionRules,()=>1762935780001);
  assert.ok(advance.advanced>=1);
  let record=await db.collection("rooms").doc(roomId).collection("gameSessions").doc(sessionId).get();
  assert.equal(record.get("phase"),"voting");
  const next=await game.expireDueGameSessions(db,productionRules,()=>1762935870002);
  assert.ok(next.advanced>=1);
  record=await record.ref.get();
  assert.equal(record.get("phase"),"reveal");
  const ended=await game.expireDueGameSessions(db,productionRules,()=>1762935880003);
  assert.ok(ended.advanced>=1);
  record=await record.ref.get();
  assert.equal(record.get("phase"),"finished");
  assert.equal((await record.ref.collection("results").get()).size,1);
  const replay=await game.expireDueGameSessions(db,productionRules,()=>1762935881000);
  assert.equal(replay.advanced,0);
});

test("membership removal prevents scheduled stage execution and cancels session",async()=>{
  const {roomId,members}=await makeRoom();
  const {sessionId}=await startRoom(roomId,members);
  await rooms.changeMember(db,{...auth("owner"),data:{
    roomId,targetUid:"player4",action:"remove"
  }});
  const result=await game.expireDueGameSessions(db,productionRules,()=>1762935780001);
  assert.ok(result.cancelled>=1);
  const doc=await db.collection("rooms").doc(roomId).collection("gameSessions").doc(sessionId).get();
  assert.equal(doc.get("phase"),"cancelled");
  assert.equal((await db.collection("rooms").doc(roomId).get()).get("activeGameSessionId"),null);
});

test("forty orphaned archived deadlines are cancelled and cannot starve live games",async()=>{
  const {roomId,members}=await makeRoom();
  const {sessionId}=await startRoom(roomId,members);
  const archived=db.collection("rooms").doc("archived-queue-regression");
  const batch=db.batch();
  const firstId="0".repeat(64);
  batch.set(archived,{status:"archived",activeGameSessionId:firstId});
  for(let i=0;i<40;i++){
    const staleId=i.toString(16).padStart(64,"0");
    batch.set(archived.collection("gameSessions").doc(staleId),{
      roomId:archived.id,sessionId:staleId,gameId:"identity",rulesVersion:1,
      phase:"playing",revision:1,phaseEndsAt:Timestamp.fromMillis(1762935700000),
      participants:{ghost:{ready:true}}
    });
  }
  await batch.commit();
  const cleaned=await game.expireDueGameSessions(db,productionRules,()=>1762935780001);
  assert.equal(cleaned.cancelled,40);
  const stale=await archived.collection("gameSessions").doc(firstId).get();
  assert.equal(stale.get("phase"),"cancelled");
  assert.equal(stale.get("phaseEndsAt"),null);
  assert.equal((await archived.get()).get("activeGameSessionId"),null);
  const progressed=await game.expireDueGameSessions(db,productionRules,()=>1762935780001);
  assert.ok(progressed.advanced>=1);
  const active=await db.collection("rooms").doc(roomId)
    .collection("gameSessions").doc(sessionId).get();
  assert.equal(active.get("phase"),"voting");
});
