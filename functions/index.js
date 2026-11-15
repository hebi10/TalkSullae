const { initializeApp,getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const {onDocumentCreated,onDocumentUpdated}=require("firebase-functions/v2/firestore");
const {getMessaging}=require("firebase-admin/messaging");
const room = require("./src/roomService");
const {saveMyProfile} = require("./src/userProfile");
const { submitSafetyReport } = require("./src/safetyReport");
const { sendRoomMessage } = require("./src/chatService");
const gameSession = require("./src/game/sessionService");
const {getMyCareer} = require("./src/game/careerService");
const identityChat = require("./src/game/identityChatService");
const liarChat = require("./src/game/liarChatService");
const forbiddenChat = require("./src/game/forbiddenChatService");
const safetyControls = require("./src/safetyControls");
const retention = require("./src/retention");
const push=require("./src/pushService");
const moderation=require("./src/moderationService");
const monitoring=require("./src/safetyMonitoring");
const logger=require("firebase-functions/logger");

if (!getApps().length) initializeApp();
const db = getFirestore();
const config = {region:"asia-northeast3",maxInstances:10,memory:"256MiB"};

exports.createRoom = onCall(config, request=>room.createRoom(db,request));
exports.saveMyProfile = onCall(config, request=>saveMyProfile(db,request));
exports.createInvitation = onCall(config, request=>room.createInvitation(db,request));
exports.previewInvitation = onCall(config, request=>room.previewInvitation(db,request));
exports.requestJoin = onCall(config, request=>room.requestJoin(db,request));
exports.changeMember = onCall(config, request=>room.changeMember(db,request));
exports.leaveRoom = onCall(config, request=>room.leaveRoom(db,request));
exports.submitSafetyReport = onCall(config, request=>submitSafetyReport(db,request));

exports.sendRoomMessage = onCall(config, request=>sendRoomMessage(db,request));
exports.registerPushToken = onCall(config, request=>push.registerPushToken(db,request));
exports.getModerationInbox = onCall(config, request=>moderation.getModerationInbox(db,request));
exports.setModerationStatus = onCall(config, request=>moderation.setModerationStatus(db,request));

exports.createGameSession = onCall(config, request=>gameSession.createGameSession(db,request));
exports.gameSessionAction = onCall(config, request=>gameSession.gameSessionAction(db,request));
exports.getMyGameSession = onCall(config, request=>gameSession.getMyGameSession(db,request));
exports.getMyCareer = onCall(config, request=>getMyCareer(db,request));
exports.findActiveGameSession = onCall(config, request=>gameSession.findActiveGameSession(db,request));
exports.findRecentCompletedGameSession = onCall(config,
  request=>gameSession.findRecentCompletedGameSession(db,request));

exports.sendIdentityMessage = onCall(config, request=>identityChat.sendIdentityMessage(db,request));
exports.sendLiarMessage = onCall(config, request=>liarChat.sendLiarMessage(db,request));
exports.sendForbiddenMessage = onCall(config, request=>forbiddenChat.sendForbiddenMessage(db,request));
exports.setUserBlock = onCall(config, request=>safetyControls.setUserBlock(db,request));
exports.reportChatMessage = onCall(config, request=>safetyControls.reportChatMessage(db,request));
exports.requestAccountDeletion = onCall(config, request=>safetyControls.requestAccountDeletion(db,request));
exports.processAccountDeletionQueue = onSchedule({
  schedule:"every 30 minutes",region:"asia-northeast3",maxInstances:1
}, async()=>{await safetyControls.processAccountDeletionQueue(db);});
exports.expireDueMessages = onSchedule({
  schedule:"every 60 minutes",region:"asia-northeast3",maxInstances:1
}, async()=>{await retention.expireDueMessages(db);});
exports.expireGameSessionDeadlines = onSchedule({
  schedule:"* * * * *",region:"asia-northeast3",maxInstances:1,
  timeZone:"Asia/Seoul"
}, async()=>{ await gameSession.expireDueGameSessions(db); });


// Generic, data-only FCM events. No chat text, aliases, identities or game secrets.
exports.notifyRoomMessage = onDocumentCreated({
  document:"rooms/{roomId}/messages/{messageId}",
  region:"asia-northeast3"
},async event=>{
  const content=event.data?.data();
  if(!content||typeof content.senderUid!=="string")return;
  await push.notifyRoomActivity(
    db,event.params.roomId,"message",content.senderUid,getMessaging());
});

exports.notifyGameStart = onDocumentUpdated({
  document:"rooms/{roomId}/gameSessions/{sessionId}",
  region:"asia-northeast3"
},async event=>{
  const before=event.data?.before.data();
  const after=event.data?.after.data();
  if(!before||!after||before.phase==="playing"||after.phase!=="playing")return;
  await push.notifyRoomActivity(db,event.params.roomId,"game",null,getMessaging());
});

exports.notifyMemberApproval = onDocumentUpdated({
  document:"rooms/{roomId}/members/{uid}",
  region:"asia-northeast3"
},async event=>{
  const before=event.data?.before.data();
  const after=event.data?.after.data();
  if(before?.status!=="pending"||after?.status!=="active")return;
  await push.notifyApprovedMember(
    db,event.params.roomId,event.params.uid,getMessaging());
});


// Operator-only, counts-only 2-hour audit. Alerts are configured separately.
exports.auditSafetyBacklog = onSchedule({
  schedule:"every 120 minutes",region:"asia-northeast3",maxInstances:1,
  timeZone:"Asia/Seoul"
},async ()=>{
  const summary=await monitoring.inspectSafetyBacklog(db);
  if(summary.reportsOver24Hours>0||summary.deletionManualReview>0||
     summary.deletionNeedsAttention>0||summary.scanCapReached){
    logger.warn("talksullae_safety_backlog_counts",summary);
  }
});
