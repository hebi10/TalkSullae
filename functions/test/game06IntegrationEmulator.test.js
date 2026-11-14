"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const games=require("../src/game/sessionService");
const records=require("../src/game/recordService");
const {productionRules}=require("../src/game/engine");

let app,db,sequence=0;
const BASE_TIME=1762935600000;
const DAY=86400001;
const auth=uid=>({auth:{uid,token:{name:uid}}});
const requestId=()=>"game06-qa-request-"+String(++sequence).padStart(12,"0");

async function makeRoom(){
  const participants=["qa06-owner","qa06-player2","qa06-player3","qa06-player4"];
  const owner=participants[0];
  const created=await rooms.createRoom(db,{...auth(owner),data:{
    name:"GAME-06 통합 검증방",description:"",requiresApproval:false
  }});
  const roomId=created.roomId;
  const invitation=await rooms.createInvitation(db,{...auth(owner),data:{roomId}});
  for(const uid of participants.slice(1)){
    await rooms.requestJoin(db,{...auth(uid),data:{code:invitation.code}});
  }
  return {roomId,owner,participants,ref:db.collection("rooms").doc(roomId)};
}

function action(roomId,sessionId,gameId,uid,expectedRevision,kind,extra={},at=BASE_TIME){
  return games.gameSessionAction(db,{...auth(uid),data:{
    roomId,sessionId,gameId,rulesVersion:1,expectedRevision,
    requestId:requestId(),payload:{kind,...extra}
  }},productionRules,()=>at);
}

async function create(room,gameId,mode="realtime"){
  return games.createGameSession(db,{...auth(room.owner),data:{
    roomId:room.roomId,gameId,mode,requestId:requestId()
  }});
}

