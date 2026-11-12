"use strict";

const {createHash}=require("node:crypto");
const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid,roomIdValue}=require("../policy");
const {productionRules}=require("./engine");
const SESSION_ID=/^[a-f0-9]{64}$/;
const KEY=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_LENGTH=500;
const MIN_INTERVAL=750;
const sha=value=>createHash("sha256").update(JSON.stringify(value)).digest("hex");

function parseIdentityChat(input){
  if(!input || typeof input!=="object" || Array.isArray(input) ||
     Object.keys(input).some(key=>!["roomId","sessionId","requestId","text"].includes(key))){
    throw new HttpsError("invalid-argument","익명 채팅 요청이 올바르지 않습니다.");
  }
  const roomId=roomIdValue(input.roomId);
  if(typeof input.sessionId!=="string"||!SESSION_ID.test(input.sessionId)||
     typeof input.requestId!=="string"||!KEY.test(input.requestId)||
     typeof input.text!=="string"){
    throw new HttpsError("invalid-argument","세션 또는 메시지 요청 ID가 잘못되었습니다.");
  }
  const text=input.text.replace(/\r\n?/g,"\n").trim();
  if(text.length===0 || text.length>MAX_LENGTH ||
     /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(text)){
    throw new HttpsError("invalid-argument","익명 채팅은 1~500자 이내의 글만 사용할 수 있습니다.");
  }
  return {roomId,sessionId:input.sessionId,requestId:input.requestId.toLowerCase(),text};
}

async function sendIdentityMessage(db,request,now=Date.now){
  const uid=authUid(request);
  const input=parseIdentityChat(request.data);
  const roomRef=db.collection("rooms").doc(input.roomId);
  const memberRef=roomRef.collection("members").doc(uid);
  const sessionRef=roomRef.collection("gameSessions").doc(input.sessionId);
  const privateRef=sessionRef.collection("privateAssignments").doc(uid);
  const messageId=sha([input.roomId,input.sessionId,uid,input.requestId]);
  const messageRef=sessionRef.collection("publicMessages").doc(messageId);
  const rateRef=sessionRef.collection("gameChatRateLimits").doc(uid);
  return db.runTransaction(async tx=>{
    const [room,member,session,personal,existing,rate]=await Promise.all([
      tx.get(roomRef),tx.get(memberRef),tx.get(sessionRef),
      tx.get(privateRef),tx.get(messageRef),tx.get(rateRef)
    ]);
    if(!room.exists||room.get("status")!=="active"||
       !member.exists||member.get("status")!=="active"||
       !session.exists||room.get("activeGameSessionId")!==input.sessionId ||
       session.get("gameId")!=="identity" || session.get("rulesVersion")!==1||
       !session.get("participants")?.[uid] || !personal.exists){
      throw new HttpsError("permission-denied","이 게임에 익명 채팅 권한이 없습니다.");
    }
    const privateRole=personal.get("data")?.alias;
    if(typeof privateRole!=="string"||privateRole.length>20 ||
       !session.get("publicState")?.options?.includes(privateRole)){
      throw new HttpsError("failed-precondition","익명 별칭 정보가 잘못되었습니다.");
    }
    if(existing.exists){
      if(existing.get("alias")!==privateRole||existing.get("text")!==input.text){
        throw new HttpsError("already-exists","다른 본문에 같은 메시지 ID를 사용할 수 없습니다.");
      }
      return {messageId,deduplicated:true};
    }
    if(session.get("phase")!=="playing" ||
       !session.get("phaseEndsAt") ||
       session.get("phaseEndsAt").toMillis()<=now()){
      throw new HttpsError("failed-precondition","익명 채팅 시간이 종료됐습니다.");
    }
    const last=rate.exists?rate.get("lastSentAt"):null;
    if(rate.exists && !(last instanceof Timestamp)){
      throw new HttpsError("failed-precondition","채팅 제한 정보를 확인할 수 없습니다.");
    }
    if(last && now()<last.toMillis()+MIN_INTERVAL){
      throw new HttpsError("resource-exhausted","메시지를 너무 빠르게 보내고 있습니다.");
    }
    tx.create(messageRef,{
      alias:privateRole,
      text:input.text,
      createdAt:FieldValue.serverTimestamp()
    });
    tx.set(rateRef,{lastSentAt:Timestamp.fromMillis(now())});
    return {messageId,deduplicated:false};
  });
}

module.exports={parseIdentityChat,sendIdentityMessage,MAX_LENGTH,MIN_INTERVAL};
