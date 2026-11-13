"use strict";

const {createHash}=require("node:crypto");
const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid}=require("../policy");
const {parseIdentityChat,MIN_INTERVAL}=require("./identityChatService");
const {DAY_MS,GAME_CHAT_DAYS}=require("../retention");

const sha=value=>createHash("sha256").update(JSON.stringify(value)).digest("hex");

async function sendLiarMessage(db,request,now=Date.now){
  const uid=authUid(request);
  const input=parseIdentityChat(request.data);
  const roomRef=db.collection("rooms").doc(input.roomId);
  const memberRef=roomRef.collection("members").doc(uid);
  const sessionRef=roomRef.collection("gameSessions").doc(input.sessionId);
  const personalRef=sessionRef.collection("privateAssignments").doc(uid);
  const messageId=sha([input.roomId,input.sessionId,uid,input.requestId]);
  const messageRef=sessionRef.collection("publicMessages").doc(messageId);
  const rateRef=sessionRef.collection("gameChatRateLimits").doc(uid);
  return db.runTransaction(async tx=>{
    const [room,member,session,personal,existing,rate,deletion]=await Promise.all([
      tx.get(roomRef),tx.get(memberRef),tx.get(sessionRef),
      tx.get(personalRef),tx.get(messageRef),tx.get(rateRef),
      tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 채팅할 수 없습니다.");
    if(!room.exists||room.get("status")!=="active"||
       !member.exists||member.get("status")!=="active"||
       !session.exists||room.get("activeGameSessionId")!==input.sessionId||
       session.get("gameId")!=="liar"||session.get("rulesVersion")!==1||
       !Object.hasOwn(session.get("participants")||{},uid)||
       !personal.exists||!["liar","citizen"].includes(personal.get("data")?.role)){
      throw new HttpsError("permission-denied","라이어 채팅 참가 권한이 없습니다.");
    }
    if(existing.exists){
      if(existing.get("senderUid")!==uid||existing.get("text")!==input.text){
        throw new HttpsError("already-exists","동일한 메시지 ID에 다른 내용을 저장할 수 없습니다.");
      }
      return {messageId,deduplicated:true};
    }
    if(session.get("phase")!=="playing"||!session.get("phaseEndsAt")||
       session.get("phaseEndsAt").toMillis()<=now()){
      throw new HttpsError("failed-precondition","라이어 게임 채팅 시간이 종료됐습니다.");
    }
    const last=rate.exists?rate.get("lastSentAt"):null;
    if(rate.exists&&!(last instanceof Timestamp)){
      throw new HttpsError("failed-precondition","채팅 간격을 확인할 수 없습니다.");
    }
    if(last&&now()<last.toMillis()+MIN_INTERVAL){
      throw new HttpsError("resource-exhausted","메시지를 너무 빠르게 보내고 있습니다.");
    }
    tx.create(messageRef,{
      senderUid:uid,text:input.text,createdAt:FieldValue.serverTimestamp(),
      expiresAt:Timestamp.fromMillis(now()+GAME_CHAT_DAYS*DAY_MS)
    });
    tx.set(rateRef,{lastSentAt:Timestamp.fromMillis(now())});
    return {messageId,deduplicated:false};
  });
}

module.exports={sendLiarMessage};
