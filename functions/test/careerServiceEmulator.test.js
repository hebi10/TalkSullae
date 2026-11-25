"use strict";
const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const {getMyCareer}=require("../src/game/careerService");

let app,db;
const auth=uid=>({auth:{uid,token:{name:uid}}});
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
  app=initializeApp({projectId:"demo-talksullae"},"live05-career");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("my career includes only verified own active memberships",async()=>{
  const owner="career-owner",guest="career-guest";
  const created=await rooms.createRoom(db,{...auth(owner),data:{
    name:"전적 확인 방",description:"",requiresApproval:false
  }});
  const ref=db.collection("rooms").doc(created.roomId);
  await ref.collection("playerStats").doc(owner).set({
    totalGames:3,totalWins:2,totalSeasonPoints:200,bestStreak:2,
    games:{identity:{plays:3,wins:2}},achievements:{first_game:true}
  });
  const ownerResult=await getMyCareer(db,{...auth(owner),data:{}});
  assert.equal(ownerResult.totalGames,3);
  assert.equal(ownerResult.roomCount,1);
  assert.equal(ownerResult.games.identity.wins,2);
  const outsider=await getMyCareer(db,{...auth(guest),data:{}});
  assert.equal(outsider.totalGames,0);
  await assert.rejects(getMyCareer(db,{...auth(guest),data:{uid:owner}}),
    error=>error.code==="invalid-argument");
  await db.collection("accountDeletionJobs").doc(owner).set({status:"queued"});
  await assert.rejects(getMyCareer(db,{...auth(owner),data:{}}),
    error=>error.code==="permission-denied");
});


test("inactive room history does not exhaust the active career limit",async()=>{
  const uid="career-history-user";
  const parent=db.collection("userRooms").doc(uid).collection("rooms");
  const batch=db.batch();
  for(let i=0;i<55;i++){
    batch.set(parent.doc("left-room-"+i),{status:"left",roomName:"과거 방"});
  }
  await batch.commit();
  const summary=await getMyCareer(db,{...auth(uid),data:{}});
  assert.equal(summary.roomCount,0);
  assert.equal(summary.totalGames,0);
});

test("retained career pages preserve own historical counters without exposing room contents",async()=>{
 const uid="r3-retained-career",owner="r3-retained-owner";
 const {roomId}=await rooms.createRoom(db,{...auth(owner),data:{name:"과거 기록 방",description:"",requiresApproval:false}});
 const inv=await rooms.createInvitation(db,{...auth(owner),data:{roomId}});
 await rooms.requestJoin(db,{...auth(uid),data:{code:inv.code}});
 const ref=db.collection("rooms").doc(roomId);
 await ref.collection("playerStats").doc(uid).set({totalGames:4,totalWins:2,totalSeasonPoints:80,bestStreak:2,
   games:{balance:{plays:4,wins:2}},achievements:{first_game:true}});
 await ref.collection("playerStats").doc(owner).set({totalGames:99,totalWins:99});
 await rooms.leaveRoom(db,{...auth(uid),data:{roomId}});
 assert.equal((await getMyCareer(db,{...auth(uid),data:{}})).totalGames,0);
 const index=db.collection("userRooms").doc(uid).collection("rooms");
 const batch=db.batch();
 for(let i=0;i<26;i++){
   const id="r3Past"+String(i).padStart(14,"0");
   const room=db.collection("rooms").doc(id);
   batch.set(index.doc(id),{status:"left",roomName:"과거 방"});
   batch.set(room,{status:"active"});
   batch.set(room.collection("members").doc(uid),{status:"left"});
   batch.set(room.collection("playerStats").doc(uid),{totalGames:1,totalWins:0, games:{identity:{plays:1,wins:0}}});
 }
 await batch.commit();
 const first=await getMyCareer(db,{...auth(uid),data:{scope:"retained"}});
 assert.ok(first.nextCursor);
 const second=await getMyCareer(db,{...auth(uid),data:{scope:"retained",cursor:first.nextCursor}});
 assert.equal(second.nextCursor,null);
 assert.equal(first.totalGames+second.totalGames,30);
 assert.equal(first.totalWins+second.totalWins,2);
 assert.equal(first.recordedRoomCount+second.recordedRoomCount,27);
 assert.equal(first.roomCount+second.roomCount,0);
 assert.equal(JSON.stringify(first).includes("roomName"),false);
 assert.equal(JSON.stringify(second).includes("r3-retained-owner"),false);
 await assert.rejects(getMyCareer(db,{...auth(uid),data:{scope:"retained",uid:owner}}),e=>e.code==="invalid-argument");
 await db.collection("accountDeletionJobs").doc(uid).set({status:"queued"});
 await assert.rejects(getMyCareer(db,{...auth(uid),data:{scope:"retained",cursor:first.nextCursor}}),e=>e.code==="permission-denied");
});
