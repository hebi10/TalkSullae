"use strict";
const {before,after,test}=require("node:test");
const assert=require("node:assert/strict");
const {initializeApp,deleteApp}=require("firebase-admin/app");
const {getFirestore}=require("firebase-admin/firestore");
const {
  getModerationInbox,setModerationStatus
}=require("../src/moderationService");
const {inspectSafetyBacklog}=require("../src/safetyMonitoring");

let app,db;
const id="f".repeat(64);
const data={
  reportId:id,requestId:"123e4567-e89b-42d3-a456-426614174000",
  status:"under_review",reason:"보호 정책 관련 최초 검토 중"
};
const mod={auth:{uid:"trusted-operator",token:{talksullaeModerator:true}}};
const guest={auth:{uid:"ordinary-user",token:{name:"일반 이용자"}}};
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST,"Firestore Emulator required");
  app=initializeApp({projectId:"demo-talksullae"},"live06-moderation");
  db=getFirestore(app);
});
after(async()=>{if(app)await deleteApp(app)});

test("only moderator sees report content and each decision has an immutable audit receipt",async()=>{
  await db.collection("safetyReports").doc(id).set({
    status:"received",actorUid:"user-a",reportedUid:"user-b",
    category:"abuse",details:"신고된 실제 메시지 검토 필요",
    roomReference:"서버 신고 기준 방 정보",
    createdAt:new Date("2026-11-10T00:00:00.000Z")
  });
  await assert.rejects(getModerationInbox(db,{...guest,data:{}}),
    e=>e.code==="permission-denied");
  await assert.rejects(setModerationStatus(db,{...guest,data}),
    e=>e.code==="permission-denied");
  let afterId;
  let found;
  for(let pageNumber=0;pageNumber<12&&!found;pageNumber++){
    const list=await getModerationInbox(db,{...mod,data:{
      limit:30,...(afterId?{afterId}:{})
    }});
    found=list.reports.find(row=>row.id===id);
    afterId=list.nextCursor;
    if(!list.hasMore)break;
  }
  assert.ok(found);
  assert.equal(found.roomReference,"서버 신고 기준 방 정보");
  const updated=await setModerationStatus(db,{...mod,data});
  assert.equal(updated.status,"under_review");
  assert.equal(updated.deduplicated,false);
  assert.equal((await db.collection("safetyReports").doc(id).get()).get("status"),"under_review");
  const repeated=await setModerationStatus(db,{...mod,data});
  assert.equal(repeated.deduplicated,true);
  await assert.rejects(setModerationStatus(db,{...mod,data:{...data,status:"dismissed"}}),
    e=>e.code==="already-exists");
  const done=await setModerationStatus(db,{...mod,data:{
    ...data,requestId:"123e4567-e89b-42d3-a456-426614174001",
    status:"resolved",reason:"신고 건을 기록하고 수동 보호 조치 확인"
  }});
  assert.equal(done.status,"resolved");
  assert.equal((await db.collection("moderationAudit").get()).size>=2,true);
  await assert.rejects(setModerationStatus(db,{...mod,data:{
    ...data,requestId:"123e4567-e89b-42d3-a456-426614174002"
  }}),e=>e.code==="failed-precondition");
});

test("monitoring emits only limited counts without sensitive report bodies",async()=>{
  const counts=await inspectSafetyBacklog(db,()=>Date.parse("2026-11-15T12:00:00Z"));
  assert.equal(typeof counts.reportsOver24Hours,"number");
  assert.equal(JSON.stringify(counts).includes("신고된 실제 메시지"),false);
});


test("moderator can page through more than 30 open cases without losing investigations",async()=>{
  const batch=db.batch();
  for(let i=0;i<35;i++){
    batch.set(db.collection("safetyReports").doc((i+1).toString(16).padStart(64,"0")),{
      status:i%3===0?"under_review":"received",
      category:"other",details:"확인해야 할 신고 건",
      createdAt:new Date("2026-11-08T00:00:00Z")
    });
  }
  await batch.commit();
  const one=await getModerationInbox(db,{...mod,data:{limit:20}});
  const two=await getModerationInbox(db,{...mod,data:{
    limit:20,afterId:one.nextCursor
  }});
  assert.equal(one.reports.length,20);
  assert.equal(one.hasMore,true);
  assert.ok(two.reports.length>=15);
  const ids=[...one.reports,...two.reports].map(row=>row.id);
  assert.equal(new Set(ids).size,ids.length);
  assert.ok(two.reports.every(row=>row.id.length===64));
  const state=await inspectSafetyBacklog(db,()=>Date.parse("2026-11-15T12:00:00Z"));
  assert.ok(state.reportsOver24Hours>0);
});
