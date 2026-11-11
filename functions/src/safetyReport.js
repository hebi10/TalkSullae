const { createHash } = require("node:crypto");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");
const { HttpsError } = require("firebase-functions/v2/https");
const { authUid } = require("./policy");

const ALLOWED_KEYS = new Set(["requestId", "category", "details", "roomReference"]);
const CATEGORIES = new Set(["child_safety", "abuse", "other"]);
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const COOLDOWN_MS = 60_000;

const sha256 = text => createHash("sha256").update(text).digest("hex");

function boundedText(value, min, max, label) {
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `${label}은(는) 글로 입력해 주세요.`);
  }
  const text = value.trim();
  if (text.length < min || text.length > max) {
    throw new HttpsError("invalid-argument", `${label}: ${min}~${max}자 입력이 필요합니다.`);
  }
  return text;
}

function parseReport(input) {
  if (!input || typeof input !== "object" || Array.isArray(input) ||
      Object.keys(input).some(key => !ALLOWED_KEYS.has(key))) {
    throw new HttpsError("invalid-argument", "신고는 지정된 항목에 글로만 접수할 수 있습니다.");
  }
  if (!Object.hasOwn(input, "requestId") || typeof input.requestId !== "string" ||
      input.requestId.length !== 36 || !UUID_V4.test(input.requestId)) {
    throw new HttpsError("invalid-argument", "신고 요청 ID 형식이 올바르지 않습니다.");
  }
  if (!Object.hasOwn(input, "category") || !CATEGORIES.has(input.category)) {
    throw new HttpsError("invalid-argument", "지원하는 신고 유형을 선택해 주세요.");
  }
  const details = boundedText(Object.hasOwn(input, "details") ? input.details : undefined, 5, 1000, "신고 내용");
  const roomReference = Object.hasOwn(input, "roomReference")
    ? boundedText(input.roomReference, 0, 100, "방 참고 정보") || null
    : null;
  return { requestId: input.requestId.toLowerCase(), category: input.category, details, roomReference };
}

async function submitSafetyReport(db, request) {
  const uid = authUid(request);
  const report = parseReport(request.data);
  const reportId = sha256(JSON.stringify([uid, report.requestId]));
  const reportRef = db.collection("safetyReports").doc(reportId);
  const rateRef = db.collection("safetyReportRateLimits").doc(sha256(uid));

  return db.runTransaction(async tx => {
    const existing = await tx.get(reportRef);
    if (existing.exists) {
      const stored = existing.data();
      if (stored.actorUid !== uid || stored.requestId !== report.requestId ||
          stored.category !== report.category || stored.details !== report.details ||
          stored.roomReference !== report.roomReference) {
        throw new HttpsError("already-exists", "같은 요청 ID로 다른 신고를 접수할 수 없습니다.");
      }
      return { reportId };
    }

    const rate = await tx.get(rateRef);
    if (rate.exists) {
      const lastSubmittedAt = rate.get("lastSubmittedAt");
      if (!(lastSubmittedAt instanceof Timestamp)) {
        throw new HttpsError("internal", "잠시 후 다시 시도해 주세요.");
      }
      if (Date.now() < lastSubmittedAt.toMillis() + COOLDOWN_MS) {
        throw new HttpsError("resource-exhausted", "마지막 신고 접수 후 1분 뒤에 새 신고를 접수해 주세요.");
      }
    }

    tx.create(reportRef, {
      ...report, actorUid: uid, createdAt: FieldValue.serverTimestamp(), status: "received"
    });
    tx.set(rateRef, { lastSubmittedAt: FieldValue.serverTimestamp() });
    return { reportId };
  });
}

module.exports = { submitSafetyReport };
