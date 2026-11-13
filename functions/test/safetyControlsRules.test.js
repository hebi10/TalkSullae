"use strict";

const {before,after,beforeEach,test}=require("node:test");
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {resolve}=require("node:path");
const {initializeTestEnvironment,assertSucceeds,assertFails}=
  require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,query,limit,setDoc,deleteDoc}=require("firebase/firestore");

let env;
before(async()=>{
 const host=/^(127\.0\.0\.1|localhost):(\d+)$/.exec(process.env.FIRESTORE_EMULATOR_HOST||"");
 assert.ok(host,"Firestore Emulator required");
 env=await initializeTestEnvironment({
  projectId:"demo-talksullae",
  firestore:{host:host[1],port:Number(host[2]),rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")}
 });
});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();
  await setDoc(doc(db,"userSafety","owner","blocks","target"),{createdAt:new Date()});
  await setDoc(doc(db,"accountDeletionJobs","owner"),{status:"queued"});
 });
});
test("only the account owner can read its own block list, with a bounded query",async()=>{
 const own=env.authenticatedContext("owner").firestore();
 const other=env.authenticatedContext("other").firestore();
 const blocked=doc(own,"userSafety","owner","blocks","target");
 await assertSucceeds(getDoc(blocked));
 await assertSucceeds(getDocs(query(collection(own,"userSafety","owner","blocks"),limit(100))));
 await assertFails(getDocs(query(collection(own,"userSafety","owner","blocks"),limit(101))));
 await assertFails(getDocs(collection(own,"userSafety","owner","blocks")));
 await assertFails(getDoc(doc(other,"userSafety","owner","blocks","target")));
 await assertFails(getDocs(query(collection(other,"userSafety","owner","blocks"),limit(100))));
});
test("no client can forge blocks or read account deletion jobs and safety reports",async()=>{
 for(const uid of ["owner","other"]){
  const db=env.authenticatedContext(uid).firestore();
  await assertFails(setDoc(doc(db,"userSafety","owner","blocks","forged"),{status:true}));
  await assertFails(deleteDoc(doc(db,"userSafety","owner","blocks","target")));
  await assertFails(getDoc(doc(db,"accountDeletionJobs","owner")));
  await assertFails(setDoc(doc(db,"accountDeletionJobs","owner"),{status:"completed"}));
 }
 const anon=env.unauthenticatedContext().firestore();
 await assertFails(getDoc(doc(anon,"userSafety","owner","blocks","target")));
});
