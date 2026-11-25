"use strict";

const {FieldValue}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid}=require("./policy");
const catalog=Object.freeze(require("./serviceNoticeCatalog.json"));
const isEnabled=()=>process.env.TALKSULLAE_SERVICE_NOTICES_ENABLED==="true";

function receiptRef(db,uid){
  return db.collection("users").doc(uid).collection("policyReceipts").doc(catalog.id);
}
function validReceipt(doc){
  return doc.exists && doc.get("termsVersion")===catalog.termsVersion &&
    doc.get("privacyVersion")===catalog.privacyVersion &&
    doc.get("termsAccepted")===true && doc.get("privacyNoticeRead")===true;
}
function object(input){
  return input&&typeof input==="object"&&!Array.isArray(input);
}

async function getServiceNoticeStatus(db,request,enabled=isEnabled()){
  const uid=authUid(request),data=request.data??{};
  if(!object(data)||Object.keys(data).length!==0)throw new HttpsError("invalid-argument","정책 조회 요청이 올바르지 않습니다.");
  return db.runTransaction(async tx=>{
    const deletion=await tx.get(db.collection("accountDeletionJobs").doc(uid));
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 서비스에 접근할 수 없습니다.");
    if(!enabled)return {enabled:false,required:false};
    const receipt=await tx.get(receiptRef(db,uid));
    const required=!validReceipt(receipt);
    return required?{enabled:true,required:true,...catalog}:{enabled:true,required:false};
  },{readOnly:true});
}

async function acceptServiceNotices(db,request,enabled=isEnabled()){
  const uid=authUid(request),data=request.data;
  const fields=["expectedUid","termsVersion","privacyVersion","termsAccepted","privacyNoticeRead"];
  if(!object(data)||Object.keys(data).length!==fields.length||
     Object.keys(data).some(key=>!fields.includes(key))||data.expectedUid!==uid||
     data.termsAccepted!==true||data.privacyNoticeRead!==true){
    throw new HttpsError("invalid-argument","이용약관 동의와 개인정보 안내 확인을 다시 확인해 주세요.");
  }
  if(!enabled||data.termsVersion!==catalog.termsVersion||data.privacyVersion!==catalog.privacyVersion){
    throw new HttpsError("failed-precondition","정책이 변경됐습니다. 다시 확인해 주세요.");
  }
  const ref=receiptRef(db,uid);
  return db.runTransaction(async tx=>{
    const [deletion,previous]=await Promise.all([tx.get(db.collection("accountDeletionJobs").doc(uid)),tx.get(ref)]);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 정책 동의를 변경할 수 없습니다.");
    if(previous.exists){
      if(!validReceipt(previous))throw new HttpsError("failed-precondition","기존 정책 기록을 확인할 수 없습니다.");
      return {accepted:true,deduplicated:true};
    }
    tx.create(ref,{termsVersion:catalog.termsVersion,privacyVersion:catalog.privacyVersion,
      termsAccepted:true,privacyNoticeRead:true,acceptedAt:FieldValue.serverTimestamp()});
    return {accepted:true,deduplicated:false};
  });
}

module.exports={catalog,getServiceNoticeStatus,acceptServiceNotices};
