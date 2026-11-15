"use strict";

const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {resolve}=require("node:path");
const {initializeTestEnvironment,assertSucceeds,assertFails}
  = require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,setDoc,updateDoc,deleteDoc}
  = require("firebase/firestore");
let env;
before(async()=>{
  const m=/^(127\.0\.0\.1|localhost):(\d+)$/.exec(process.env.FIRESTORE_EMULATOR_HOST||"");
  assert.ok(m,"Firestore Emulator required");
  env=await initializeTestEnvironment({projectId:"demo-talksullae",
    firestore:{host:m[1],port:Number(m[2]),
      rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")}});
  await env.withSecurityRulesDisabled(async ctx=>{
    await setDoc(doc(ctx.firestore(),"users","profile-a"),
      {nickname:"본인별명",avatar:"고양이"});
  });
});
after(async()=>{if(env)await env.cleanup()});
test("only owner can read their profile; no client writes or listing",async()=>{
  const a=env.authenticatedContext("profile-a").firestore();
  const b=env.authenticatedContext("profile-b").firestore();
  const guest=env.unauthenticatedContext().firestore();
  const own=doc(a,"users","profile-a");
  await assertSucceeds(getDoc(own));
  await assertFails(getDoc(doc(b,"users","profile-a")));
  await assertFails(getDoc(doc(guest,"users","profile-a")));
  await assertFails(getDocs(collection(a,"users")));
  await assertFails(setDoc(doc(a,"users","profile-new"),{nickname:"위조"}));
  await assertFails(updateDoc(own,{nickname:"위조"}));
  await assertFails(deleteDoc(own));
});
