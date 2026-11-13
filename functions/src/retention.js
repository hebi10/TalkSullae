"use strict";

const {Timestamp}=require("firebase-admin/firestore");

const DAY_MS=24*60*60*1000;
const ROOM_CHAT_DAYS=90;
const GAME_CHAT_DAYS=7;

const expiry=(now,days)=>Timestamp.fromMillis(now+days*DAY_MS);

// Only indexed, due documents are deleted. Existing documents without expiresAt
// remain untouched until an explicit migration reviews their retention requirements.
async function expireDueMessages(db,now=Date.now){
  const counts={roomChat:0,gameChat:0};
  const groups=[["messages","roomChat"],["publicMessages","gameChat"]];
  for(const [collection,field] of groups){
    // Drain a bounded number of pages per hourly run, so a normal traffic spike
    // does not leave all but the first batch waiting for another full day.
    for(let page=0;page<50;page++){
      const due=await db.collectionGroup(collection)
        .where("expiresAt","<=",Timestamp.fromMillis(now()))
        .limit(200).get();
      if(due.empty)break;
      const batch=db.batch();
      for(const message of due.docs){
        batch.delete(message.ref);
        counts[field]++;
      }
      await batch.commit();
      if(due.size<200)break;
    }
  }
  return counts;
}

module.exports={DAY_MS,ROOM_CHAT_DAYS,GAME_CHAT_DAYS,expiry,expireDueMessages};