before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
  app=initializeApp({projectId:"demo-talksullae"},"game-06-integration-emulator");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("four different games finish in one persistent room without leaking state or duplicating season records",async()=>{
  const room=await makeRoom();
  const gameIds=["identity","liar","forbidden","balance"];
  const sessionIds=new Set();
  let at=BASE_TIME;

  for(const gameId of gameIds){
    const created=await create(room,gameId,gameId==="balance"?"async":"realtime");
    const sessionId=created.sessionId;
    const ref=room.ref.collection("gameSessions").doc(sessionId);
    assert.equal(created.phase,"waiting");
    assert.equal(created.revision,0);
    assert.ok(!sessionIds.has(sessionId));
    sessionIds.add(sessionId);

    const active=await games.findActiveGameSession(db,{
      ...auth(room.owner),data:{roomId:room.roomId}
    });
    assert.equal(active.sessionId,sessionId);
    assert.equal(active.gameId,gameId);
    assert.equal(active.joined,true);
    await assert.rejects(games.findActiveGameSession(db,{
      ...auth("qa06-outsider"),data:{roomId:room.roomId}
    }),e=>e.code==="permission-denied");
    await assert.rejects(create(room,"balance"),e=>e.code==="failed-precondition");
    await assert.rejects(action(room.roomId,sessionId,
      gameId==="identity"?"liar":"identity",room.owner,0,"set_ready",
      {ready:true},at),e=>e.code==="failed-precondition");

    let revision=created.revision;
    for(const uid of room.participants.slice(1)){
      revision=(await action(room.roomId,sessionId,gameId,uid,revision,"join",{},at)).revision;
    }
    for(const uid of room.participants){
      revision=(await action(room.roomId,sessionId,gameId,uid,revision,
        "set_ready",{ready:true},at)).revision;
    }
    const started=await action(room.roomId,sessionId,gameId,room.owner,
      revision,"start",{},at);
    assert.equal(started.phase,"playing");
    const session=await ref.get();
    assert.equal(session.get("gameId"),gameId);
    assert.equal(Object.keys(session.get("participants")).length,4);
    assert.equal(session.get("publicState.publishedResult"),undefined);

    const view=await games.getMyGameSession(db,{
      ...auth(room.owner),data:{roomId:room.roomId,sessionId}
    });
    assert.equal(view.public.gameId,gameId);
    assert.equal(view.public.phase,"playing");
    assert.equal(view.public.privateAssignments,undefined);
    assert.equal(view.public.privateSubmissions,undefined);
    assert.equal(view.public.settlementId,undefined);
    if(gameId==="identity"){
      assert.equal(typeof view.personal.alias,"string");
    }else if(gameId==="liar"){
      const assignments=await ref.collection("privateAssignments").get();
      const topics=assignments.docs.map(doc=>doc.get("data.topic")).filter(Boolean);
      assert.equal(assignments.size,4);
      assert.equal(new Set(topics).size,1);
      assert.ok(!JSON.stringify(view.public).includes(topics[0]));
    }else if(gameId==="forbidden"){
      const own=(await ref.collection("privateAssignments").doc(room.owner).get())
        .get("data.forbiddenWord");
      assert.ok(Array.isArray(view.personal.otherWords));
      assert.equal(view.personal.otherWords.length,3);
      assert.ok(view.personal.otherWords.every(item=>item.word!==own));
    }
    await assert.rejects(games.getMyGameSession(db,{
      ...auth("qa06-outsider"),data:{roomId:room.roomId,sessionId}
    }),e=>e.code==="permission-denied");

    let state=started;
    for(let step=0;step<6 && state.phase!=="finished";step++){
      at+=DAY;
      state=await action(room.roomId,sessionId,gameId,room.owner,
        state.revision,"advance",{},at);
    }
    assert.equal(state.phase,"finished",gameId+" must reach finished");
    const result=await ref.collection("results").doc("final").get();
    assert.equal(result.exists,true);
    assert.equal(result.get("gameId"),gameId);
    assert.equal((await room.ref.collection("recordSettlements").doc(sessionId).get()).exists,true);
    await assert.rejects(action(room.roomId,sessionId,gameId,room.owner,
      state.revision,"advance",{},at+1),e=>e.code==="failed-precondition");
    const inactive=await games.findActiveGameSession(db,{
      ...auth(room.owner),data:{roomId:room.roomId}
    });
    assert.equal(inactive.sessionId,null);
    at+=1000;
  }

  assert.equal((await room.ref.collection("recordSettlements").get()).size,4);
  assert.equal((await room.ref.collection("recordEvents").get()).size,4);
  assert.equal((await room.ref.collection("stats").doc("summary").get()).get("totalGames"),4);
  const season=records.currentSeason(at);
  const seasonScores=await room.ref.collection("seasons").doc(season).collection("scores").get();
  assert.equal(seasonScores.size,4);
  for(const uid of room.participants){
    const member=await room.ref.collection("members").doc(uid).get();
    assert.equal(member.get("status"),"active");
    const stats=await room.ref.collection("playerStats").doc(uid).get();
    assert.equal(stats.get("totalGames"),4);
    assert.equal(stats.get("achievements.first_game"),true);
    for(const gameId of gameIds){
      assert.equal(stats.get("games."+gameId+".plays"),1);
    }
    assert.equal((await room.ref.collection("playerStats").doc(uid).collection("games").get()).size,4);
    const seasonScore=await room.ref.collection("seasons").doc(season)
      .collection("scores").doc(uid).get();
    assert.equal(seasonScore.get("gamesPlayed"),4);
  }
  assert.equal((await room.ref.get()).get("memberCount"),4);
  assert.equal((await room.ref.get()).get("activeGameSessionId"),null);
});

test("a cancelled round frees the room without producing a settlement",async()=>{
  const room=await makeRoom();
  const original=await create(room,"identity");
  const cancelled=await action(room.roomId,original.sessionId,"identity",
    room.owner,original.revision,"cancel");
  assert.equal(cancelled.phase,"cancelled");
  assert.equal((await room.ref.collection("recordSettlements").get()).size,0);
  assert.equal((await room.ref.collection("recordEvents").get()).size,0);
  await assert.rejects(action(room.roomId,original.sessionId,"identity",
    room.owner,cancelled.revision,"join"),e=>e.code==="failed-precondition");
  const next=await create(room,"forbidden");
  assert.notEqual(next.sessionId,original.sessionId);
  assert.equal((await games.findActiveGameSession(db,{
    ...auth(room.owner),data:{roomId:room.roomId}
  })).sessionId,next.sessionId);
  await action(room.roomId,next.sessionId,"forbidden",room.owner,
    next.revision,"cancel");
  assert.equal((await room.ref.get()).get("activeGameSessionId"),null);
  assert.equal((await room.ref.collection("recordSettlements").get()).size,0);
});
