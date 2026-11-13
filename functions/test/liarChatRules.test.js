"use strict";

const {before,after,beforeEach,test}=require("node:test");
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {resolve}=require("node:path");
const {initializeTestEnvironment,assertSucceeds,assertFails}=
  require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,query,orderBy,limit,
  setDoc,updateDoc,deleteDoc}=require("firebase/firestore");

const roomId="12345678901234567890";
const sessionId="b".repeat(64);
let env;
before(async()=>{
 const match=/^(127\.0\.0\.1|localhost):(\d+)$/
   .exec(process.env.FIRESTORE_EMULATOR_HOST||"");
 assert.ok(match,"Firestore emulator required");
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
   for(const [uid,status] of [
     ["owner","active"],["friend","active"],["watcher","active"],["waiting","pending"]
   ])await setDoc(doc(db,"rooms",roomId,"members",uid),{status});
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId),{
     roomId,sessionId,gameId:"liar",rulesVersion:1,schemaVersion:1,
     phase:"playing",revision:1,round:1,
     participants:{owner:{ready:true},friend:{ready:true}},
     publicState:{prompt:"힌트를 주고받아 주세요."}
   });
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"publicMessages","one"),{
     senderUid:"owner",text:"첫 힌트",createdAt:new Date("2026-11-12T00:00:00.000Z")
   });
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"privateAssignments","owner"),{
     data:{role:"citizen",topic:"커피"}
   });
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"privateSubmissions","vote"),{
     data:{targetUid:"friend"}
   });
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"gameChatRateLimits","owner"),{
     lastSentAt:new Date("2026-11-12T00:00:00.000Z")
   });
 });
});

test("only active game participants read capped public liar messages",async()=>{
 for(const uid of ["owner","friend"]){
   const db=env.authenticatedContext(uid).firestore();
   const path=["rooms",roomId,"gameSessions",sessionId,"publicMessages"];
   await assertSucceeds(getDoc(doc(db,...path,"one")));
   await assertSucceeds(getDocs(query(collection(db,...path),
     orderBy("createdAt","desc"),limit(30))));
   await assertFails(getDocs(query(collection(db,...path),limit(31))));
   await assertFails(getDocs(collection(db,...path)));
   await assertFails(setDoc(doc(db,...path,"forged"),{
     senderUid:"owner",text:"위조",role:"liar",topic:"커피"
   }));
   await assertFails(updateDoc(doc(db,...path,"one"),{text:"변조"}));
   await assertFails(deleteDoc(doc(db,...path,"one")));
 }
 for(const uid of ["watcher","waiting","outsider"]){
   const db=env.authenticatedContext(uid).firestore();
   const path=["rooms",roomId,"gameSessions",sessionId,"publicMessages"];
   await assertFails(getDoc(doc(db,...path,"one")));
   await assertFails(getDocs(query(collection(db,...path),limit(30))));
 }
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),
   "rooms",roomId,"gameSessions",sessionId,"publicMessages","one")));
});

test("even the room owner cannot read roles, ballots or rate-limit documents",async()=>{
 const client=env.authenticatedContext("owner").firestore();
 for(const [kind,id] of [
   ["privateAssignments","owner"],["privateSubmissions","vote"],
   ["gameChatRateLimits","owner"]
 ]){
   const path=["rooms",roomId,"gameSessions",sessionId,kind];
   await assertFails(getDoc(doc(client,...path,id)));
   await assertFails(getDocs(query(collection(client,...path),limit(10))));
   await assertFails(setDoc(doc(client,...path,"tamper"),{data:{role:"liar"}}));
   await assertFails(updateDoc(doc(client,...path,id),{data:{role:"liar"}}));
 }
});

test("membership removal and archived rooms revoke previously granted chat reads",async()=>{
 const client=env.authenticatedContext("owner").firestore();
 const ref=doc(client,"rooms",roomId,"gameSessions",sessionId,"publicMessages","one");
 await assertSucceeds(getDoc(ref));
 await env.withSecurityRulesDisabled(async c=>{
   await updateDoc(doc(c.firestore(),"rooms",roomId,"members","owner"),{status:"removed"});
 });
 await assertFails(getDoc(ref));
 await env.withSecurityRulesDisabled(async c=>{
   await updateDoc(doc(c.firestore(),"rooms",roomId,"members","owner"),{status:"active"});
   await updateDoc(doc(c.firestore(),"rooms",roomId),{status:"archived"});
 });
 await assertFails(getDoc(ref));
});
