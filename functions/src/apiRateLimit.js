"use strict";

const {createHash}=require("node:crypto");
const {Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {authUid}=require("./policy");

const LIMITS=Object.freeze({
  createRoom:{max:20,windowMs:60*60*1000},
  createInvitation:{max:20,windowMs:60*1000},
  revokeInvitation:{max:20,windowMs:60*1000},
  previewInvitation:{max:60,windowMs:60*1000},
  requestJoin:{max:30,windowMs:60*1000},
  updateRoom:{max:20,windowMs:60*1000},
  updateRoomIcon:{max:20,windowMs:60*1000},
  archiveRoom:{max:10,windowMs:60*1000},
  getRoomFriendRecords:{max:6,windowMs:60*1000}
});

async function consumeRoomQuota(db,uid,operation,now=Date.now){
  const rule=LIMITS[operation];
  if(!rule)throw new Error("Unknown room operation");
  const key=createHash("sha256").update(JSON.stringify([uid,operation])).digest("hex");
  const ref=db.collection("roomOperationLimits").doc(key);
  const timestamp=now();
  const windowStart=Math.floor(timestamp/rule.windowMs)*rule.windowMs;
  await db.runTransaction(async tx=>{
    const snap=await tx.get(ref);
    const sameWindow=snap.exists&&snap.get("windowStart")===windowStart;
    const count=sameWindow?snap.get("count"):0;
    if(!Number.isSafeInteger(count)||count<0){
      throw new HttpsError("internal","요청 제한 상태를 확인할 수 없습니다.");
    }
    if(count>=rule.max){
      throw new HttpsError("resource-exhausted","요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
    }
    tx.set(ref,{windowStart,count:count+1,
      expiresAt:Timestamp.fromMillis(windowStart+rule.windowMs+24*60*60*1000)});
  });
}

async function guardRoomOperation(db,request,operation,handler){
  const uid=authUid(request);
  await consumeRoomQuota(db,uid,operation);
  return handler(db,request);
}

module.exports={LIMITS,consumeRoomQuota,guardRoomOperation};
