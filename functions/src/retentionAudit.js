"use strict";

const {Timestamp,FieldPath}=require("firebase-admin/firestore");
const {ROOM_CHAT_DAYS,GAME_CHAT_DAYS,DAY_MS}=require("./retention");

function classify(path,createdAt,expiresAt,cutoff){
  const segments=path.split("/");
  const room=segments.length===4 && segments[0]==="rooms" && segments[2]==="messages";
  const game=segments.length===6 && segments[0]==="rooms" && segments[2]==="gameSessions" && segments[4]==="publicMessages";
  if(!room&&!game)return "unrecognizedPath";
  if(expiresAt instanceof Timestamp)return expiresAt.toMillis()<=cutoff?"indexedDue":"indexedFuture";
  if(expiresAt!==undefined&&expiresAt!==null)return "invalidExpiry";
  if(!(createdAt instanceof Timestamp)||createdAt.toMillis()>cutoff)return "missingExpiryInvalidCreatedAt";
  const days=room?ROOM_CHAT_DAYS:GAME_CHAT_DAYS;
  return createdAt.toMillis()+days*DAY_MS<=cutoff?"legacyDue":"legacyFuture";
}

async function scanRetention(db,{maxDocuments=2000,pageSize=200,now=Date.now}={}){
  if(!Number.isSafeInteger(maxDocuments)||maxDocuments<1||maxDocuments>100000 ||
     !Number.isSafeInteger(pageSize)||pageSize<1||pageSize>500)throw new Error("Invalid audit limits");
  const cutoff=now(), result={cutoffMillis:cutoff,groups:{}};
  for(const group of ["messages","publicMessages"]){
    let cursor=null,scanned=0,complete=false;
    const counts={indexedDue:0,indexedFuture:0,invalidExpiry:0,legacyDue:0,legacyFuture:0,missingExpiryInvalidCreatedAt:0,unrecognizedPath:0};
    while(scanned<maxDocuments){
      const size=Math.min(pageSize,maxDocuments-scanned);
      let query=db.collectionGroup(group).select("createdAt","expiresAt").orderBy(FieldPath.documentId()).limit(size);
      if(cursor)query=query.startAfter(cursor);
      const page=await query.get();
      for(const doc of page.docs){
        counts[classify(doc.ref.path,doc.get("createdAt"),doc.get("expiresAt"),cutoff)]++;
        scanned++;
      }
      if(page.size<size){complete=true;break;}
      cursor=page.docs.at(-1);
    }
    result.groups[group]={scanned,complete,counts};
  }
  return result;
}

module.exports={classify,scanRetention};
