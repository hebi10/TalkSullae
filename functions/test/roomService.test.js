const {test,before,after} = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp,deleteApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const service = require("../src/roomService");

let app,db;
const auth = (uid,name=uid) => ({auth:{uid,token:{name}}});
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore emulator must be running");
  app=initializeApp({projectId:"demo-talksullae"},"room-service-tests");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("invite requests are hashed; owner alone approves, transfers, removes",async()=>{
  const created=await service.createRoom(db,{...auth("owner"),data:{
    name:"우리 친구들",description:"테스트",requiresApproval:true
  }});
  const roomId=created.roomId;
  const invite=await service.createInvitation(db,{...auth("owner"),data:{roomId}});
  assert.ok(invite.code.startsWith(roomId+"."));
  assert.ok(invite.code.split(".")[1].length===43);
  const roomRef=db.collection("rooms").doc(roomId);
  const inviteRefs=await roomRef.collection("invites").get();
  assert.equal(inviteRefs.size,1);
  assert.notEqual(inviteRefs.docs[0].id,invite.code.split(".")[1]);
  await assert.rejects(service.createInvitation(db,{...auth("outsider"),data:{roomId}}));

  const pending=await service.requestJoin(db,{...auth("member","민지"),data:{code:invite.code}});
  assert.equal(pending.status,"pending");
  assert.equal((await roomRef.get()).data().pendingCount,1);
  await assert.rejects(service.changeMember(db,{
    ...auth("outsider"),data:{roomId,targetUid:"member",action:"approve"}
  }));
  const approve=await service.changeMember(db,{
    ...auth("owner"),data:{roomId,targetUid:"member",action:"approve"}
  });
  assert.equal(approve.status,"active");
  assert.equal((await roomRef.get()).data().memberCount,2);
  assert.equal((await roomRef.get()).data().pendingCount,0);
  await service.changeMember(db,{
    ...auth("owner"),data:{roomId,targetUid:"member",action:"transfer"}
  });
  assert.equal((await roomRef.get()).data().ownerId,"member");
  await assert.rejects(service.changeMember(db,{
    ...auth("owner"),data:{roomId,targetUid:"member",action:"remove"}
  }));
  await assert.rejects(service.leaveRoom(db,{...auth("member"),data:{roomId}}));
  await service.changeMember(db,{
    ...auth("member"),data:{roomId,targetUid:"owner",action:"remove"}
  });
  assert.equal((await roomRef.get()).data().memberCount,1);
  assert.equal((await roomRef.collection("members").doc("owner").get()).data().status,"removed");
  await assert.rejects(service.requestJoin(db,{...auth("owner"),data:{code:invite.code}}));
});

test("new invitation replaces prior code and closed membership cannot be spoofed",async()=>{
  const created=await service.createRoom(db,{...auth("secondOwner"),data:{
    name:"오프라인게임",description:"",requiresApproval:false
  }});
  const roomId=created.roomId;
  const oldInvite=await service.createInvitation(db,{...auth("secondOwner"),data:{roomId}});
  const newInvite=await service.createInvitation(db,{...auth("secondOwner"),data:{roomId}});
  await assert.rejects(service.requestJoin(db,{...auth("guest"),data:{code:oldInvite.code}}));
  const joined=await service.requestJoin(db,{...auth("guest"),data:{code:newInvite.code}});
  assert.equal(joined.status,"active");
  assert.equal((await db.collection("rooms").doc(roomId).get()).data().memberCount,2);
  await assert.rejects(service.changeMember(db,{...auth("guest"),data:{
    roomId,targetUid:"secondOwner",action:"remove"
  }}));
  await service.leaveRoom(db,{...auth("guest"),data:{roomId}});
  assert.equal((await db.collection("rooms").doc(roomId).get()).data().memberCount,1);
  await service.leaveRoom(db,{...auth("secondOwner"),data:{roomId}});
  assert.equal((await db.collection("rooms").doc(roomId).get()).data().status,"archived");
  await assert.rejects(service.requestJoin(db,{...auth("guest"),data:{code:newInvite.code}}));
});

test("last owner must resolve pending applications before archiving room", async()=>{
  const {roomId}=await service.createRoom(db,{...auth("pendingOwner"),data:{
    name:"가입 대기 테스트",description:"",requiresApproval:true
  }});
  const roomRef=db.collection("rooms").doc(roomId);
  const invite=await service.createInvitation(db,{...auth("pendingOwner"),data:{roomId}});
  const application=await service.requestJoin(db,{...auth("waitingMember"),data:{code:invite.code}});
  assert.equal(application.status,"pending");
  assert.equal((await roomRef.get()).data().pendingCount,1);
  await assert.rejects(service.leaveRoom(db,{...auth("pendingOwner"),data:{roomId}}));
  assert.equal((await roomRef.get()).data().status,"active");
  await service.changeMember(db,{...auth("pendingOwner"),data:{
    roomId,targetUid:"waitingMember",action:"reject"
  }});
  assert.equal((await roomRef.get()).data().pendingCount,0);
  await service.leaveRoom(db,{...auth("pendingOwner"),data:{roomId}});
  assert.equal((await roomRef.get()).data().status,"archived");
  assert.equal((await db.collection("userRooms").doc("waitingMember").collection("rooms").doc(roomId).get()).data().status,"rejected");
});

test("oversized member UID is rejected before reading Firestore",async()=>{
  const {roomId}=await service.createRoom(db,{...auth("idOwner"),data:{
    name:"방장 검증",description:"",requiresApproval:true
  }});
  await assert.rejects(service.changeMember(db,{...auth("idOwner"),data:{
    roomId,targetUid:"x".repeat(129),action:"remove"
  }}));
});
