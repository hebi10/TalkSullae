const {before,after,beforeEach,test} = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {resolve} = require("node:path");
const {
  initializeTestEnvironment,assertSucceeds,assertFails
} = require("@firebase/rules-unit-testing");
const {doc,collection,query,orderBy,limit,getDoc,getDocs,setDoc,updateDoc,deleteDoc} = require("firebase/firestore");

let testEnv;
const roomId = "room-rules-1";

before(async()=>{
  const emulator = /^(127\.0\.0\.1|localhost):(\d+)$/.exec(process.env.FIRESTORE_EMULATOR_HOST || "");
  assert.ok(emulator, "Rules tests require a local Firestore emulator");
  testEnv = await initializeTestEnvironment({
    projectId:"demo-talksullae",
    firestore:{
      host:emulator[1],
      port:Number(emulator[2]),
      rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")
    }
  });
});

beforeEach(async()=>{
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async context=>{
    const db=context.firestore();
    await setDoc(doc(db,"rooms",roomId),{
      ownerId:"owner",status:"active",memberCount:1,pendingCount:1,name:"친구방"
    });
    await setDoc(doc(db,"rooms",roomId,"members","owner"),{status:"active"});
    await setDoc(doc(db,"rooms",roomId,"members","pending"),{status:"pending"});
    await setDoc(doc(db,"rooms",roomId,"invites","tokenHash"),{createdBy:"owner"});
    await setDoc(doc(db,"userRooms","owner","rooms",roomId),{status:"active",roomName:"친구방"});
    await setDoc(doc(db,"userRooms","pending","rooms",roomId),{status:"pending",roomName:"친구방"});
  });
});

after(async()=>{if(testEnv) await testEnv.cleanup()});

test("owner can read room and member list, but cannot write directly",async()=>{
  const db=testEnv.authenticatedContext("owner").firestore();
  await assertSucceeds(getDoc(doc(db,"rooms",roomId)));
  await assertSucceeds(getDocs(collection(db,"rooms",roomId,"members")));
  await assertFails(updateDoc(doc(db,"rooms",roomId),{name:"조작"}));
  await assertFails(setDoc(doc(db,"rooms",roomId,"members","attacker"),{status:"active"}));
  await assertFails(deleteDoc(doc(db,"rooms",roomId,"members","pending")));
});

test("pending member sees own index and membership, not room or other members",async()=>{
  const db=testEnv.authenticatedContext("pending").firestore();
  await assertSucceeds(getDoc(doc(db,"userRooms","pending","rooms",roomId)));
  await assertSucceeds(getDoc(doc(db,"rooms",roomId,"members","pending")));
  await assertFails(getDoc(doc(db,"rooms",roomId)));
  await assertFails(getDoc(doc(db,"rooms",roomId,"members","owner")));
  await assertFails(getDocs(collection(db,"rooms",roomId,"members")));
});

test("invitation hashes are inaccessible even to owner",async()=>{
  const db=testEnv.authenticatedContext("owner").firestore();
  await assertFails(getDoc(doc(db,"rooms",roomId,"invites","tokenHash")));
  await assertFails(getDocs(collection(db,"rooms",roomId,"invites")));
});

test("cross-account room indices and unknown rooms are denied",async()=>{
  const db=testEnv.authenticatedContext("attacker").firestore();
  await assertFails(getDoc(doc(db,"userRooms","owner","rooms",roomId)));
  await assertFails(getDoc(doc(db,"rooms",roomId)));
  await assertFails(setDoc(doc(db,"rooms","new"),{ownerId:"attacker"}));
  await assertFails(getDocs(collection(db,"rooms")));
});

test("unauthenticated reads denied and revoked owner loses room access",async()=>{
  const anonymous=testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anonymous,"rooms",roomId)));
  await testEnv.withSecurityRulesDisabled(async context=>{
    await updateDoc(doc(context.firestore(),"rooms",roomId,"members","owner"),{status:"removed"});
  });
  const db=testEnv.authenticatedContext("owner").firestore();
  await assertFails(getDoc(doc(db,"rooms",roomId)));
});

