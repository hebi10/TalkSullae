"use strict";

const {before,after,beforeEach,test}=require("node:test");
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {resolve}=require("node:path");
const {initializeTestEnvironment,assertSucceeds,assertFails}
  =require("@firebase/rules-unit-testing");
const {doc,collection,getDoc,getDocs,query,orderBy,limit,
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
    firestore:{
      host:match[1],port:Number(match[2]),
      rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")
    }
  });
});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context=>{
    const db=context.firestore();
    await setDoc(doc(db,"rooms",roomId),{status:"active",ownerId:"owner",activeGameSessionId:sessionId});
    for(const [uid,status] of [["owner","active"],["friend","active"],["watcher","active"],["pending","pending"]]){
      await setDoc(doc(db,"rooms",roomId,"members",uid),{status});
    }
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId),{
      roomId,sessionId,gameId:"identity",rulesVersion:1,schemaVersion:1,
      phase:"playing",participants:{owner:{ready:true},friend:{ready:true}},
      publicState:{prompt:"누가 제일 잘 버틸까요?",options:["고양이","토끼"]},
      revision:1,round:1
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"publicMessages","message1"),{
      alias:"고양이",text:"익명 인사",createdAt:new Date("2026-11-12T00:00:00.000Z")
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"privateAssignments","owner"),{
      data:{alias:"고양이",actualUid:"owner"}
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"privateSubmissions","private"),{
      data:{matches:{"고양이":"friend"}}
    });
    await setDoc(doc(db,"rooms",roomId,"gameSessions",sessionId,"gameChatRateLimits","owner"),{
      lastSentAt:new Date("2026-11-12T00:00:00.000Z")
    });
  });
});

test("only the exact active session participants can get capped anonymous messages",async()=>{
  for(const uid of ["owner","friend"]){
    const db=env.authenticatedContext(uid).firestore();
    const base=["rooms",roomId,"gameSessions",sessionId,"publicMessages"];
    await assertSucceeds(getDoc(doc(db,...base,"message1")));
    await assertSucceeds(getDocs(query(collection(db,...base),
      orderBy("createdAt","desc"),limit(30))));
    await assertFails(getDocs(query(collection(db,...base),limit(31))));
    await assertFails(getDocs(collection(db,...base)));
    await assertFails(setDoc(doc(db,...base,"forged"),{
      alias:"고양이",text:"위조",senderUid:uid
    }));
    await assertFails(updateDoc(doc(db,...base,"message1"),{text:"변경"}));
    await assertFails(deleteDoc(doc(db,...base,"message1")));
  }
  for(const uid of ["watcher","pending","stranger"]){
    const db=env.authenticatedContext(uid).firestore();
    const base=["rooms",roomId,"gameSessions",sessionId,"publicMessages"];
    await assertFails(getDoc(doc(db,...base,"message1")));
    await assertFails(getDocs(query(collection(db,...base),limit(30))));
  }
  const guest=env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(guest,"rooms",roomId,
    "gameSessions",sessionId,"publicMessages","message1")));
});

test("owner cannot inspect secret alias mappings, votes, or chat rate receipts",async()=>{
  const db=env.authenticatedContext("owner").firestore();
  for(const [segment,id] of [
    ["privateAssignments","owner"],["privateSubmissions","private"],
    ["gameChatRateLimits","owner"]
  ]){
    const path=["rooms",roomId,"gameSessions",sessionId,segment];
    await assertFails(getDoc(doc(db,...path,id)));
    await assertFails(getDocs(query(collection(db,...path),limit(10))));
    await assertFails(setDoc(doc(db,...path,"tampered"),{data:"forged"}));
  }
});

test("archived rooms and removed membership revoke message reads",async()=>{
  const client=env.authenticatedContext("owner").firestore();
  const msg=doc(client,"rooms",roomId,"gameSessions",sessionId,"publicMessages","message1");
  await assertSucceeds(getDoc(msg));
  await env.withSecurityRulesDisabled(async context=>{
    await updateDoc(doc(context.firestore(),"rooms",roomId,"members","owner"),{status:"removed"});
  });
  await assertFails(getDoc(msg));
  await env.withSecurityRulesDisabled(async context=>{
    const db=context.firestore();
    await updateDoc(doc(db,"rooms",roomId,"members","owner"),{status:"active"});
    await updateDoc(doc(db,"rooms",roomId),{status:"archived"});
  });
  await assertFails(getDoc(msg));
});
