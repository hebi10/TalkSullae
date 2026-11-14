"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const game=require("../src/game/sessionService");
const records=require("../src/game/recordService");
const {productionRules}=require("../src/game/engine");

let app,db,count=1;
const base=1762935600000;
const auth=(uid,name=uid)=>({auth:{uid,token:{name}}});
const req=()=> "records-request-"+String(count++).padStart(12,"0");
const action=(roomId,sessionId,uid,rev,kind,data={},at=base)=>
 game.gameSessionAction(db,{...auth(uid),data:{
   roomId,sessionId,gameId:"identity",rulesVersion:1,
   expectedRevision:rev,requestId:req(),payload:{kind,...data}
 }},productionRules,()=>at);
async function makeRoom(){
 const owner="owner-"+count++;
 const users=[owner,...Array.from({length:3},()=> "player-"+count++)];
 const created=await rooms.createRoom(db,{...auth(owner),data:{
   name:"경기 기록 검증방",description:"",requiresApproval:false
 }});
 const roomId=created.roomId;
 const invite=await rooms.createInvitation(db,{...auth(owner),data:{roomId}});
 for(const player of users.slice(1))await rooms.requestJoin(db,{...auth(player),data:{code:invite.code}});
 return {roomId,owner,users};
}
async function finishGame(room,at){
 const {roomId,owner,users}=room;
 const created=await game.createGameSession(db,{...auth(owner),data:{
   roomId,gameId:"identity",mode:"realtime",requestId:req()
 }});
 let revision=created.revision;
 const sessionId=created.sessionId;
 for(const player of users.slice(1)){
   revision=(await action(roomId,sessionId,player,revision,"join",{},at)).revision;
 }
 for(const player of users){
   revision=(await action(roomId,sessionId,player,revision,"set_ready",{ready:true},at)).revision;
 }
 const started=await action(roomId,sessionId,owner,revision,"start",{},at);
 const ref=db.collection("rooms").doc(roomId).collection("gameSessions").doc(sessionId);
 const assignments={};
 for(const player of users){
   assignments[player]=(await ref.collection("privateAssignments").doc(player).get()).get("data").alias;
 }
 const vote=await action(roomId,sessionId,owner,started.revision,"advance",{},at+180001);
 revision=vote.revision;
 for(const uid of users){
   const choice=Object.fromEntries(
     users.filter(u=>u!==uid).map(u=>[assignments[u],u])
   );
   revision=(await action(roomId,sessionId,uid,revision,"submit",
     {choice},at+180100)).revision;
 }
 const reveal=await action(roomId,sessionId,owner,revision,"advance",{},at+180200);
 const ended=await action(roomId,sessionId,owner,reveal.revision,"advance",{},at+190201);
 return {sessionId,ref,reveal,ended};
}
before(async()=>{
 assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
 app=initializeApp({projectId:"demo-talksullae"},"record-service-emulator");
 db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});
test("real game persists idempotent monthly season points and individual achievements",async()=>{
 const room=await makeRoom(),id=room.owner;
 const first=await finishGame(room,base);
 assert.equal(first.ended.phase,"finished");
 const parent=db.collection("rooms").doc(room.roomId);
 const season=records.currentSeason(base+190201);
 const myStats=await parent.collection("playerStats").doc(id).get();
 assert.equal(myStats.get("totalGames"),1);
 assert.equal(myStats.get("totalWins"),1);
 assert.equal(myStats.get("currentStreak"),1);
 assert.equal(myStats.get("achievements.first_game"),true);
 const score=await parent.collection("seasons").doc(season).collection("scores").doc(id).get();
 assert.equal(score.get("seasonPoints"),records.normalizedPoints("identity",300,4,true));
 assert.equal(score.get("gamesPlayed"),1);
 assert.equal((await parent.collection("recordEvents").get()).size,1);
 assert.equal((await parent.collection("recordSettlements").get()).size,1);
 const replay=await game.gameSessionAction(db,{...auth(room.owner),data:{
   roomId:room.roomId,sessionId:first.sessionId,gameId:"identity",rulesVersion:1,
   expectedRevision:first.ended.revision,requestId:"records-final-replay-key-111111111",
   payload:{kind:"advance"}
 }},productionRules,()=>base+190210).catch(e=>e);
 assert.equal(replay.code,"failed-precondition");
 assert.equal((await parent.collection("playerStats").doc(id).get()).get("totalGames"),1);
 assert.equal((await parent.collection("seasons").doc(season)
   .collection("scores").doc(id).get()).get("gamesPlayed"),1);
 const second=await finishGame(room,base+200000);
 assert.equal(second.ended.phase,"finished");
 const updated=await parent.collection("playerStats").doc(id).get();
 assert.equal(updated.get("totalGames"),2);
 assert.equal(updated.get("totalWins"),2);
 assert.equal(updated.get("currentStreak"),2);
 assert.equal(updated.get("bestStreak"),2);
 const points=(await parent.collection("seasons").doc(season).collection("scores").doc(id).get());
 assert.equal(points.get("gamesPlayed"),2);
 assert.equal(points.get("seasonPoints"),2*records.normalizedPoints("identity",300,4,true));
 assert.equal((await parent.collection("recordEvents").get()).size,2);
 assert.equal((await parent.collection("recordSettlements").get()).size,2);
});
