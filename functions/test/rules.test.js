const {before,after,beforeEach,test} = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {resolve} = require("node:path");
const {
  initializeTestEnvironment,assertSucceeds,assertFails
} = require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,setDoc,updateDoc,deleteDoc} = require("firebase/firestore");

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