test("all app clients are denied reads and writes to safety reports and cooldowns", async () => {
  const collections = ["safetyReports", "safetyReportRateLimits"];
  await testEnv.withSecurityRulesDisabled(async context => {
    const admin = context.firestore();
    for (const name of collections) {
      await setDoc(doc(admin, name, "fixture"), { actorUid: "reporter" });
    }
  });
  const clients = [
    testEnv.unauthenticatedContext().firestore(),
    testEnv.authenticatedContext("reporter").firestore(),
    testEnv.authenticatedContext("owner").firestore()
  ];
  for (const db of clients) {
    for (const name of collections) {
      const ref = doc(db, name, "fixture");
      await assertFails(getDoc(ref));
      await assertFails(getDocs(collection(db, name)));
      await assertFails(setDoc(doc(db, name, "new"), { actorUid: "reporter" }));
      await assertFails(updateDoc(ref, { actorUid: "tampered" }));
      await assertFails(deleteDoc(ref));
    }
  }
});


test("active members may only read bounded message pages, never write", async () => {
  await testEnv.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(doc(db, "rooms", roomId, "messages", "msg1"), {
      senderUid:"owner", senderName:"방장", text:"안녕하세요",
      createdAt:new Date("2026-11-12T00:00:00.000Z")
    });
    await setDoc(doc(db, "rooms", roomId, "chatRateLimits", "owner"), {
      lastSentAt:new Date("2026-11-12T00:00:00.000Z")
    });
  });

  const owner=testEnv.authenticatedContext("owner").firestore();
  const pending=testEnv.authenticatedContext("pending").firestore();
  const outsider=testEnv.authenticatedContext("stranger").firestore();
  const anonymous=testEnv.unauthenticatedContext().firestore();
  const messageRef=doc(owner,"rooms",roomId,"messages","msg1");
  const msgColl=collection(owner,"rooms",roomId,"messages");

  await assertSucceeds(getDoc(messageRef));
  await assertSucceeds(getDocs(query(msgColl,orderBy("createdAt","desc"),limit(30))));
  await assertSucceeds(getDocs(query(msgColl,orderBy("createdAt","desc"),limit(10))));
  await assertFails(getDocs(query(msgColl,orderBy("createdAt","desc"),limit(31))));
  await assertFails(getDocs(msgColl));
  await assertFails(setDoc(doc(msgColl,"new"),{senderUid:"owner",text:"위조"}));
  await assertFails(updateDoc(messageRef,{text:"수정"}));
  await assertFails(deleteDoc(messageRef));

  await assertFails(getDoc(doc(pending,"rooms",roomId,"messages","msg1")));
  await assertFails(getDocs(query(collection(pending,"rooms",roomId,"messages"),limit(30))));
  await assertFails(getDoc(doc(outsider,"rooms",roomId,"messages","msg1")));
  await assertFails(getDocs(query(collection(outsider,"rooms",roomId,"messages"),limit(30))));
  await assertFails(getDocs(query(collection(anonymous,"rooms",roomId,"messages"),limit(30))));
  await assertFails(getDoc(doc(owner,"rooms",roomId,"chatRateLimits","owner")));
  await assertFails(setDoc(doc(owner,"rooms",roomId,"chatRateLimits","owner"),{lastSentAt:new Date()}));
});

test("a removed member loses message reads even after earlier membership", async () => {
  await testEnv.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(),"rooms",roomId,"messages","old"),{
      senderUid:"owner", senderName:"방장", text:"이전 메시지",
      createdAt:new Date("2026-11-12T00:00:00.000Z")
    });
  });
  const before=testEnv.authenticatedContext("owner").firestore();
  await assertSucceeds(getDocs(query(collection(before,"rooms",roomId,"messages"),limit(30))));
  await testEnv.withSecurityRulesDisabled(async context => {
    await updateDoc(doc(context.firestore(),"rooms",roomId,"members","owner"),{status:"removed"});
  });
  await assertFails(getDocs(query(collection(before,"rooms",roomId,"messages"),limit(30))));
  await assertFails(getDoc(doc(before,"rooms",roomId,"messages","old")));
});
