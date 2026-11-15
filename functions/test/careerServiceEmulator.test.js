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
