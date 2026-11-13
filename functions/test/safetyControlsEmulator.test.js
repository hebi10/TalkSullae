"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore,Timestamp}=require("firebase-admin/firestore");
const rooms=require("../src/roomService");
const chat=require("../src/chatService");
const safety=require("../src/safetyControls");

let app,db;
const nowMillis=1762935600000;
const auth=(uid,name=uid)=>({auth:{uid,token:{name,auth_time:Math.floor(nowMillis/1000)}}});
const UUID=n=>"123e4567-e89b-42d3-a456-"+String(n).padStart(12,"0");

before(async()=>{
 assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
 app=initializeApp({projectId:"demo-talksullae"},"safety-01-emulator");
 db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

async function sharedRoom(){
 const owner="owner-"+UUID(Math.floor(Math.random()*100000)).slice(-8);
 const member="member-"+UUID(Math.floor(Math.random()*100000)).slice(-8);
 const {roomId}=await rooms.createRoom(db,{...auth(owner),data:{
   name:"안전 검증방",description:"",requiresApproval:false
 }});
 const invite=await rooms.createInvitation(db,{...auth(owner),data:{roomId}});
 await rooms.requestJoin(db,{...auth(member),data:{code:invite.code}});
 return {owner,member,roomId};
}
test("user block records persist in server-owned collection and can be reversed",async()=>{
 const {owner,member}=await sharedRoom();
 assert.deepEqual(await safety.setUserBlock(db,{...auth(owner),data:{
   targetUid:member,blocked:true
 }}),{targetUid:member,blocked:true});
 assert.equal((await db.collection("userSafety").doc(owner).collection("blocks").doc(member).get()).exists,true);
 await safety.setUserBlock(db,{...auth(owner),data:{targetUid:member,blocked:false}});
 assert.equal((await db.collection("userSafety").doc(owner).collection("blocks").doc(member).get()).exists,false);
});
test("reporting requires real member and real stored message; retry is idempotent",async()=>{
 const {owner,member,roomId}=await sharedRoom();
 const message=await chat.sendRoomMessage(db,{...auth(owner),data:{
   roomId,requestId:UUID(11),text:"검증을 위한 신고 대상 메시지"
 }});
 const data={roomId,messageId:message.messageId,
   requestId:UUID(12),category:"abuse",details:"반복적인 악성 메시지를 신고합니다."};
 const first=await safety.reportChatMessage(db,{...auth(member),data},()=>nowMillis);
 const replay=await safety.reportChatMessage(db,{...auth(member),data},()=>nowMillis);
 assert.equal(first.reportId,replay.reportId);
 assert.equal(replay.deduplicated,true);
 const record=await db.collection("safetyReports").doc(first.reportId).get();
 assert.equal(record.get("reportedUid"),owner);
 assert.equal(record.get("actorUid"),member);
 assert.equal(record.get("messageId"),message.messageId);
 await assert.rejects(safety.reportChatMessage(db,{...auth("outsider"),data},()=>nowMillis),
   e=>e.code==="permission-denied");
 await assert.rejects(safety.reportChatMessage(db,{...auth(member),data:{
   ...data,messageId:"f".repeat(64),requestId:UUID(13)
 }},()=>nowMillis),e=>e.code==="not-found");
});
test("recently authenticated non-owner may request deletion; owner must transfer ownership",async()=>{
 const {owner,member,roomId}=await sharedRoom();
 await assert.rejects(safety.requestAccountDeletion(db,{
   ...auth(owner),data:{confirm:"DELETE",expectedUid:owner}
 },()=>nowMillis),e=>e.code==="failed-precondition");
 const result=await safety.requestAccountDeletion(db,{
   ...auth(member),data:{confirm:"DELETE",expectedUid:member}
 },()=>nowMillis);
 assert.equal(result.status,"queued");
 const repeat=await safety.requestAccountDeletion(db,{
   ...auth(member),data:{confirm:"DELETE",expectedUid:member}
 },()=>nowMillis);
 assert.equal(repeat.status,"queued");
 const job=await db.collection("accountDeletionJobs").doc(member).get();
 assert.equal(job.get("status"),"queued");
 const other=await db.collection("rooms").doc(roomId).collection("members").doc(owner).get();
 assert.equal(other.get("status"),"active");
});
test("server deletion worker removes user chat, membership, preference and auth without harming others",async()=>{
 const {owner,member,roomId}=await sharedRoom();
 await safety.setUserBlock(db,{...auth(member),data:{targetUid:owner,blocked:true}});
 const original=await chat.sendRoomMessage(db,{...auth(owner),data:{
   roomId,requestId:UUID(40),text:"소유자가 보낸 메시지"
 }});
 const own=await chat.sendRoomMessage(db,{...auth(member),data:{
   roomId,requestId:UUID(41),text:"탈퇴자의 메시지"
 }});
 await safety.requestAccountDeletion(db,{...auth(member),data:{confirm:"DELETE",expectedUid:member}},()=>nowMillis);
 const identityRef=db.collection("rooms").doc(roomId)
   .collection("gameSessions").doc("f".repeat(64));
 await identityRef.set({
   roomId,sessionId:identityRef.id,gameId:"identity",
   phase:"finished",participants:{[member]:{ready:true}},revision:1
 });
 await identityRef.collection("privateAssignments").doc(member).set({
   data:{alias:"고양이"}
 });
 await identityRef.collection("publicMessages").doc("anonymous-message").set({
   alias:"고양이",text:"삭제할 익명 메시지"
 });
 await identityRef.collection("publicMessages").doc("other-message").set({
   alias:"토끼",text:"다른 사람의 메시지"
 });
 const deleted=[];
 const result=await safety.processOneAccountDeletion(db,member,{
   async deleteUser(uid){deleted.push(uid);}
 });
 assert.equal(result.status,"completed");
 assert.deepEqual(deleted,[member]);
 const base=db.collection("rooms").doc(roomId);
 assert.equal((await base.collection("messages").doc(own.messageId).get()).exists,false);
 assert.equal((await base.collection("messages").doc(original.messageId).get()).exists,true);
 assert.equal((await identityRef.collection("publicMessages").doc("anonymous-message").get()).exists,false);
 assert.equal((await identityRef.collection("publicMessages").doc("other-message").get()).exists,true);
 assert.equal((await base.collection("members").doc(member).get()).exists,false);
 assert.equal((await base.collection("members").doc(owner).get()).exists,true);
 assert.equal((await base.get()).get("memberCount"),1);
 assert.equal((await db.collection("userSafety").doc(member).get()).exists,false);
 assert.equal((await db.collection("accountDeletionJobs").doc(member).get()).exists,false);
});

test("a queued deletion blocks new game participation even when the user signs in again",async()=>{
 const {owner,member,roomId}=await sharedRoom();
 await safety.requestAccountDeletion(db,{
   ...auth(member),data:{confirm:"DELETE",expectedUid:member}
 },()=>nowMillis);
 const game=require("../src/game/sessionService");
 const result=await game.createGameSession(db,{
   ...auth(owner),data:{
     roomId,gameId:"identity",mode:"realtime",requestId:"safety-test-"+UUID(70)
   }
 });
 await assert.rejects(game.gameSessionAction(db,{
   ...auth(member),data:{
     roomId,sessionId:result.sessionId,gameId:"identity",rulesVersion:1,
     expectedRevision:result.revision,requestId:"safety-join-"+UUID(71),
     payload:{kind:"join"}
   }
 }),e=>e.code==="permission-denied");
});
