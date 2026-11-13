"use strict";
const {before,after,beforeEach,test}=require("node:test");
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {resolve}=require("node:path");
const {initializeTestEnvironment,assertSucceeds,assertFails}=
  require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,query,limit,setDoc,updateDoc,deleteDoc}
 =require("firebase/firestore");

const roomId="12345678901234567890",sessionId="c".repeat(64);
let env;
before(async()=>{
 const h=/^(127\.0\.0\.1|localhost):(\d+)$/.exec(process.env.FIRESTORE_EMULATOR_HOST||"");
 assert.ok(h,"Firestore Emulator required");
 env=await initializeTestEnvironment({projectId:"demo-talksullae",
   firestore:{host:h[1],port:Number(h[2]),
     rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")}
 });
});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
   const db=c.firestore();
   await setDoc(doc(db,"rooms",roomId),{status:"active",ownerId:"owner",activeGameSessionId:sessionId});
   for(const [uid,status] of [
     ["owner","active"],["friend","active"],["watcher","active"],["pending","pending"]
   ])await setDoc(doc(db,"rooms",roomId,"members",uid),{status});
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId),{
     roomId,sessionId,gameId:"forbidden",rulesVersion:1,
     phase:"playing",schemaVersion:1,revision:0,round:1,
     participants:{owner:{ready:true},friend:{ready:true}},
     publicState:{eliminatedUids:[],prompt:"금지어를 피하세요."}
   });
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"publicMessages","first"),{
     senderUid:"owner",text:"아침입니다",triggered:false
   });
   await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"privateAssignments","owner"),{
     data:{forbiddenWord:"커피"}
   });
 });
});
test("game messages are readable only for participants with cap and no direct writes",async()=>{
 for(const uid of ["owner","friend"]){
   const db=env.authenticatedContext(uid).firestore();
   const base=["rooms",roomId,"gameSessions",sessionId,"publicMessages"];
   await assertSucceeds(getDoc(doc(db,...base,"first")));
   await assertSucceeds(getDocs(query(collection(db,...base),limit(30))));
   await assertFails(getDocs(query(collection(db,...base),limit(31))));
   await assertFails(getDocs(collection(db,...base)));
   await assertFails(setDoc(doc(db,...base,"fake"),{
     senderUid:uid,text:"금지어",forbiddenWord:"비밀"
   }));
   await assertFails(updateDoc(doc(db,...base,"first"),{text:"조작"}));
   await assertFails(deleteDoc(doc(db,...base,"first")));
   await assertFails(getDoc(doc(db,"rooms",roomId,
     "gameSessions",sessionId,"privateAssignments","owner")));
 }
 for(const uid of ["watcher","pending","outsider"]){
   const db=env.authenticatedContext(uid).firestore();
   await assertFails(getDoc(doc(db,"rooms",roomId,
     "gameSessions",sessionId,"publicMessages","first")));
 }
});
test("archived room and removed user immediately lose game message access",async()=>{
 const db=env.authenticatedContext("owner").firestore();
 const message=doc(db,"rooms",roomId,"gameSessions",sessionId,"publicMessages","first");
 await assertSucceeds(getDoc(message));
 await env.withSecurityRulesDisabled(async c=>{
   await updateDoc(doc(c.firestore(),"rooms",roomId,"members","owner"),{status:"removed"});
 });
 await assertFails(getDoc(message));
 await env.withSecurityRulesDisabled(async c=>{
   await updateDoc(doc(c.firestore(),"rooms",roomId,"members","owner"),{status:"active"});
   await updateDoc(doc(c.firestore(),"rooms",roomId),{status:"archived"});
 });
 await assertFails(getDoc(message));
});
