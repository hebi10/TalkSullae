"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const game=require("../src/game/sessionService");
const chat=require("../src/game/forbiddenChatService");
const {productionRules}=require("../src/game/engine");

let app,db,seq=1;
const baseline=1762935600000;
const auth=uid=>({auth:{uid,token:{name:uid}}});
const req=n=>"game-04-b-request-"+String(n).padStart(10,"0");
const chatId=n=>"123e4567-e89b-42d3-a456-"+String(n).padStart(12,"0");

async function action(roomId,sessionId,uid,revision,kind,extra={},now=baseline){
 return game.gameSessionAction(db,{...auth(uid),data:{
   roomId,sessionId,gameId:"forbidden",rulesVersion:1,
   expectedRevision:revision,requestId:req(seq++),payload:{kind,...extra}
 }},productionRules,()=>now);
}
async function setup(size=3){
 const owner="owner-"+seq++;
 const ids=[owner,...Array.from({length:size-1},()=> "member-"+seq++)];
 const {roomId}=await rooms.createRoom(db,{...auth(owner),data:{
   name:"금지어 채팅방",description:"",requiresApproval:false
 }});
 const invite=await rooms.createInvitation(db,{...auth(owner),data:{roomId}});
 for(const uid of ids.slice(1)){
  await rooms.requestJoin(db,{...auth(uid),data:{code:invite.code}});
 }
 const created=await game.createGameSession(db,{...auth(owner),data:{
   roomId,gameId:"forbidden",mode:"realtime",requestId:req(seq++)
 }});
 let rev=created.revision;
 for(const uid of ids.slice(1))rev=(await action(roomId,created.sessionId,uid,rev,"join")).revision;
 for(const uid of ids)rev=(await action(roomId,created.sessionId,uid,rev,"set_ready",{ready:true})).revision;
 const started=await action(roomId,created.sessionId,owner,rev,"start");
 const ref=db.collection("rooms").doc(roomId).collection("gameSessions").doc(created.sessionId);
 const assignments={};
 for(const uid of ids){
   assignments[uid]=(await ref.collection("privateAssignments").doc(uid).get()).get("data");
 }
 return {owner,ids,roomId,sessionId:created.sessionId,ref,revision:started.revision,assignments};
}

before(async()=>{
 assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
 app=initializeApp({projectId:"demo-talksullae"},"forbidden-emulator");
 db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("three players' own forbidden words are hidden; word triggers elimination and final one-time result",async()=>{
 const s=await setup();
 const publicState=(await s.ref.get()).get("publicState");
 assert.equal(publicState.eliminatedUids.length,0);
 const words=Object.values(s.assignments).map(x=>x.forbiddenWord);
 assert.equal(new Set(words).size,3);
 for(const uid of s.ids){
   const personal=await game.getMyGameSession(db,{...auth(uid),data:{
     roomId:s.roomId,sessionId:s.sessionId
   }});
   assert.equal(personal.personal.otherWords.length,2);
   assert.ok(!personal.personal.otherWords.some(x=>x.uid===uid));
   assert.ok(!personal.personal.otherWords.some(x=>x.word===s.assignments[uid].forbiddenWord));
   assert.equal(JSON.stringify(personal.public).includes(s.assignments[uid].forbiddenWord),false);
 }
 await assert.rejects(game.getMyGameSession(db,{...auth("outsider"),data:{
   roomId:s.roomId,sessionId:s.sessionId
 }}),e=>e.code==="permission-denied");
 const player=s.ids[1];
 const message={roomId:s.roomId,sessionId:s.sessionId,
   requestId:chatId(seq++),text:"오늘은 "+s.assignments[player].forbiddenWord+" 먹고 싶어요"};
 const first=await chat.sendForbiddenMessage(db,{...auth(player),data:message},()=>baseline+1000);
 assert.equal(first.triggered,true);
 assert.equal(first.deduplicated,false);
 const repeat=await chat.sendForbiddenMessage(db,{...auth(player),data:message},()=>baseline+1001);
 assert.equal(repeat.deduplicated,true);
 assert.equal((await s.ref.get()).get("publicState.eliminatedUids")[0],player);
 const payload=(await s.ref.collection("publicMessages").doc(first.messageId).get()).data();
 assert.equal(payload.senderUid,player);
 assert.equal(payload.triggered,true);
 assert.equal(payload.forbiddenWord,undefined);
 await assert.rejects(chat.sendForbiddenMessage(db,{...auth(player),data:{
   ...message,requestId:chatId(seq++)
 }},()=>baseline+2000),e=>e.code==="failed-precondition");
 await assert.rejects(chat.sendForbiddenMessage(db,{...auth("outsider"),data:{
   ...message,requestId:chatId(seq++)
 }},()=>baseline+2000),e=>e.code==="permission-denied");
 const current=await s.ref.get();
 const revealed=await action(s.roomId,s.sessionId,s.owner,current.get("revision"),
   "advance",{},baseline+180001);
 assert.equal(revealed.phase,"reveal");
 assert.ok((await s.ref.get()).get("publicState.publishedResult").includes("탈락"));
 const finished=await action(s.roomId,s.sessionId,s.owner,revealed.revision,
   "advance",{},baseline+190500);
 assert.equal(finished.phase,"finished");
 const final=await s.ref.collection("results").doc("final").get();
 assert.equal(final.exists,true);
 assert.equal(final.get("scoresByUid")[player],0);
 for(const id of s.ids.filter(id=>id!==player))assert.equal(final.get("scoresByUid")[id],100);
 assert.equal((await s.ref.collection("results").get()).size,1);
});

test("ten players may start; 11th participant cannot join a running game",async()=>{
 const s=await setup(10);
 assert.equal((await s.ref.collection("privateAssignments").get()).size,10);
 const invite=await rooms.createInvitation(db,{...auth(s.owner),data:{roomId:s.roomId}});
 const outsider="other-"+seq++;
 await rooms.requestJoin(db,{...auth(outsider),data:{code:invite.code}});
 await assert.rejects(action(s.roomId,s.sessionId,outsider,s.revision,"join"),
   error=>error.code==="failed-precondition");
});
test("safe chat respects server rate and deadline without client clocks",async()=>{
 const s=await setup();
 const who=s.ids[0];
 const msg={roomId:s.roomId,sessionId:s.sessionId,
   requestId:chatId(seq++),text:"좋은 하루입니다"};
 assert.equal((await chat.sendForbiddenMessage(db,{...auth(who),data:msg},
   ()=>baseline+1000)).triggered,false);
 await assert.rejects(chat.sendForbiddenMessage(db,{...auth(who),data:{
   ...msg,requestId:chatId(seq++),text:"두 번째 메시지"
 }},()=>baseline+1100),e=>e.code==="resource-exhausted");
 await assert.rejects(chat.sendForbiddenMessage(db,{...auth(who),data:{
   ...msg,requestId:chatId(seq++),text:"시간 종료"
 }},()=>baseline+180001),e=>e.code==="failed-precondition");
});
