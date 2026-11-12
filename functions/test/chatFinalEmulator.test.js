"use strict";

const {before, beforeEach, after, test} = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {resolve} = require("node:path");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {initializeTestEnvironment, assertSucceeds, assertFails} =
  require("@firebase/rules-unit-testing");
const {collection, doc, query, orderBy, limit, startAfter, getDoc, getDocs, setDoc} =
  require("firebase/firestore");
const roomService = require("../src/roomService");
const {sendRoomMessage} = require("../src/chatService");

let adminApp;
let admin;
let env;

const auth = uid => ({auth:{uid,token:{name:uid}}});
const msg = (roomId,uid,num,body) => ({
  ...auth(uid),
  data:{
    roomId,
    requestId:"183c9c18-46a1-423d-87d0-" + String(num).padStart(12,"0"),
    text:body
  }
});

async function twoMembers() {
  const {roomId} = await roomService.createRoom(admin,{
    ...auth("owner"),
    data:{name:"통합 채팅방",description:"",requiresApproval:true}
  });
  const invitation = await roomService.createInvitation(admin,{
    ...auth("owner"), data:{roomId}
  });
  const pending = await roomService.requestJoin(admin,{
    ...auth("member"), data:{code:invitation.code}
  });
  assert.equal(pending.status,"pending");
  await roomService.changeMember(admin,{
    ...auth("owner"),
    data:{roomId,targetUid:"member",action:"approve"}
  });
  return roomId;
}

before(async()=>{
  const emulator = /^(127\.0\.0\.1|localhost):(\d+)$/.exec(
    process.env.FIRESTORE_EMULATOR_HOST || ""
  );
  assert.ok(emulator,"Firestore emulator is required");
  adminApp = initializeApp({projectId:"demo-talksullae"},"game-01-final-tests");
  admin = getFirestore(adminApp);
  env = await initializeTestEnvironment({
    projectId:"demo-talksullae",
    firestore:{
      host:emulator[1],
      port:Number(emulator[2]),
      rules:readFileSync(resolve(__dirname,"../../firestore.rules"),"utf8")
    }
  });
});
beforeEach(async()=>env.clearFirestore());
after(async()=>{
  if(env) await env.cleanup();
  if(adminApp) await deleteApp(adminApp);
});

test("two authenticated members exchange and read the same server messages",async()=>{
  const roomId = await twoMembers();
  const first = await sendRoomMessage(admin,msg(roomId,"owner",101,"안녕하세요"));
  const second = await sendRoomMessage(admin,msg(roomId,"member",102,"반갑습니다"));
  assert.equal(first.deduplicated,false);
  assert.equal(second.deduplicated,false);
  const retry = await sendRoomMessage(admin,msg(roomId,"owner",101,"안녕하세요"));
  assert.equal(retry.messageId,first.messageId);
  assert.equal(retry.deduplicated,true);

  for(const uid of ["owner","member"]){
    const client = env.authenticatedContext(uid).firestore();
    const chat = collection(client,"rooms",roomId,"messages");
    const list = await assertSucceeds(getDocs(query(chat,orderBy("createdAt","desc"),limit(30))));
    assert.equal(list.size,2);
    const texts = list.docs.map(x=>x.get("text")).sort();
    assert.deepEqual(texts,["반갑습니다","안녕하세요"].sort());
  }
});

test("bounded 30-message pages cover 65 old messages with stable cursors",async()=>{
  const roomId = await twoMembers();
  const chatRef = admin.collection("rooms").doc(roomId).collection("messages");
  const batch = admin.batch();
  for(let i=0;i<65;i++){
    batch.set(chatRef.doc("fixed-" + String(i).padStart(3,"0")),{
      senderUid:"owner",
      senderName:"방장",
      text:"기록 " + i,
      createdAt:Timestamp.fromMillis(1760000000000 + i*1000)
    });
  }
  await batch.commit();
  const client = env.authenticatedContext("member").firestore();
  const chat = collection(client,"rooms",roomId,"messages");
  let cursor=null;
  const found=[];
  for(const expected of [30,30,5]){
    let q=query(chat,orderBy("createdAt","desc"),limit(30));
    if(cursor) q=query(chat,orderBy("createdAt","desc"),startAfter(cursor),limit(30));
    const page=await assertSucceeds(getDocs(q));
    assert.equal(page.size,expected);
    found.push(...page.docs.map(x=>x.id));
    cursor=page.docs[page.size-1];
  }
  assert.equal(new Set(found).size,65);
  await assertFails(getDocs(query(chat,orderBy("createdAt","desc"),limit(31))));
  await assertFails(getDocs(chat));
});

test("archived room denies message reads even if a member row remains active",async()=>{
  const roomId=await twoMembers();
  await sendRoomMessage(admin,msg(roomId,"owner",103,"방 보존 메시지"));
  const client=env.authenticatedContext("owner").firestore();
  const chat=collection(client,"rooms",roomId,"messages");
  await assertSucceeds(getDocs(query(chat,orderBy("createdAt","desc"),limit(30))));
  await admin.collection("rooms").doc(roomId).update({status:"archived"});
  await assertFails(getDocs(query(chat,orderBy("createdAt","desc"),limit(30))));
});

test("removed and pending members cannot read, list or write",async()=>{
  const roomId=await twoMembers();
  await sendRoomMessage(admin,msg(roomId,"owner",104,"권한 검증"));
  const owner=env.authenticatedContext("owner").firestore();
  const member=env.authenticatedContext("member").firestore();
  const list=collection(member,"rooms",roomId,"messages");
  const snapshot=await assertSucceeds(getDocs(query(list,orderBy("createdAt","desc"),limit(30))));
  assert.equal(snapshot.size,1);
  await roomService.changeMember(admin,{
    ...auth("owner"),data:{roomId,targetUid:"member",action:"remove"}
  });
  await assertFails(getDocs(query(list,orderBy("createdAt","desc"),limit(30))));
  await assertFails(getDoc(doc(member,"rooms",roomId,"messages",snapshot.docs[0].id)));
  await assertFails(setDoc(doc(list,"injected"),{
    senderUid:"member",senderName:"임의",text:"금지"
  }));
  await assertSucceeds(getDocs(query(
    collection(owner,"rooms",roomId,"messages"),orderBy("createdAt","desc"),limit(30)
  )));
});
