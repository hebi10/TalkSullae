"use strict";

const {test,before,after}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const {saveMyProfile}=require("../src/userProfile");
const room=require("../src/roomService");

let db,app;
const auth=(uid,name="Google 표시명")=>({auth:{uid,token:{name}}});
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
  app=initializeApp({projectId:"demo-talksullae"},"live01-profile");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("profile nickname is used in existing and newly joined rooms",async()=>{
  const owner="live01-owner",member="live01-member";
  const old=await room.createRoom(db,{...auth(owner),data:{
    name:"프로필 동기화 방",description:"",requiresApproval:false
  }});
  const roomRef=db.collection("rooms").doc(old.roomId);
  const first=await roomRef.collection("members").doc(owner).get();
  assert.equal(first.get("displayName"),"Google 표시명");
  await saveMyProfile(db,{...auth(owner),data:{nickname:"새로운별명",avatar:"고양이"}});
  assert.equal((await db.collection("users").doc(owner).get()).get("nickname"),"새로운별명");
  assert.equal((await roomRef.collection("members").doc(owner).get()).get("displayName"),"새로운별명");
  const invite=await room.createInvitation(db,{...auth(owner),data:{roomId:old.roomId}});
  await saveMyProfile(db,{...auth(member),data:{nickname:"참여자별명",avatar:"토끼"}});
  await room.requestJoin(db,{...auth(member),data:{code:invite.code}});
  assert.equal((await roomRef.collection("members").doc(member).get()).get("displayName"),"참여자별명");
  await saveMyProfile(db,{...auth(member),data:{nickname:"변경한별명",avatar:"부엉이"}});
  assert.equal((await roomRef.collection("members").doc(member).get()).get("displayName"),"변경한별명");
  assert.equal((await roomRef.collection("members").doc(member).get()).get("avatar"),"부엉이");
});

test("authenticated user cannot alter another UID or recreate a queued deletion",async()=>{
  const uid="live01-deleting";
  await saveMyProfile(db,{...auth(uid),data:{nickname:"삭제중계정",avatar:"토끼"}});
  await db.collection("accountDeletionJobs").doc(uid).set({status:"queued"});
  await assert.rejects(saveMyProfile(db,{...auth(uid),data:{
    nickname:"다시생성",avatar:"토끼"
  }}),e=>e.code==="permission-denied");
  assert.equal((await db.collection("users").doc(uid).get()).get("nickname"),"삭제중계정");
});

test("new avatar survives room join while the installed legacy app still reads a valid profile",async()=>{
 const uid="r2-extended-avatar";
 const saved=await saveMyProfile(db,{...auth(uid),data:{nickname:"호환프로필",avatar:"펭귄"}});
 assert.equal(saved.avatar,"펭귄");
 const userRef=db.collection("users").doc(uid);
 assert.equal((await userRef.get()).get("avatar"),"부엉이");
 assert.equal((await userRef.get()).get("avatarV2"),"펭귄");
 const {roomId}=await room.createRoom(db,{...auth(uid),data:{name:"새 아바타",description:"",requiresApproval:false}});
 const memberRef=db.collection("rooms").doc(roomId).collection("members").doc(uid);
 assert.equal((await memberRef.get()).get("avatar"),"펭귄");
 await saveMyProfile(db,{...auth(uid),data:{nickname:"구버전수정",avatar:"토끼"}});
 assert.equal((await userRef.get()).get("avatarV2"),"토끼");
 assert.equal((await memberRef.get()).get("avatar"),"토끼");
});
test("inactive room history does not block profile editing",async()=>{
 const uid="r2-profile-history";
 const parent=db.collection("userRooms").doc(uid).collection("rooms");
 const batch=db.batch();
 for(let i=0;i<105;i++)batch.set(parent.doc("historical-"+i),{status:"left"});
 await batch.commit();
 const saved=await saveMyProfile(db,{...auth(uid),data:{nickname:"이력보존",avatar:"고양이"}});
 assert.equal(saved.nickname,"이력보존");
});
