"use strict";
const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const game=require("../src/game/sessionService");
const {productionRules}=require("../src/game/engine");
let app,db,seq=1;
const base=1762935600000;
const auth=uid=>({auth:{uid,token:{name:uid}}});
const req=()=> "balance-request-"+String(seq++).padStart(12,"0");
async function change(roomId,sessionId,uid,revision,kind,payload={},time=base){
 return game.gameSessionAction(db,{...auth(uid),data:{
  roomId,sessionId,gameId:"balance",rulesVersion:1,
  expectedRevision:revision,requestId:req(),payload:{kind,...payload}
 }},productionRules,()=>time);
}
async function start(n=2,mode="realtime"){
 const owner="owner-"+seq++,ids=[owner,...Array.from({length:n-1},()=> "member-"+seq++)];
 const r=await rooms.createRoom(db,{...auth(owner),data:{name:"밸런스 예측방",
  description:"",requiresApproval:false}});
 const invite=await rooms.createInvitation(db,{...auth(owner),data:{roomId:r.roomId}});
 for(const uid of ids.slice(1))await rooms.requestJoin(db,{...auth(uid),data:{code:invite.code}});
 const c=await game.createGameSession(db,{...auth(owner),data:{
  roomId:r.roomId,gameId:"balance",mode,requestId:req()
 }});
 let revision=c.revision;
 for(const uid of ids.slice(1))
   revision=(await change(r.roomId,c.sessionId,uid,revision,"join")).revision;
 for(const uid of ids)
   revision=(await change(r.roomId,c.sessionId,uid,revision,"set_ready",{ready:true})).revision;
 const started=await change(r.roomId,c.sessionId,owner,revision,"start");
 return {roomId:r.roomId,sessionId:c.sessionId,owner,ids,revision:started.revision,
   ref:db.collection("rooms").doc(r.roomId).collection("gameSessions").doc(c.sessionId)};
}
before(async()=>{
 assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore emulator required");
 app=initializeApp({projectId:"demo-talksullae"},"balance-emulator");
 db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});
test("two players privately choose A/B then predict one friend, reveal and settle once",async()=>{
 const s=await start();
 assert.equal((await s.ref.get()).get("phase"),"playing");
 assert.equal((await s.ref.collection("privateAssignments").get()).size,0);
 let rev=s.revision;
 rev=(await change(s.roomId,s.sessionId,s.ids[0],rev,"submit",{choice:"A"},base+100)).revision;
 const own=await game.getMyGameSession(db,{...auth(s.ids[0]),
  data:{roomId:s.roomId,sessionId:s.sessionId}});
 assert.equal(own.personal.submitted,true);
 assert.equal(own.personal.ownChoice,"A");
 assert.equal((await s.ref.get()).get("publicState.publishedResult"),undefined);
 await assert.rejects(change(s.roomId,s.sessionId,s.ids[0],rev,"submit",
   {choice:"B"},base+110),e=>e.code==="already-exists");
 rev=(await change(s.roomId,s.sessionId,s.ids[1],rev,"submit",{choice:"B"},base+120)).revision;
 const guessPhase=await change(s.roomId,s.sessionId,s.ids[0],rev,"advance",{},base+130);
 assert.equal(guessPhase.phase,"voting");
 assert.equal((await s.ref.get()).get("publicState.publishedResult"),undefined);
 rev=guessPhase.revision;
 rev=(await change(s.roomId,s.sessionId,s.ids[0],rev,"submit",
   {choice:{targetUid:s.ids[1],prediction:"B"}},base+140)).revision;
 rev=(await change(s.roomId,s.sessionId,s.ids[1],rev,"submit",
   {choice:{targetUid:s.ids[0],prediction:"A"}},base+150)).revision;
 const reveal=await change(s.roomId,s.sessionId,s.ids[1],rev,"advance",{},base+160);
 assert.equal(reveal.phase,"reveal");
 const pub=(await s.ref.get()).get("publicState.publishedResult");
 assert.ok(pub.includes("A:") && pub.includes("B:"));
 assert.equal((await s.ref.collection("results").get()).size,0);
 const finish=await change(s.roomId,s.sessionId,s.ids[0],reveal.revision,
   "advance",{},base+10161);
 assert.equal(finish.phase,"finished");
 const result=await s.ref.collection("results").doc("final").get();
 for(const uid of s.ids)assert.equal(result.get("scoresByUid")[uid],120);
 assert.equal((await s.ref.collection("results").get()).size,1);
 const recent=await game.findRecentCompletedGameSession(db,{
    ...auth(s.ids[0]),data:{roomId:s.roomId,gameId:"balance"}
 });
 assert.equal(recent.sessionId,s.sessionId);
 const restored=await game.getMyGameSession(db,{
   ...auth(s.ids[1]),data:{roomId:s.roomId,sessionId:s.sessionId}
 });
 assert.equal(restored.public.phase,"finished");
 assert.match(restored.public.publicState.publishedResult,/A:/);
 await assert.rejects(game.findRecentCompletedGameSession(db,{
   ...auth("outsider"),data:{roomId:s.roomId,gameId:"balance"}
 }),e=>e.code==="permission-denied");
});
test("async mode uses server deadlines and refuses late or outsider choices",async()=>{
 const s=await start(3,"async");
 const session=await s.ref.get();
 assert.equal(session.get("phaseEndsAt").toMillis()-session.get("startedAt").toMillis(),
   86400000);
 await assert.rejects(change(s.roomId,s.sessionId,"outsider",s.revision,"submit",
   {choice:"A"},base+100),e=>e.code==="permission-denied");
 await assert.rejects(change(s.roomId,s.sessionId,s.ids[0],s.revision,"submit",
   {choice:"A"},base+86400001),e=>e.code==="deadline-exceeded");
});
