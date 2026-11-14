"use strict";

const {before,after,beforeEach,test} = require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const roomService=require("../src/roomService");
const game=require("../src/game/sessionService");
const {makeRuleRegistry}=require("../src/game/engine");

let app,db;
let current=1762935600000;
const now=()=>current;
const auth=uid=>({auth:{uid,token:{name:uid}}});
const reqId=n=>"request-game-02-"+String(n).padStart(8,"0");

const fakeRules=makeRuleRegistry([{
  gameId:"balance",rulesVersion:1,
  prepare({participants}){
    return {
      phase:"playing",durationMs:60000,publicState:{prompt:"A 아니면 B"},
      privateByUid:Object.fromEntries(participants.map(uid=>[uid,{secret:"only-"+uid}]))
    };
  },
  validateSubmission({choice}){
    if(!["A","B"].includes(choice)){
      throw new HttpsError("invalid-argument","선택지는 A 또는 B입니다.");
    }
    return {selection:choice};
  },
  advance({session,submissions}){
    if(session.phase==="playing"){
      assert.equal(Object.keys(submissions).length,2);
      return {phase:"voting",durationMs:60000,publicState:{prompt:"친구의 선택 예측"}};
    }
    if(session.phase==="voting"){
      assert.equal(Object.keys(submissions).length,2);
      return {phase:"reveal",durationMs:5000,publicState:{publishedResult:"선택 결과 확인"}};
    }
    if(session.phase==="reveal"){
      return {phase:"finished",publicState:{publishedResult:"모두 종료"},
        scoresByUid:{owner:100,member:120}};
    }
    throw new Error("unsupported stage");
  },
  projectForPlayer({personalData}){return personalData||null;}
}]);

before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
  app=initializeApp({projectId:"demo-talksullae"},"game-02-emulator");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

async function twoMembers(){
  const {roomId}=await roomService.createRoom(db,{
    ...auth("owner"),data:{name:"게임방",description:"",requiresApproval:true}
  });
  const inv=await roomService.createInvitation(db,{...auth("owner"),data:{roomId}});
  await roomService.requestJoin(db,{...auth("member"),data:{code:inv.code}});
  await roomService.changeMember(db,{...auth("owner"),data:{
    roomId,targetUid:"member",action:"approve"
  }});
  return roomId;
}

async function created(roomId,n=1){
  return game.createGameSession(db,{...auth("owner"),data:{
    roomId,gameId:"balance",mode:"realtime",requestId:reqId(n)
  }});
}

async function action(roomId,sessionId,uid,revision,kind,payload={},n=1,rules=fakeRules){
  return game.gameSessionAction(db,{...auth(uid),data:{
    roomId,sessionId,gameId:"balance",rulesVersion:1,
    expectedRevision:revision,requestId:reqId(n),
    payload:{kind,...payload}
  }},rules,now);
}

async function readyRoom(roomId,sessionId){
  const a=await action(roomId,sessionId,"member",0,"join",{},20);
  const b=await action(roomId,sessionId,"owner",a.revision,"set_ready",{ready:true},21);
  const c=await action(roomId,sessionId,"member",b.revision,"set_ready",{ready:true},22);
  assert.equal(c.phase,"ready");
  return c.revision;
}

test("only the real active room owner can create a supported game; retry is idempotent",async()=>{
  const roomId=await twoMembers();
  await assert.rejects(game.createGameSession(db,{...auth("member"),data:{
    roomId,gameId:"balance",mode:"realtime",requestId:reqId(1)
  }}),e=>e.code==="permission-denied");
  const first=await created(roomId);
  assert.equal(first.phase,"waiting");
  assert.equal(first.revision,0);
  assert.match(first.sessionId,/^[a-f0-9]{64}$/);
  const second=await created(roomId);
  assert.equal(second.sessionId,first.sessionId);
  assert.equal(second.deduplicated,true);
  await assert.rejects(created(roomId,2),e=>e.code==="failed-precondition");
  await assert.rejects(game.createGameSession(db,{...auth("owner"),data:{
    roomId,gameId:"identity",mode:"async",requestId:reqId(3)
  }}));
});

