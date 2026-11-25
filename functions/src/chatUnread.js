"use strict";

const {Timestamp}=require("firebase-admin/firestore");
const {HttpsError}=require("firebase-functions/v2/https");
const {ROOM_CHAT_DAYS,DAY_MS}=require("./retention");

function number(value){
  if(!Number.isSafeInteger(value)||value<0)throw new HttpsError("failed-precondition","읽음 상태를 확인할 수 없습니다.");
  return value;
}

function activity(room){
  if(room.unreadEpoch===undefined)return null;
  const epoch=number(room.unreadEpoch),sequence=number(room.unreadSequence);
  const counts=room.unreadSentCounts;
  if(epoch<1||!counts||typeof counts!=="object"||Array.isArray(counts))throw new HttpsError("failed-precondition","읽음 버전을 확인할 수 없습니다.");
  for(const value of Object.values(counts))if(number(value)>sequence)throw new HttpsError("failed-precondition","읽음 수를 확인할 수 없습니다.");
  return {epoch,sequence,counts};
}

function ownCount(state,uid){
  return Object.hasOwn(state.counts,uid)?state.counts[uid]:0;
}

function nextActivity(room,activeUids,uid,now){
  const previous=activity(room);
  const expired=!(room.lastChatAt instanceof Timestamp)||room.lastChatAt.toMillis()<=now-ROOM_CHAT_DAYS*DAY_MS;
  const reset=previous!==null&&expired;
  const epoch=previous?number(previous.epoch+(reset?1:0)):1;
  const start=previous&&!reset?previous:{epoch,sequence:0,counts:{}};
  const sequence=number(start.sequence+1);
  const counts=Object.fromEntries(activeUids.filter(id=>Object.hasOwn(start.counts,id)||id===uid)
    .map(id=>[id,number(ownCount(start,id)+(id===uid?1:0))]));
  return {epoch,sequence,counts,migrating:previous===null,expired};
}

function readBaseline(state,uid){
  return {unreadSchema:1,chatReadEpoch:state.epoch,chatReadSequence:state.sequence,
    chatReadOwnSent:ownCount(state,uid),unreadCarry:0,unreadCount:0};
}

function joinReadBaseline(room,uid){
  const state=activity(room);
  return state?readBaseline(state,uid):{};
}

function migrationBaseline(index,expired,now){
  const last=index.lastChatAt;
  const carry=!expired&&last instanceof Timestamp&&last.toMillis()>now-ROOM_CHAT_DAYS*DAY_MS
    ?number(index.unreadCount??0):0;
  return {unreadSchema:1,chatReadEpoch:1,chatReadSequence:0,chatReadOwnSent:0,unreadCarry:carry};
}

function unreadFor(state,index,uid){
  const readEpoch=number(index.chatReadEpoch),own=ownCount(state,uid);
  if(readEpoch>state.epoch)throw new HttpsError("failed-precondition","읽음 버전이 올바르지 않습니다.");
  const same=readEpoch===state.epoch;
  const baseline=same?number(index.chatReadSequence):0;
  const ownBaseline=same?number(index.chatReadOwnSent):0;
  const carry=same?number(index.unreadCarry??0):0;
  const delta=state.sequence-baseline-(own-ownBaseline);
  if(baseline>state.sequence||ownBaseline>own||delta<0)throw new HttpsError("failed-precondition","읽음 기준을 확인할 수 없습니다.");
  return number(carry+delta);
}

function removeSender(room,uid){
  const counts=room.unreadSentCounts;
  if(!counts||typeof counts!=="object"||Array.isArray(counts)||!Object.hasOwn(counts,uid))return {};
  return {unreadSentCounts:Object.fromEntries(Object.entries(counts).filter(([id])=>id!==uid))};
}

module.exports={activity,nextActivity,readBaseline,joinReadBaseline,migrationBaseline,unreadFor,removeSender};
