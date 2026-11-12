const { initializeApp,getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { onCall } = require("firebase-functions/v2/https");
const room = require("./src/roomService");
const { submitSafetyReport } = require("./src/safetyReport");
const { sendRoomMessage } = require("./src/chatService");

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
