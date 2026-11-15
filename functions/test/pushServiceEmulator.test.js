"use strict";
const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const {registerPushToken,notifyRoomActivity}=require("../src/pushService");
const {createRoom,createInvitation,requestJoin}=require("../src/roomService");
let app,db;
const auth=uid=>({auth:{uid,token:{name:uid}}});
const token="x".repeat(120)+":token";
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
  app=initializeApp({projectId:"demo-talksullae"},"live05-push");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("only a signed-in owner can register their own push token",async()=>{
  const uid="push-owner";
  await db.collection("users").doc(uid).set({nickname:"알림사용자"});
  await assert.rejects(registerPushToken(db,{data:{token,enabled:true}}),
    e=>e.code==="unauthenticated");
  await registerPushToken(db,{...auth(uid),data:{token,enabled:true}});
  const docs=await db.collection("users").doc(uid).collection("pushTokens").get();
  assert.equal(docs.size,1);
  assert.notEqual(docs.docs[0].id,token);
  await registerPushToken(db,{...auth(uid),data:{token,enabled:false}});
  assert.equal((await db.collection("users").doc(uid).collection("pushTokens").get()).size,0);
});

test("room event never includes chat contents or sends to outsiders",async()=>{
  const owner="push-sender",guest="push-receiver";
  const created=await createRoom(db,{...auth(owner),data:{
    name:"알림 테스트 방",description:"",requiresApproval:false
  }});
  const roomId=created.roomId;
  const invite=await createInvitation(db,{...auth(owner),data:{roomId}});
  await requestJoin(db,{...auth(guest),data:{code:invite.code}});
  await db.collection("users").doc(guest).set({nickname:"받는사람"});
  await registerPushToken(db,{...auth(guest),data:{token,enabled:true}});
  const captured=[];
  const messaging={sendEach:async messages=>{
    captured.push(...messages);
    return {successCount:messages.length,responses:messages.map(()=>({success:true}))};
  }};
  const result=await notifyRoomActivity(db,roomId,"message",owner,messaging,()=>1763160000000);
  assert.equal(result.sent,1);
  assert.equal(captured.length,1);
  assert.equal(captured[0].data.recipientUid,guest);
  assert.equal(captured[0].data.kind,"message");
  assert.equal(captured[0].notification,undefined);
  assert.equal(JSON.stringify(captured).includes("채팅 내용"),false);
  assert.equal((await notifyRoomActivity(db,roomId,"message",owner,messaging,
    ()=>1763160001000)).sent,0);
});
