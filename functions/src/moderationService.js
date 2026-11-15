"use strict";

const {createHash}=require("node:crypto");
const {HttpsError}=require("firebase-functions/v2/https");
const {FieldValue,FieldPath}=require("firebase-admin/firestore");
const {authUid}=require("./policy");

const REPORT_RE=/^[a-f0-9]{64}$/;
const UUID_RE=/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
const DECISIONS=new Set(["under_review","resolved","dismissed"]);
const MAX_QUEUE=30;

function moderatorUid(request){
  const uid=authUid(request);
  if(request.auth?.token?.talksullaeModerator!==true){
    throw new HttpsError("permission-denied","운영 담당자만 신고를 처리할 수 있습니다.");
  }
  return uid;
}

function parseDecision(data){
  if(!data||typeof data!=="object"||Array.isArray(data)||
     Object.keys(data).some(key=>!["reportId","requestId","status","reason"].includes(key))||
     typeof data.reportId!=="string"||!REPORT_RE.test(data.reportId)||
     typeof data.requestId!=="string"||!UUID_RE.test(data.requestId)||
     !DECISIONS.has(data.status)||typeof data.reason!=="string"){
    throw new HttpsError("invalid-argument","신고 처리 요청 형식이 올바르지 않습니다.");
  }
  const reason=data.reason.trim();
  if(reason.length>500||reason.length<5){
    throw new HttpsError("invalid-argument","운영 조치 근거는 5~500자로 기록해 주세요.");
  }
  return {
    reportId:data.reportId,requestId:data.requestId.toLowerCase(),
    status:data.status,reason
  };
}

async function getModerationInbox(db,request){
  moderatorUid(request);
  const input=request.data??{};
  if(typeof input!=="object"||Array.isArray(input)||
     Object.keys(input).some(key=>!["limit","afterId"].includes(key))||
     (input.limit!==undefined && (!Number.isInteger(input.limit)||
       input.limit<1||input.limit>MAX_QUEUE))||
     (input.afterId!==undefined &&
       (typeof input.afterId!=="string"||!REPORT_RE.test(input.afterId)))){
    throw new HttpsError("invalid-argument","신고 조회 범위가 올바르지 않습니다.");
  }
  const limit=input.limit??MAX_QUEUE;
  let query=db.collection("safetyReports")
    .where("status","in",["received","under_review"])
    .orderBy(FieldPath.documentId()).limit(limit+1);
  if(input.afterId)query=query.startAfter(input.afterId);
  const snapshot=await query.get();
  const page=snapshot.docs.slice(0,limit);
  return {reports:page.map(item=>{
    const data=item.data();
    return {
      id:item.id,
      status:data.status,
      category:data.category||"other",
      createdAtMillis:data.createdAt?.toMillis?.()??null,
      actorUid:data.actorUid||null,
      reportedUid:data.reportedUid||null,
      roomId:data.roomId||null,
      roomReference:data.roomReference||null,
      details:data.details||"",
      evidence:data.evidence||null
    };
  }),limit,hasMore:snapshot.size>limit,
    nextCursor:snapshot.size>limit?page[page.length-1].id:null};
}

async function setModerationStatus(db,request){
  const operator=moderatorUid(request);
  const data=parseDecision(request.data);
  const id=createHash("sha256").update(JSON.stringify([
    data.reportId,data.requestId
  ])).digest("hex");
  const report=db.collection("safetyReports").doc(data.reportId);
  const audit=db.collection("moderationAudit").doc(id);
  return db.runTransaction(async tx=>{
    const [doc,receipt]=await Promise.all([tx.get(report),tx.get(audit)]);
    if(receipt.exists){
      if(receipt.get("operatorUid")!==operator||
         receipt.get("reportId")!==data.reportId||
         receipt.get("status")!==data.status||
         receipt.get("reason")!==data.reason){
        throw new HttpsError("already-exists","처리 요청 ID가 다른 작업에 사용되었습니다.");
      }
      return {reportId:data.reportId,status:data.status,deduplicated:true};
    }
    if(!doc.exists)throw new HttpsError("not-found","신고 번호를 확인할 수 없습니다.");
    const before=doc.get("status");
    if(!["received","under_review"].includes(before)){
      throw new HttpsError("failed-precondition","이미 종료된 신고는 다시 처리할 수 없습니다.");
    }
    if(before==="under_review"&&data.status==="under_review"){
      throw new HttpsError("failed-precondition","이미 검토 중인 신고입니다.");
    }
    tx.update(report,{
      status:data.status,lastReviewedAt:FieldValue.serverTimestamp(),
      lastReviewedBy:operator
    });
    tx.create(audit,{
      reportId:data.reportId,operatorUid:operator,
      from:before,status:data.status,reason:data.reason,
      at:FieldValue.serverTimestamp()
    });
    return {reportId:data.reportId,status:data.status,deduplicated:false};
  });
}

module.exports={
  MAX_QUEUE,moderatorUid,parseDecision,
  getModerationInbox,setModerationStatus
};
