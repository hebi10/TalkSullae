"use strict";

const {FieldValue} = require("firebase-admin/firestore");
const {HttpsError} = require("firebase-functions/v2/https");
const {authUid,memberName} = require("./policy");

const AVATARS = new Set(["부엉이","토끼","고양이","강아지","곰","여우","판다","펭귄"]);
const MAX_ROOMS = 100;

function validateProfile(input) {
  if (!input || typeof input !== "object" || Array.isArray(input) ||
      Object.keys(input).some(key => !["nickname","avatar"].includes(key)) ||
      typeof input.nickname !== "string" || typeof input.avatar !== "string") {
    throw new HttpsError("invalid-argument","프로필 입력을 확인해 주세요.");
  }
  const nickname = input.nickname.normalize("NFC").trim();
  if (Array.from(nickname).length < 2 || Array.from(nickname).length > 16 ||
      /[\u0000-\u001f\u007f-\u009f]/u.test(nickname)) {
    throw new HttpsError("invalid-argument","닉네임은 2~16자 이내로 입력해 주세요.");
  }
  if (!AVATARS.has(input.avatar)) {
    throw new HttpsError("invalid-argument","기본 아바타를 선택해 주세요.");
  }
  return {nickname,avatar:input.avatar};
}

function memberDisplayName(profile,auth) {
  const nickname = profile?.exists ? profile.get("nickname") : null;
  return typeof nickname === "string" && nickname.length > 0
    ? nickname : memberName(auth);
}

async function saveMyProfile(db,request) {
  const uid = authUid(request);
  const values = validateProfile(request.data);
  const ref = db.collection("users").doc(uid);
  const deletionRef = db.collection("accountDeletionJobs").doc(uid);
  const indexQuery = db.collection("userRooms").doc(uid)
    .collection("rooms").limit(MAX_ROOMS + 1);

  return db.runTransaction(async tx => {
    const [existing,deletion,rooms] = await Promise.all([
      tx.get(ref),tx.get(deletionRef),tx.get(indexQuery)
    ]);
    if (deletion.exists) {
      throw new HttpsError("permission-denied","계정 삭제 요청 중에는 프로필을 변경할 수 없습니다.");
    }
    if (rooms.size > MAX_ROOMS) {
      throw new HttpsError("resource-exhausted","가입한 방이 많아 운영자 확인이 필요합니다.");
    }
    const memberRefs = rooms.docs
      .filter(item => ["active","pending"].includes(item.get("status")))
      .map(item => db.collection("rooms").doc(item.id).collection("members").doc(uid));
    const memberships = await Promise.all(memberRefs.map(item => tx.get(item)));
    if (existing.exists) {
      tx.update(ref,{...values,updatedAt:FieldValue.serverTimestamp()});
    } else {
      tx.create(ref,{
        ...values,createdAt:FieldValue.serverTimestamp(),
        updatedAt:FieldValue.serverTimestamp()
      });
    }
    for (const member of memberships) {
      if (member.exists && ["active","pending"].includes(member.get("status"))) {
        tx.update(member.ref,{
          displayName:values.nickname,avatar:values.avatar,
          updatedAt:FieldValue.serverTimestamp()
        });
      }
    }
    return values;
  });
}

module.exports={AVATARS,MAX_ROOMS,validateProfile,memberDisplayName,saveMyProfile};
