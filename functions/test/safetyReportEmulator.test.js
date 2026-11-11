const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { createHash, randomUUID } = require("node:crypto");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { submitSafetyReport } = require("../src/safetyReport");

let app, db;
before(() => {
  assert.match(process.env.FIRESTORE_EMULATOR_HOST || "", /^(?:127\.0\.0\.1|localhost):\d+$/,
    "Safety report integration tests require a local Firestore emulator");
  app = initializeApp({ projectId: "demo-talksullae" }, "safety-report-tests");
  db = getFirestore(app);
});
after(async () => { if (app) await deleteApp(app); });

const requestFor = (uid, overrides = {}) => ({
  auth: { uid, token: { name: "Non-stored display name" } },
  data: {
    requestId: randomUUID(),
    category: "child_safety",
    details: "  안전 문제가 발생했습니다.\n두 번째 줄입니다.  ",
    roomReference: "  reference-1  ",
    ...overrides
  }
});

function rateRef(uid) {
  const id = createHash("sha256").update(uid).digest("hex");
  return db.collection("safetyReportRateLimits").doc(id);
}

test("stores only normalized text, authenticated UID, and a server timestamp", async () => {
  const uid = randomUUID();
  const request = requestFor(uid, {
    requestId: randomUUID().toUpperCase(),
    details: "  " + "한".repeat(1000) + "  ",
    roomReference: "  " + "r".repeat(100) + "  "
  });
  const { reportId } = await submitSafetyReport(db, request);
  assert.match(reportId, /^[a-f0-9]{64}$/);
  const stored = (await db.collection("safetyReports").doc(reportId).get()).data();
  assert.ok(stored.createdAt instanceof Timestamp);
  const { createdAt, ...fields } = stored;
  assert.deepEqual(fields, {
    actorUid: uid,
    requestId: request.data.requestId.toLowerCase(),
    category: "child_safety",
    details: "한".repeat(1000),
    roomReference: "r".repeat(100),
    status: "received"
  });
  const rate = (await rateRef(uid).get()).data();
  assert.deepEqual(Object.keys(rate), ["lastSubmittedAt"]);
  assert.ok(rate.lastSubmittedAt instanceof Timestamp);
});

test("parallel retries are idempotent and a reused UUID cannot change the report", async () => {
  const uid = randomUUID();
  const request = requestFor(uid);
  delete request.data.roomReference;
  const retry = {
    ...request, data: { ...request.data, details: request.data.details.trim(), roomReference: "  " }
  };
  const [first, second] = await Promise.all([
    submitSafetyReport(db, request), submitSafetyReport(db, retry)
  ]);
  assert.deepEqual(first, second);
  const reports = await db.collection("safetyReports").where("actorUid", "==", uid).get();
  assert.equal(reports.size, 1);
  assert.equal(reports.docs[0].data().roomReference, null);
  await assert.rejects(submitSafetyReport(db, {
    ...request, data: { ...request.data, details: "다른 신고 내용을 보냅니다." }
  }), { code: "already-exists" });
  assert.deepEqual(await submitSafetyReport(db, request), first);
});

test("concurrent distinct requests enforce one per UID, and an expired limit allows another", async () => {
  const uid = randomUUID();
  const first = requestFor(uid, { category: "abuse" });
  const second = requestFor(uid, { category: "other" });
  const results = await Promise.allSettled([submitSafetyReport(db, first), submitSafetyReport(db, second)]);
  const accepted = results.filter(result => result.status === "fulfilled");
  const denied = results.filter(result => result.status === "rejected");
  assert.equal(accepted.length, 1);
  assert.equal(denied.length, 1);
  assert.equal(denied[0].reason.code, "resource-exhausted");

  const rejectedRequest = results[0].status === "rejected" ? first : second;
  const differentUser = requestFor(randomUUID(), { requestId: rejectedRequest.data.requestId });
  const isolated = await submitSafetyReport(db, differentUser);
  assert.notEqual(isolated.reportId, accepted[0].value.reportId);

  await rateRef(uid).set({ lastSubmittedAt: Timestamp.fromMillis(Date.now() - 61_000) });
  const afterCooldown = await submitSafetyReport(db, rejectedRequest);
  assert.notEqual(afterCooldown.reportId, accepted[0].value.reportId);
  assert.notEqual(afterCooldown.reportId, isolated.reportId);
});