test("join/ready requires versioned revision and authoritative active membership",async()=>{
  const roomId=await twoMembers();
  const start=await created(roomId,7);
  const joined=await action(roomId,start.sessionId,"member",0,"join",{},30);
  assert.equal(joined.phase,"waiting");
  await assert.rejects(action(roomId,start.sessionId,"owner",0,"set_ready",
    {ready:true},31),e=>e.code==="aborted");
  const a=await action(roomId,start.sessionId,"owner",1,"set_ready",{ready:true},31);
  const b=await action(roomId,start.sessionId,"member",a.revision,"set_ready",{ready:true},32);
  assert.equal(b.phase,"ready");
  const exactRetry=await action(roomId,start.sessionId,"owner",1,"set_ready",{ready:true},31);
  assert.equal(exactRetry.deduplicated,true);
  assert.equal(exactRetry.revision,a.revision);
  await assert.rejects(action(roomId,start.sessionId,"owner",1,"set_ready",
    {ready:false},31),e=>e.code==="already-exists");

  await roomService.changeMember(db,{...auth("owner"),data:{
    roomId,targetUid:"member",action:"remove"
  }});
  await assert.rejects(action(roomId,start.sessionId,"member",b.revision,
    "set_ready",{ready:true},33),e=>e.code==="permission-denied");
  await assert.rejects(action(roomId,start.sessionId,"owner",b.revision,
    "start",{},34),e=>e.code==="failed-precondition");
});

test("authenticated members recover active session after restart; outsiders are denied",async()=>{
  const roomId=await twoMembers();
  const createdSession=await created(roomId,70);
  const forOwner=await game.findActiveGameSession(db,{
    ...auth("owner"),data:{roomId}
  });
  const forMember=await game.findActiveGameSession(db,{
    ...auth("member"),data:{roomId}
  });
  assert.equal(forOwner.sessionId,createdSession.sessionId);
  assert.equal(forOwner.joined,true);
  assert.equal(forMember.sessionId,createdSession.sessionId);
  assert.equal(forMember.joined,false);
  await assert.rejects(game.findActiveGameSession(db,{
    ...auth("attacker"),data:{roomId}
  }),e=>e.code==="permission-denied");
  await assert.rejects(game.getMyGameSession(db,{
    ...auth("member"),data:{roomId,sessionId:createdSession.sessionId}
  }),e=>e.code==="permission-denied");
  const cancelled=await action(roomId,createdSession.sessionId,"owner",0,
    "cancel",{},71);
  assert.equal(cancelled.phase,"cancelled");
  const after=await game.findActiveGameSession(db,{...auth("owner"),data:{roomId}});
  assert.equal(after.sessionId,null);
});

test("audited balance server rule starts with no role secrets",async()=>{
  const roomId=await twoMembers();
  const start=await created(roomId,8);
  const revision=await readyRoom(roomId,start.sessionId);
  const begun=await game.gameSessionAction(db,{...auth("owner"),data:{
    roomId,sessionId:start.sessionId,gameId:"balance",rulesVersion:1,
    expectedRevision:revision,requestId:reqId(41),payload:{kind:"start"}
  }},undefined,now);
  assert.equal(begun.phase,"playing");
  const snap=await db.collection("rooms").doc(roomId)
    .collection("gameSessions").doc(start.sessionId).get();
  assert.equal((await snap.ref.collection("privateAssignments").get()).size,0);
  assert.equal(snap.get("settlementId"),null);
});

