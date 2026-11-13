"use strict";

const {createHash}=require("node:crypto");
const {FieldValue,Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid}=require("../policy");
const {parseIdentityChat,MIN_INTERVAL}=require("./identityChatService");
const {DAY_MS,GAME_CHAT_DAYS}=require("../retention");
const forbidden=require("./forbiddenRules");

const hash=value=>createHash("sha256").update(JSON.stringify(value)).digest("hex");

async function sendForbiddenMessage(db,request,now=Date.now){
  const uid=authUid(request);
  const input=parseIdentityChat(request.data);
  const roomRef=db.collection("rooms").doc(input.roomId);
  const memberRef=roomRef.collection("members").doc(uid);
  const sessionRef=roomRef.collection("gameSessions").doc(input.sessionId);
  const secretRef=sessionRef.collection("privateAssignments").doc(uid);
  const messageId=hash([input.roomId,input.sessionId,uid,input.requestId]);
  const messageRef=sessionRef.collection("publicMessages").doc(messageId);
  const rateRef=sessionRef.collection("gameChatRateLimits").doc(uid);
  return db.runTransaction(async tx=>{
    const [room,member,session,secret,existing,rate,deletion]=await Promise.all([
      tx.get(roomRef),tx.get(memberRef),tx.get(sessionRef),
      tx.get(secretRef),tx.get(messageRef),tx.get(rateRef),
      tx.get(db.collection("accountDeletionJobs").doc(uid))
    ]);
    if(deletion.exists)throw new HttpsError("permission-denied","계정 삭제 요청 중에는 게임 채팅을 보낼 수 없습니다.");
    if(!room.exists||room.get("status")!=="active"||
       !member.exists||member.get("status")!=="active"||
       !session.exists||room.get("activeGameSessionId")!==input.sessionId||
       session.get("gameId")!=="forbidden"||session.get("rulesVersion")!==1||
       !Object.hasOwn(session.get("participants")||{},uid)||
       !secret.exists||!forbidden.WORDS.includes(secret.get("data")?.forbiddenWord)){
      throw new HttpsError("permission-denied","금지어 채팅에 참가할 권한이 없습니다.");
    }
    if(existing.exists){
      if(existing.get("senderUid")!==uid||existing.get("text")!==input.text){
        throw new HttpsError("already-exists","같은 메시지 ID는 다른 내용으로 사용할 수 없습니다.");
      }
      return {messageId,triggered:existing.get("triggered")===true,deduplicated:true};
    }
    const current=now();
    if(session.get("phase")!=="playing"||!session.get("phaseEndsAt")||
       session.get("phaseEndsAt").toMillis()<=current){
      throw new HttpsError("failed-precondition","금지어 채팅 시간이 종료됐습니다.");
    }
    const eliminated=forbidden.requireEliminations(session.data());
    if(eliminated.includes(uid)){
      throw new HttpsError("failed-precondition","탈락한 참가자는 메시지를 보낼 수 없습니다.");
    }
    const last=rate.exists?rate.get("lastSentAt"):null;
    if(rate.exists&&!(last instanceof Timestamp)){
      throw new HttpsError("failed-precondition","메시지 전송 간격이 잘못되었습니다.");
    }
    if(last&&current<last.toMillis()+MIN_INTERVAL){
      throw new HttpsError("resource-exhausted","메시지를 너무 빠르게 보내고 있습니다.");
    }
    const triggered=forbidden.isForbidden(input.text,secret.get("data").forbiddenWord);
    tx.create(messageRef,{
      senderUid:uid,text:input.text,triggered,
      createdAt:FieldValue.serverTimestamp(),
      expiresAt:Timestamp.fromMillis(current+GAME_CHAT_DAYS*DAY_MS)
    });
    tx.set(rateRef,{lastSentAt:Timestamp.fromMillis(current)});
    if(triggered){
      tx.update(sessionRef,{
        "publicState.eliminatedUids":[...eliminated,uid],
        revision:FieldValue.increment(1),
        updatedAt:FieldValue.serverTimestamp()
      });
    }
    return {messageId,triggered,deduplicated:false};
  });
}
module.exports={sendForbiddenMessage};
