"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const game=require("../src/game/sessionService");
const chat=require("../src/game/liarChatService");
const {productionRules}=require("../src/game/engine");

let app,db;
const baseline=1762935600000;
const uid=identity=>({auth:{uid:identity,token:{name:identity}}});
const key=n=>"liar-game-04-request-"+String(n).padStart(12,"0");
const chatKey=n=>"123e4567-e89b-42d3-a456-"+String(n).padStart(12,"0");
let sequence=1;
function action(roomId,sessionId,player,revision,kind,extra={},time=baseline){
 return game.gameSessionAction(db,{...uid(player),data:{
   roomId,sessionId,gameId:"liar",rulesVersion:1,
   expectedRevision:revision,requestId:key(sequence++),payload:{kind,...extra}
 }},productionRules,()=>time);
}
async function setup(size=4){
 const owner="owner"+sequence++;
 const {roomId}=await rooms.createRoom(db,{...uid(owner),data:{
   name:"라이어 테스트방",description:"",requiresApproval:false
 }});
 const invite=await rooms.createInvitation(db,{...uid(owner),data:{roomId}});
 const ids=[owner,...Array.from({length:size-1},(_,i)=>"player"+sequence+++"-"+i)];
 for(const player of ids.slice(1)){
   await rooms.requestJoin(db,{...uid(player),data:{code:invite.code}});
 }
 const created=await game.createGameSession(db,{...uid(owner),data:{
   roomId,gameId:"liar",mode:"realtime",requestId:key(sequence++)
 }});
 let rev=created.revision;
 for(const player of ids.slice(1)){
   rev=(await action(roomId,created.sessionId,player,rev,"join")).revision;
 }
 for(const player of ids){
   rev=(await action(roomId,created.sessionId,player,rev,"set_ready",{ready:true})).revision;
 }
 const begin=await action(roomId,created.sessionId,owner,rev,"start");
 const ref=db.collection("rooms").doc(roomId).collection("gameSessions").doc(created.sessionId);
 const roles={};
 for(const player of ids){
   roles[player]=(await ref.collection("privateAssignments").doc(player).get()).get("data");
 }
 return {roomId,sessionId:created.sessionId,owner,ids,revision:begin.revision,ref,roles};
}
before(async()=>{
 assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore emulator required");
 app=initializeApp({projectId:"demo-talksullae"},"game-04-liar-emulator");
 db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("real four-player game has private role/topic, timed chat, votes, guess and one settlement",async()=>{
 const s=await setup();
 const liar=s.ids.find(id=>s.roles[id].role==="liar");
 const citizens=s.ids.filter(id=>id!==liar);
 const topic=s.roles[citizens[0]].topic;
 const publicBefore=(await s.ref.get()).data();
 assert.equal(publicBefore.gameId,"liar");
 assert.equal(publicBefore.phase,"playing");
 assert.ok(!JSON.stringify(publicBefore).includes(topic));
 assert.ok(!JSON.stringify(publicBefore).includes("liarUid"));
 for(const player of s.ids){
   const personal=await game.getMyGameSession(db,{...uid(player),data:{
     roomId:s.roomId,sessionId:s.sessionId
   }});
   assert.equal(personal.personal.role,s.roles[player].role);
   assert.equal(personal.personal.topic,player===liar?undefined:topic);
   assert.ok(!JSON.stringify(personal.public).includes(topic));
 }
 await assert.rejects(game.getMyGameSession(db,{...uid("outsider"),data:{
   roomId:s.roomId,sessionId:s.sessionId
 }}),e=>e.code==="permission-denied");

 const request={roomId:s.roomId,sessionId:s.sessionId,requestId:chatKey(sequence++),text:"누구일까요?"};
 const first=await chat.sendLiarMessage(db,{...uid(s.owner),data:request},()=>baseline+1000);
 assert.equal(first.deduplicated,false);
 const again=await chat.sendLiarMessage(db,{...uid(s.owner),data:request},()=>baseline+1001);
 assert.equal(again.messageId,first.messageId);
 assert.equal(again.deduplicated,true);
 const message=await s.ref.collection("publicMessages").doc(first.messageId).get();
 assert.deepEqual(Object.keys(message.data()).sort(),["createdAt","senderUid","text"]);
 assert.equal(message.get("senderUid"),s.owner);
 assert.equal(message.get("topic"),undefined);
 await assert.rejects(chat.sendLiarMessage(db,{...uid("outsider"),data:{
   ...request,requestId:chatKey(sequence++)
 }},()=>baseline+1002),e=>e.code==="permission-denied");
 await assert.rejects(chat.sendLiarMessage(db,{...uid(s.owner),data:{
   ...request,requestId:chatKey(sequence++),role:"liar"
 }},()=>baseline+1003),e=>e.code==="invalid-argument");

 const voting=await action(s.roomId,s.sessionId,s.owner,s.revision,
   "advance",{},baseline+180001);
 assert.equal(voting.phase,"voting");
 let rev=voting.revision;
 for(const player of s.ids){
   const target=player===liar?citizens[0]:liar;
   rev=(await action(s.roomId,s.sessionId,player,rev,"submit",
     {choice:target},baseline+180100)).revision;
 }
 const guessed=await action(s.roomId,s.sessionId,s.owner,rev,
   "advance",{},baseline+180200);
 assert.equal(guessed.phase,"voting");
 const staged=await s.ref.get();
 assert.equal(staged.get("publicState.notice"),"guess");
 assert.equal(staged.get("publicState.publishedResult"),undefined);
 await assert.rejects(action(s.roomId,s.sessionId,citizens[0],guessed.revision,
   "submit",{choice:topic},baseline+180250),e=>e.code==="permission-denied");

 const chosen=await action(s.roomId,s.sessionId,liar,guessed.revision,
   "submit",{choice:topic},baseline+180300);
 const reveal=await action(s.roomId,s.sessionId,s.owner,chosen.revision,
   "advance",{},baseline+180400);
 assert.equal(reveal.phase,"reveal");
 assert.equal((await s.ref.get()).get("publicState.publishedResult").includes(topic),true);
 assert.equal((await s.ref.collection("results").get()).size,0);
 const finished=await action(s.roomId,s.sessionId,s.owner,reveal.revision,
   "advance",{},baseline+190500);
 assert.equal(finished.phase,"finished");
 const settled=await s.ref.collection("results").doc("final").get();
 assert.equal(settled.exists,true);
 assert.equal(settled.get("scoresByUid."+liar),100);
 for(const citizen of citizens)assert.equal(settled.get("scoresByUid."+citizen),20);
 assert.equal((await db.collection("rooms").doc(s.roomId).get()).get("activeGameSessionId"),null);
 assert.equal((await s.ref.collection("results").get()).size,1);
});

test("role server rejects duplicate ballots, self-votes and late chat, while preserving UUID retry",async()=>{
 const s=await setup();
 const liar=s.ids.find(id=>s.roles[id].role==="liar");
 const other=s.ids.find(id=>id!==liar);
 const req={roomId:s.roomId,sessionId:s.sessionId,requestId:chatKey(sequence++),text:"첫 대화"};
 await chat.sendLiarMessage(db,{...uid(other),data:req},()=>baseline+1000);
 await assert.rejects(chat.sendLiarMessage(db,{...uid(other),data:{
   ...req,requestId:chatKey(sequence++),text:"너무 빠름"
 }},()=>baseline+1100),e=>e.code==="resource-exhausted");
 await assert.rejects(chat.sendLiarMessage(db,{...uid(other),data:{
   ...req,requestId:chatKey(sequence++),text:"늦음"
 }},()=>baseline+180001),e=>e.code==="failed-precondition");
 const vote=await action(s.roomId,s.sessionId,other,s.revision,
   "advance",{},baseline+180001);
 await assert.rejects(action(s.roomId,s.sessionId,other,vote.revision,
   "submit",{choice:other},baseline+180100),e=>e.code==="invalid-argument");
 const good=await action(s.roomId,s.sessionId,other,vote.revision,
   "submit",{choice:liar},baseline+180110);
 await assert.rejects(action(s.roomId,s.sessionId,other,good.revision,
   "submit",{choice:liar},baseline+180111),e=>e.code==="already-exists");
});

test("four-person tie is revoted once and second tie gives liar victory",async()=>{
 const s=await setup();
 const liar=s.ids.find(id=>s.roles[id].role==="liar");
 const other=s.ids.find(id=>id!==liar);
 const rest=s.ids.filter(id=>id!==liar&&id!==other);
 const first=await action(s.roomId,s.sessionId,s.owner,s.revision,
   "advance",{},baseline+180001);
 let rev=first.revision;
 const voted=[[other,liar],[liar,other],[rest[0],liar],[rest[1],other]];
 for(const [voter,target] of voted){
   rev=(await action(s.roomId,s.sessionId,voter,rev,"submit",
     {choice:target},baseline+180100)).revision;
 }
 const next=await action(s.roomId,s.sessionId,s.owner,rev,
   "advance",{},baseline+180200);
 assert.equal(next.phase,"voting");
 assert.equal((await s.ref.get()).get("publicState.notice"),"revote");
 rev=next.revision;
 for(const [voter,target] of voted){
   rev=(await action(s.roomId,s.sessionId,voter,rev,"submit",
     {choice:target},baseline+180300)).revision;
 }
 const reveal=await action(s.roomId,s.sessionId,s.owner,rev,
   "advance",{},baseline+180400);
 assert.equal(reveal.phase,"reveal");
 assert.match((await s.ref.get()).get("publicState.publishedResult"),/재투표 동점/);
});

test("ten-player lobby accepts ten distinct members, denies eleventh",async()=>{
 const s=await setup(10);
 assert.equal((await s.ref.collection("privateAssignments").get()).size,10);
 assert.equal(Object.values(s.roles).filter(x=>x.role==="liar").length,1);
 const invite=await rooms.createInvitation(db,{...uid(s.owner),data:{roomId:s.roomId}});
 const newcomer="player-"+sequence++;
 await rooms.requestJoin(db,{...uid(newcomer),data:{code:invite.code}});
 await assert.rejects(action(s.roomId,s.sessionId,newcomer,s.revision,
   "join"),e=>e.code==="failed-precondition");
});

test("scheduled worker progresses liar chat deadline without client-supplied clocks",async()=>{
 const s=await setup();
 const moved=await game.expireDueGameSessions(db,productionRules,()=>baseline+180001);
 assert.ok(moved.advanced>=1);
 assert.equal((await s.ref.get()).get("phase"),"voting");
});
