const { HttpsError } = require("firebase-functions/v2/https");

const ROOM_CAPACITY = 20;
const PENDING_CAPACITY = 30;
const INVITE_DURATION_MS = 24 * 60 * 60 * 1000;

function asText(value, max) {
  return typeof value === "string" ? value.trim().slice(0, max + 1) : "";
}

function requiredText(value, min, max, label) {
  const s = asText(value, max);
  if (s.length < min || s.length > max || /[\r\n]/.test(s)) {
    throw new HttpsError("invalid-argument", `${label}: ${min}~${max}자 입력이 필요합니다.`);
  }
  return s;
}

function roomDraft(input) {
  const name = requiredText(input?.name, 2, 24, "방 이름");
  const description = input?.description ?? "";
  if (typeof description !== "string" || description.length > 120) {
    throw new HttpsError("invalid-argument", "설명은 최대 120자입니다.");
  }
  if (typeof input?.requiresApproval !== "boolean") {
    throw new HttpsError("invalid-argument", "가입 승인 설정이 필요합니다.");
  }
  return {name, description: description.trim(), requiresApproval: input.requiresApproval};
}

function roomIdValue(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9]{20}$/.test(value)) {
    throw new HttpsError("invalid-argument", "방 ID 형식이 올바르지 않습니다.");
  }
  return value;
}

function parseJoinCode(code) {
  if (typeof code !== "string") throw new HttpsError("invalid-argument", "초대 코드를 입력해 주세요.");
  const parts = code.trim().split(".");
  if (parts.length !== 2 || !/^[A-Za-z0-9]{20}$/.test(parts[0]) ||
      !/^[A-Za-z0-9_-]{43}$/.test(parts[1])) {
    throw new HttpsError("invalid-argument", "초대 코드가 올바르지 않습니다.");
  }
  return {roomId:parts[0], token:parts[1]};
}

function authUid(request) {
  const uid = request?.auth?.uid;
  if (!uid || typeof uid !== "string") throw new HttpsError("unauthenticated", "Google 로그인 후 이용해 주세요.");
  return uid;
}

function memberName(auth) {
  return asText(auth?.token?.name, 30).replace(/[\r\n]/g, " ") || "멤버";
}

module.exports = {
  ROOM_CAPACITY,PENDING_CAPACITY,INVITE_DURATION_MS,
  roomDraft,roomIdValue,parseJoinCode,authUid,memberName
};
