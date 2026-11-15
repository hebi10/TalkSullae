"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const room=require("../src/roomService");
let app,db;
const auth=uid=>({auth:{uid,token:{name:uid}}});
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator is required");
  app=initializeApp({projectId:"demo-talksullae"},"live03-invites");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("owner invitation preview reveals room metadata only to authenticated valid code holder",async()=>{
  const created=await room.createRoom(db,{...auth("live03-owner"),data:{
    name:"토요일 게임방",description:"친구들만",requiresApproval:true
  }});
  const invitation=await room.createInvitation(db,{...auth("live03-owner"),data:{roomId:created.roomId}});
  const v=await room.previewInvitation(db,{...auth("live03-new"),data:{code:invitation.code}});
  assert.equal(v.roomId,created.roomId);
  assert.equal(v.roomName,"토요일 게임방");
  assert.equal(v.description,"친구들만");
  assert.equal(v.memberCount,1);
  assert.equal(v.requiresApproval,true);
  assert.ok(v.expiresAtMillis>Date.now());
  assert.equal(v.inviteHash,undefined);
  assert.equal(v.members,undefined);
  await assert.rejects(room.previewInvitation(db,{data:{code:invitation.code}}),e=>e.code==="unauthenticated");
  const bad=invitation.code.slice(0,-1)+(invitation.code.endsWith("Z")?"Y":"Z");
  await assert.rejects(room.previewInvitation(db,{...auth("live03-new"),data:{code:bad}}),e=>e.code==="permission-denied");
  const requested=await room.requestJoin(db,{...auth("live03-new"),data:{code:invitation.code}});
  assert.equal(requested.status,"pending");
});

test("reissued invite revokes previews from previously shared code",async()=>{
  const {roomId}=await room.createRoom(db,{...auth("live03-other"),data:{
    name:"초대 재발급",description:"",requiresApproval:false
  }});
  const first=await room.createInvitation(db,{...auth("live03-other"),data:{roomId}});
  const second=await room.createInvitation(db,{...auth("live03-other"),data:{roomId}});
  await assert.rejects(room.previewInvitation(db,{...auth("live03-friend"),data:{code:first.code}}),
    e=>e.code==="permission-denied");
  const preview=await room.previewInvitation(db,{...auth("live03-friend"),data:{code:second.code}});
  assert.equal(preview.roomId,roomId);
  await db.collection("accountDeletionJobs").doc("live03-friend").set({status:"queued"});
  await assert.rejects(room.previewInvitation(db,{...auth("live03-friend"),data:{code:second.code}}),
    e=>e.code==="permission-denied");
});
