const { test } = require("node:test");
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const { submitSafetyReport } = require("../src/safetyReport");

const db = {
  collection() { throw new Error("Invalid requests must not access Firestore"); }
};
const data = {
  requestId: randomUUID(),
  category: "child_safety",
  details: "안전 문제가 발생했습니다."
};

test("reports require a verified callable UID before Firestore access", async () => {
  for (const auth of [undefined, {}, { uid: "" }, { uid: 123 }]) {
    await assert.rejects(submitSafetyReport(db, { auth, data }), { code: "unauthenticated" });
  }
});

test("reports reject forged server fields, attachments, and unknown fields", async () => {
  for (const key of ["actorUid", "uid", "createdAt", "status", "attachments", "attachment", "imageUrl", "reportId"]) {
    await assert.rejects(submitSafetyReport(db, {
      auth: { uid: "authenticated-user" },
      data: { ...data, [key]: "untrusted input" }
    }), { code: "invalid-argument" });
  }
});

test("reports validate UUID v4, category, trimmed text lengths, and text-only input", async () => {
  const invalidInputs = [
    null, [], "message", {}, Object.create(data),
    { ...data, requestId: 42 },
    { ...data, requestId: "00000000-0000-1000-8000-000000000001" },
    { ...data, requestId: "00000000-0000-4000-7000-000000000001" },
    { ...data, requestId: data.requestId + "a" },
    { ...data, category: " child_safety " },
    { ...data, category: ["abuse"] },
    { ...data, details: " 1234 " },
    { ...data, details: "x".repeat(1001) },
    { ...data, details: { text: "structured object" } },
    { ...data, roomReference: "x".repeat(101) },
    { ...data, roomReference: null },
    { ...data, roomReference: 12 }
  ];
  for (const input of invalidInputs) {
    await assert.rejects(submitSafetyReport(db, {
      auth: { uid: "authenticated-user" },
      data: input
    }), { code: "invalid-argument" });
  }
});
