"use strict";

const {before,after,beforeEach,test}=require("node:test");
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {resolve}=require("node:path");
const {initializeTestEnvironment,assertSucceeds,assertFails}=
  require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,query,limit,setDoc,updateDoc,deleteDoc}
  =require("firebase/firestore");

const roomId="12345678901234567890",sessionId="a".repeat(64);
const seasonId="2026-11";
let env;
before(async()=>{
 const host=/^(127\.0\.0\.1|localhost):(\d+)$/
   .exec(process.env.FIRESTORE_EMULATOR_HOST||"");
 assert.ok(host,"Firestore Emulator required");
 env=await initializeTestEnvironment({
   projectId:"demo-talksullae",
   firestore:{host:host[1],port:Number(host[2]),
     rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")}
 });
});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async context=>{
   const db=context.firestore();
   await setDoc(doc(db,"rooms",roomId),{status:"active",ownerId:"owner"});
   await setDoc(doc(db,"rooms",roomId,"members","owner"),{status:"active"});
   await setDoc(doc(db,"rooms",roomId,"members","friend"),{status:"active"});
   await setDoc(doc(db,"rooms",roomId,"members","pending"),{status:"pending"});
   await setDoc(doc(db,"rooms",roomId,"playerStats","owner"),{
     totalGames:5,achievements:{first_game:true}
   });
   await setDoc(doc(db,"rooms",roomId,"playerStats","friend"),{totalGames:3});
   await setDoc(doc(db,"rooms",roomId,"playerStats","owner","games",sessionId),{
     sessionId,gameId:"identity",seasonPoints:100
   });
   await setDoc(doc(db,"rooms",roomId,"seasons",seasonId),{
     seasonId,gameCount:1
   });
   await setDoc(doc(db,"rooms",roomId,"seasons",seasonId,"scores","owner"),{
     uid:"owner",seasonPoints:100
   });
   await setDoc(doc(db,"rooms",roomId,"seasons",seasonId,"scores","friend"),{
     uid:"friend",seasonPoints:60
   });
   await setDoc(doc(db,"rooms",roomId,"recordEvents",sessionId),{
     gameId:"identity",seasonId
   });
   await setDoc(doc(db,"rooms",roomId,"recordSettlements",sessionId),{
     settlementId:"x"
   });
 });
});
test("members see public monthly rankings, own stats and own recent results only",async()=>{
 const own=env.authenticatedContext("owner").firestore();
 const friend=env.authenticatedContext("friend").firestore();
 await assertSucceeds(getDoc(doc(own,"rooms",roomId,"playerStats","owner")));
 await assertFails(getDoc(doc(own,"rooms",roomId,"playerStats","friend")));
 await assertFails(getDoc(doc(friend,"rooms",roomId,"playerStats","owner")));
 await assertSucceeds(getDoc(doc(own,"rooms",roomId,"seasons",seasonId)));
 await assertSucceeds(getDoc(doc(own,"rooms",roomId,"seasons",seasonId,"scores","friend")));
 const list=collection(own,"rooms",roomId,"seasons",seasonId,"scores");
 await assertSucceeds(getDocs(query(list,limit(20))));
 await assertFails(getDocs(query(list,limit(21))));
 await assertFails(getDocs(list));
 await assertSucceeds(getDocs(query(collection(own,"rooms",roomId,
   "playerStats","owner","games"),limit(30))));
 await assertFails(getDocs(query(collection(own,"rooms",roomId,
   "playerStats","owner","games"),limit(31))));
 await assertFails(getDocs(query(collection(own,"rooms",roomId,
   "playerStats","friend","games"),limit(30))));
 await assertSucceeds(getDocs(query(collection(own,"rooms",roomId,"recordEvents"),limit(20))));
 await assertFails(getDocs(query(collection(own,"rooms",roomId,"recordEvents"),limit(21))));
});
test("server-only scores, ledger, achievements and result history cannot be forged",async()=>{
 const user=env.authenticatedContext("owner").firestore();
 const score=doc(user,"rooms",roomId,"seasons",seasonId,"scores","owner");
 await assertFails(updateDoc(score,{seasonPoints:99999}));
 await assertFails(deleteDoc(score));
 await assertFails(setDoc(doc(user,"rooms",roomId,"playerStats","owner"),{totalWins:999}));
 await assertFails(setDoc(doc(user,"rooms",roomId,"playerStats","owner","games","fake"),{
   seasonPoints:10000
 }));
 await assertFails(getDoc(doc(user,"rooms",roomId,"recordSettlements",sessionId)));
 await assertFails(setDoc(doc(user,"rooms",roomId,"recordSettlements","fake"),{}));
});
test("pending, outsider, unauthenticated and archived room users cannot see rankings",async()=>{
 for(const uid of ["pending","outsider"]){
   const db=env.authenticatedContext(uid).firestore();
   await assertFails(getDoc(doc(db,"rooms",roomId,"seasons",seasonId)));
   await assertFails(getDocs(query(collection(db,"rooms",roomId,
     "seasons",seasonId,"scores"),limit(20))));
 }
 const unauth=env.unauthenticatedContext().firestore();
 await assertFails(getDoc(doc(unauth,"rooms",roomId,"seasons",seasonId)));
 await env.withSecurityRulesDisabled(async c=>{
   await updateDoc(doc(c.firestore(),"rooms",roomId),{status:"archived"});
 });
 const member=env.authenticatedContext("owner").firestore();
 await assertFails(getDoc(doc(member,"rooms",roomId,"playerStats","owner")));
 await assertFails(getDocs(query(collection(member,"rooms",roomId,
   "seasons",seasonId,"scores"),limit(20))));
});
