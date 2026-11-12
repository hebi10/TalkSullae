const { initializeApp,getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const room = require("./src/roomService");
const { submitSafetyReport } = require("./src/safetyReport");
const { sendRoomMessage } = require("./src/chatService");
const gameSession = require("./src/game/sessionService");
const identityChat = require("./src/game/identityChatService");

if (!getApps().length) initializeApp();
const db = getFirestore();
const config = {region:"asia-northeast3",maxInstances:10,memory:"256MiB"};

exports.createRoom = onCall(config, request=>room.createRoom(db,request));
exports.createInvitation = onCall(config, request=>room.createInvitation(db,request));
exports.requestJoin = onCall(config, request=>room.requestJoin(db,request));
exports.changeMember = onCall(config, request=>room.changeMember(db,request));
exports.leaveRoom = onCall(config, request=>room.leaveRoom(db,request));
exports.submitSafetyReport = onCall(config, request=>submitSafetyReport(db,request));

exports.sendRoomMessage = onCall(config, request=>sendRoomMessage(db,request));

exports.createGameSession = onCall(config, request=>gameSession.createGameSession(db,request));
exports.gameSessionAction = onCall(config, request=>gameSession.gameSessionAction(db,request));
exports.getMyGameSession = onCall(config, request=>gameSession.getMyGameSession(db,request));
exports.findActiveGameSession = onCall(config, request=>gameSession.findActiveGameSession(db,request));

exports.sendIdentityMessage = onCall(config, request=>identityChat.sendIdentityMessage(db,request));
exports.expireGameSessionDeadlines = onSchedule({
  schedule:"every 1 minutes",region:"asia-northeast3",maxInstances:1,
  timeZone:"Asia/Seoul"
}, async()=>{ await gameSession.expireDueGameSessions(db); });
