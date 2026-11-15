"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const {saveMyProfile}=require("../src/userProfile");
const rooms=require("../src/roomService");
const {sendRoomMessage}=require("../src/chatService");
const game=require("../src/game/sessionService");
const {getMyCareer}=require("../src/game/careerService");
const {setUserBlock,reportChatMessage,requestAccountDeletion}
  =require("../src/safetyControls");
const {getModerationInbox,setModerationStatus}
  =require("../src/moderationService");

let app,db;
const auth=(uid,name)=>({auth:{uid,token:{name}}});
const owner=auth("live08-owner","구글 계정 표시명");
const invited=auth("live08-invited","다른 구글 계정 표시명");
const outsider=auth("live08-outsider","방 외부 계정");
const moderator={auth:{uid:"live08-admin",token:{talksullaeModerator:true}}};

before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,
    "LIVE-08 requires Firestore Emulator; refusing real Firestore connections");
  app=initializeApp({projectId:"demo-talksullae"},"live08-user-journey");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("two distinct Google UIDs can join and chat, with least privilege and safety audit",async()=>{
  await saveMyProfile(db,{...owner,data:{nickname:"방장닉네임",avatar:"고양이"}});
  await saveMyProfile(db,{...invited,data:{nickname:"친구닉네임",avatar:"토끼"}});
  const created=await rooms.createRoom(db,{...owner,data:{
    name:"친구 모임채팅",description:"동아리 멤버만",requiresApproval:true
  }});
  const roomId=created.roomId;
  const roomRef=db.collection("rooms").doc(roomId);
  assert.equal((await roomRef.collection("members").doc(owner.auth.uid).get())
    .get("displayName"),"방장닉네임");

  const invitation=await rooms.createInvitation(db,{
    ...owner,data:{roomId}
  });
  const preview=await rooms.previewInvitation(db,{
    ...invited,data:{code:invitation.code}
  });
  assert.equal(preview.roomId,roomId);
  assert.equal(preview.roomName,"친구 모임채팅");
  assert.equal(preview.requiresApproval,true);
  assert.equal(preview.inviteHash,undefined);
  await assert.rejects(rooms.previewInvitation(db,{
    ...outsider,data:{code:"invalid-code"}
  }),e=>e.code==="invalid-argument");

  const join=await rooms.requestJoin(db,{
    ...invited,data:{code:invitation.code}
  });
  assert.equal(join.status,"pending");
  const requestId="123e4567-e89b-42d3-a456-426614174001";
  await assert.rejects(sendRoomMessage(db,{...invited,data:{
    roomId,requestId,text:"승인 전 공개되어서는 안 됩니다"
  }}),e=>e.code==="permission-denied");
  await assert.rejects(rooms.changeMember(db,{...invited,data:{
    roomId,targetUid:outsider.auth.uid,action:"approve"
  }}),e=>e.code==="permission-denied");

  const approved=await rooms.changeMember(db,{
    ...owner,data:{roomId,targetUid:invited.auth.uid,action:"approve"}
  });
  assert.equal(approved.status,"active");
  const first=await sendRoomMessage(db,{
    ...invited,data:{roomId,requestId,text:"모임에서 만나요"}
  });
  assert.equal(first.deduplicated,false);
  const repeated=await sendRoomMessage(db,{
    ...invited,data:{roomId,requestId,text:"모임에서 만나요"}
  });
  assert.equal(repeated.messageId,first.messageId);
  assert.equal(repeated.deduplicated,true);
  await assert.rejects(sendRoomMessage(db,{
    ...invited,data:{roomId,requestId,text:"같은 요청으로 다른 메시지"}
  }),e=>e.code==="already-exists");
  const stored=await roomRef.collection("messages").doc(first.messageId).get();
  assert.equal(stored.get("senderName"),"친구닉네임");
  assert.equal(stored.get("senderUid"),invited.auth.uid);
  await assert.rejects(sendRoomMessage(db,{...outsider,data:{
    roomId,requestId:"123e4567-e89b-42d3-a456-426614174002",text:"무단 전송"
  }}),e=>e.code==="permission-denied");

  const safety=await reportChatMessage(db,{...owner,data:{
    roomId,messageId:first.messageId,
    requestId:"123e4567-e89b-42d3-a456-426614174003",
    category:"abuse",details:"해당 메시지 운영 정책 위반 신고"
  }});
  assert.match(safety.reportId,/^[0-9a-f]{64}$/);
  await assert.rejects(getModerationInbox(db,{...owner,data:{}}),
    e=>e.code==="permission-denied");
  const reviewed=await setModerationStatus(db,{...moderator,data:{
    reportId:safety.reportId,
    requestId:"123e4567-e89b-42d3-a456-426614174004",
    status:"under_review",reason:"이용자 신고의 메시지 증거 검증 중"
  }});
  assert.equal(reviewed.status,"under_review");
  const blocked=await setUserBlock(db,{...owner,data:{
    targetUid:invited.auth.uid,blocked:true
  }});
  assert.equal(blocked.blocked,true);

  const active=await getMyCareer(db,{...owner,data:{}});
  const invitedStats=await getMyCareer(db,{...invited,data:{}});
  assert.equal(active.roomCount,1);
  assert.equal(invitedStats.roomCount,1);
  assert.equal(active.totalGames,0);
  await assert.rejects(getMyCareer(db,{...outsider,data:{
    uid:owner.auth.uid
  }}),e=>e.code==="invalid-argument");
  const session=await game.createGameSession(db,{...owner,data:{
    roomId,gameId:"balance",mode:"async",
    requestId:"live08-unique-async-session-00001"
  }});
  assert.equal(session.phase,"waiting");
  const found=await game.findActiveGameSession(db,{...owner,data:{roomId}});
  assert.equal(found.sessionId,session.sessionId);
  await assert.rejects(game.findActiveGameSession(db,{...outsider,data:{roomId}}),
    e=>e.code==="permission-denied");

  // Account deletion remains fail-closed while a room owner still has active
  // responsibility and while an active server game is in progress.
  const currentAuth={...owner,auth:{...owner.auth,token:{
    name:"구글 계정 표시명",auth_time:Math.floor(Date.now()/1000)
  }}};
  await assert.rejects(requestAccountDeletion(db,{...currentAuth,data:{
    expectedUid:owner.auth.uid,confirm:"DELETE"
  }}),e=>e.code==="failed-precondition");
  assert.equal((await db.collection("accountDeletionJobs")
    .doc(owner.auth.uid).get()).exists,false);
});
