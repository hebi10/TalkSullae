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
const deviceId="123e4567-e89b-42d3-a456-426614174000";
const body=(enabled,revision,value=token)=>({token:value,enabled,deviceId,revision});
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
  app=initializeApp({projectId:"demo-talksullae"},"live05-push");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("device token rotation preserves one registration and old writes cannot reverse opt-out",async()=>{
  const uid="push-owner";
  await db.collection("users").doc(uid).set({nickname:"알림사용자"});
  await assert.rejects(registerPushToken(db,{data:body(true,1)}),
    e=>e.code==="unauthenticated");
  await registerPushToken(db,{...auth(uid),data:body(true,1)});
  const devices=db.collection("users").doc(uid).collection("pushDevices");
  assert.equal((await devices.get()).size,1);
  assert.notEqual((await devices.get()).docs[0].id,deviceId);
  const rotated=token+"rotated";
  await registerPushToken(db,{...auth(uid),data:body(true,1,rotated)});
  assert.equal((await devices.get()).size,1);
  assert.equal((await devices.get()).docs[0].get("token"),rotated);
  await registerPushToken(db,{...auth(uid),data:body(false,2,rotated)});
  assert.equal((await devices.get()).docs[0].get("enabled"),false);
  const delayed=await registerPushToken(db,{...auth(uid),data:body(true,1,rotated)});
  assert.equal(delayed.applied,false);
  assert.equal(delayed.enabled,false);
  assert.equal((await devices.get()).docs[0].get("enabled"),false);
});

test("room event sends generic data only to verified recipients",async()=>{
  const owner="push-sender",guest="push-receiver";
  const created=await createRoom(db,{...auth(owner),data:{
    name:"알림 테스트 방",description:"",requiresApproval:false
  }});
  const roomId=created.roomId;
  const invite=await createInvitation(db,{...auth(owner),data:{roomId}});
  await requestJoin(db,{...auth(guest),data:{code:invite.code}});
  await db.collection("users").doc(guest).set({nickname:"받는사람"});
  await registerPushToken(db,{...auth(guest),data:body(true,1)});
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
  const invalidMessaging={sendEach:async values=>({
    successCount:0,
    responses:values.map(()=>({
      success:false,error:{code:"messaging/registration-token-not-registered"}
    }))
  })};
  await notifyRoomActivity(db,roomId,"game",owner,invalidMessaging,()=>1763160002000);
  const device=(await db.collection("users").doc(guest)
    .collection("pushDevices").get()).docs[0];
  assert.equal((await device.ref.get()).get("enabled"),true);
  assert.equal((await device.ref.get()).get("token"),null);
  const fresh=token+"fresh";
  const rotated=await registerPushToken(db,{...auth(guest),data:body(true,1,fresh)});
  assert.equal(rotated.applied,true);
  assert.equal((await device.ref.get()).get("enabled"),true);
  assert.equal((await device.ref.get()).get("token"),fresh);
});