test("injected audited test rule drives timed submission, private recovery and once-only settlement",async()=>{
  current=1762935600000;
  const roomId=await twoMembers();
  const initial=await created(roomId,9);
  let rev=await readyRoom(roomId,initial.sessionId);
  const started=await action(roomId,initial.sessionId,"owner",rev,"start",{},45);
  assert.equal(started.phase,"playing");
  rev=started.revision;
  const viewOwner=await game.getMyGameSession(db,{...auth("owner"),data:{
    roomId,sessionId:initial.sessionId
  }},fakeRules);
  const viewMember=await game.getMyGameSession(db,{...auth("member"),data:{
    roomId,sessionId:initial.sessionId
  }},fakeRules);
  assert.equal(viewOwner.personal.secret,"only-owner");
  assert.equal(viewMember.personal.secret,"only-member");
  assert.equal(JSON.stringify(viewOwner.public).includes("only-"),false);
  assert.equal(viewOwner.public.deadlineEpochMillis,current+60000);

  const first=await action(roomId,initial.sessionId,"owner",rev,"submit",{choice:"A"},46);
  rev=first.revision;
  await assert.rejects(action(roomId,initial.sessionId,"owner",rev,"submit",
    {choice:"B"},47),e=>e.code==="already-exists");
  const second=await action(roomId,initial.sessionId,"member",rev,"submit",{choice:"B"},48);
  rev=second.revision;
  const repeat=await action(roomId,initial.sessionId,"member",second.revision-1,
    "submit",{choice:"B"},48);
  assert.equal(repeat.deduplicated,true);
  const voting=await action(roomId,initial.sessionId,"member",rev,"advance",{},49);
  assert.equal(voting.phase,"voting");
  rev=voting.revision;

  const v1=await action(roomId,initial.sessionId,"owner",rev,"submit",{choice:"A"},50);
  const v2=await action(roomId,initial.sessionId,"member",v1.revision,"submit",{choice:"B"},51);
  const reveal=await action(roomId,initial.sessionId,"member",v2.revision,"advance",{},52);
  assert.equal(reveal.phase,"reveal");
  current+=60001;
  const finished=await action(roomId,initial.sessionId,"member",reveal.revision,
    "advance",{},53);
  assert.equal(finished.phase,"finished");
  const resultRef=db.collection("rooms").doc(roomId)
    .collection("gameSessions").doc(initial.sessionId).collection("results").doc("final");
  const result=await resultRef.get();
  assert.equal(result.get("scoresByUid.owner"),100);
  assert.equal(result.get("scoresByUid.member"),120);
  const settlementId=result.get("settlementId");
  assert.match(settlementId,/^[a-f0-9]{64}$/);
  const repeatFinish=await action(roomId,initial.sessionId,"member",reveal.revision,
    "advance",{},53);
  assert.equal(repeatFinish.deduplicated,true);
  assert.equal((await resultRef.get()).get("settlementId"),settlementId);
  const room=await db.collection("rooms").doc(roomId).get();
  assert.equal(room.get("activeGameSessionId"),null);
  const next=await created(roomId,10);
  assert.notEqual(next.sessionId,initial.sessionId);
});

test("server deadline rejects late submissions, and owner cancellation releases active slot",async()=>{
  current=1762935600000;
  const roomId=await twoMembers();
  const start=await created(roomId,11);
  const rev=await readyRoom(roomId,start.sessionId);
  const begun=await action(roomId,start.sessionId,"owner",rev,"start",{},54);
  current+=60001;
  await assert.rejects(action(roomId,start.sessionId,"owner",begun.revision,
    "submit",{choice:"A"},55),e=>e.code==="deadline-exceeded");
  await assert.rejects(action(roomId,start.sessionId,"member",begun.revision,
    "cancel",{},56),e=>e.code==="permission-denied");
  const cancelled=await action(roomId,start.sessionId,"owner",begun.revision,
    "cancel",{},57);
  assert.equal(cancelled.phase,"cancelled");
  await assert.rejects(action(roomId,start.sessionId,"member",cancelled.revision,
    "join",{},58),e=>e.code==="failed-precondition");
  assert.equal((await db.collection("rooms").doc(roomId).get()).get("activeGameSessionId"),null);
});
