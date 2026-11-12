"use strict";

const {before,after,beforeEach,test}=require("node:test");
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {resolve}=require("node:path");
const {initializeTestEnvironment,assertSucceeds,assertFails}
  =require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,query,limit,
  setDoc,updateDoc,deleteDoc}=require("firebase/firestore");

const roomId="12345678901234567890";
const sessionId="a".repeat(64);
let env;

before(async()=>{
  const match=/^(127\.0\.0\.1|localhost):(\d+)$/
    .exec(process.env.FIRESTORE_EMULATOR_HOST||"");
  assert.ok(match,"Firestore emulator is required");
  env=await initializeTestEnvironment({
    projectId:"demo-talksullae",
    firestore:{host:match[1],port:Number(match[2]),
      rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")}
  });
});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context=>{
    const db=context.firestore();
    await setDoc(doc(db,"rooms",roomId),{ownerId:"owner",status:"active",activeGameSessionId:sessionId});
    await setDoc(doc(db,"rooms",roomId,"members","owner"),{status:"active"});
    await setDoc(doc(db,"rooms",roomId,"members","friend"),{status:"active"});
    await setDoc(doc(db,"rooms",roomId,"members","waiting"),{status:"pending"});
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId),{
      roomId,sessionId,gameId:"identity",rulesVersion:1,schemaVersion:1,
      mode:"realtime",phase:"waiting",revision:0,round:1,
      participants:{owner:{ready:false}},publicState:{},phaseEndsAt:null
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"privateAssignments","owner"),{
      data:{secretRole:"spy",realUid:"owner"}
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"privateSubmissions","sample"),{
      actorUid:"owner",data:{choice:"A"}
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"processedActions","sample"),{
      actorUid:"owner",fingerprint:"private"
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"results","final"),{
      scoresByUid:{owner:100}
    });
  });
});

test("only active members of an active room can read public session metadata",async()=>{
  for(const uid of ["owner","friend"]){
    const db=env.authenticatedContext(uid).firestore();
    const ref=doc(db,"rooms",roomId,"gameSessions",sessionId);
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(getDocs(query(
      collection(db,"rooms",roomId,"gameSessions"),limit(10))));
    await assertFails(getDocs(query(collection(db,"rooms",roomId,"gameSessions"),limit(11))));
    await assertFails(getDocs(collection(db,"rooms",roomId,"gameSessions")));
    await assertFails(setDoc(doc(db,"rooms",roomId,"gameSessions","b".repeat(64)),
      {role:"forged"}));
    await assertFails(updateDoc(ref,{phase:"finished"}));
    await assertFails(deleteDoc(ref));
  }
  for(const uid of ["waiting","outsider"]){
    const db=env.authenticatedContext(uid).firestore();
    await assertFails(getDoc(doc(db,"rooms",roomId,"gameSessions",sessionId)));
    await assertFails(getDocs(query(collection(db,"rooms",roomId,"gameSessions"),limit(10))));
  }
  const anonymous=env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anonymous,"rooms",roomId,"gameSessions",sessionId)));
});

test("real aliases, assigned roles, unsubmitted guesses and request receipts stay server-only",async()=>{
  const paths=["privateAssignments/owner","privateSubmissions/sample","processedActions/sample"];
  for(const uid of ["owner","friend","waiting","outsider"]){
    const db=env.authenticatedContext(uid).firestore();
    for(const suffix of paths){
      const [kind,key]=suffix.split("/");
      const ref=doc(db,"rooms",roomId,"gameSessions",sessionId,kind,key);
      await assertFails(getDoc(ref));
      await assertFails(getDocs(collection(db,"rooms",roomId,"gameSessions",sessionId,kind)));
      await assertFails(setDoc(ref,{data:{secret:"forged"}}));
      await assertFails(updateDoc(ref,{data:{secret:"changed"}}));
      await assertFails(deleteDoc(ref));
    }
  }
});

test("results remain private until finished; result direct mutations and list always denied",async()=>{
  const db=env.authenticatedContext("owner").firestore();
  const path=["rooms",roomId,"gameSessions",sessionId,"results"];
  const result=doc(db,...path,"final");
  await assertFails(getDoc(result));
  await assertFails(getDocs(query(collection(db,...path),limit(10))));
  await env.withSecurityRulesDisabled(async context=>{
    await updateDoc(doc(context.firestore(),"rooms",roomId,"gameSessions",sessionId),
      {phase:"finished"});
  });
  await assertSucceeds(getDoc(result));
  await assertFails(getDocs(query(collection(db,...path),limit(10))));
  await assertFails(updateDoc(result,{"scoresByUid.owner":999}));
  await assertFails(deleteDoc(result));
});

test("archived rooms and revoked members cannot read sessions or final results",async()=>{
  const db=env.authenticatedContext("owner").firestore();
  const session=doc(db,"rooms",roomId,"gameSessions",sessionId);
  await assertSucceeds(getDoc(session));
  await env.withSecurityRulesDisabled(async context=>{
    await updateDoc(doc(context.firestore(),"rooms",roomId),{status:"archived"});
  });
  await assertFails(getDoc(session));
  await assertFails(getDocs(query(collection(db,"rooms",roomId,"gameSessions"),limit(10))));
});
