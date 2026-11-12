"use strict";

const {test} = require("node:test");
const assert = require("node:assert/strict");
const {
  CATALOG_SCHEMA_VERSION, GAME_PHASES, GAME_DEFINITIONS, GameContractError,
  getGameDescriptor, isTerminalPhase, validateCatalog, validateActionEnvelope
} = require("../src/game/contracts");

const validAction = () => ({
  roomId: "12345678901234567890",
  sessionId: "session-0123456789",
  gameId: "identity",
  rulesVersion: 1,
  expectedRevision: 0,
  requestId: "183c9c18-46a1-423d-87d0-2cfa436afab9",
  payload: {choice: "example"}
});

test("catalog keeps four unique, versioned, bounded game definitions", () => {
  assert.equal(CATALOG_SCHEMA_VERSION, 1);
  assert.deepEqual(GAME_DEFINITIONS.map(game => game.id),
    ["identity", "liar", "forbidden", "balance"]);
  assert.ok(GAME_DEFINITIONS.every(game =>
    game.minPlayers >= 2 && game.maxPlayers <= 20 &&
    game.maxPlayers >= game.minPlayers && game.rulesVersion === 1));
  assert.deepEqual(getGameDescriptor("balance").modes, ["realtime", "async"]);
  assert.deepEqual(getGameDescriptor("identity").modes, ["realtime"]);
  assert.ok(Object.isFrozen(GAME_DEFINITIONS));
});

test("unknown game or mismatched rulesVersion is always rejected", () => {
  assert.throws(() => getGameDescriptor("other"), GameContractError);
  assert.throws(() => getGameDescriptor("identity", 2), GameContractError);
});

test("catalog rejects duplicate ids, invalid modes, and invalid player ranges", () => {
  const descriptor = {
    id: "custom", rulesVersion: 1, minPlayers: 2,
    maxPlayers: 10, modes: ["realtime"],
    hasSecretAssignments: false, hasAnonymousMessages: false
  };
  assert.throws(() => validateCatalog({schemaVersion: 1,
    games: [descriptor, descriptor]}), /Duplicate/);
  assert.throws(() => validateCatalog({schemaVersion: 1,
    games: [{...descriptor, maxPlayers: 21}]}), /Invalid game descriptor/);
  assert.throws(() => validateCatalog({schemaVersion: 1,
    games: [{...descriptor, modes: ["unsupported"]}]}), /Invalid game descriptor/);
  assert.throws(() => validateCatalog({schemaVersion: 2,
    games: [descriptor]}), /Invalid game catalog schema/);
});

test("game state is versioned and terminal phases are irreversible by contract", () => {
  assert.ok(GAME_PHASES.includes("assigning"));
  assert.ok(GAME_PHASES.includes("voting"));
  assert.equal(isTerminalPhase("finished"), true);
  assert.equal(isTerminalPhase("cancelled"), true);
  assert.equal(isTerminalPhase("playing"), false);
  assert.throws(() => isTerminalPhase("arbitrary"), GameContractError);
});

test("action envelopes require idempotency and optimistic revision", () => {
  const action = validateActionEnvelope(validAction());
  assert.equal(action.requestId, validAction().requestId);
  assert.equal(action.rulesVersion, 1);
  assert.equal(action.expectedRevision, 0);
  assert.ok(Object.isFrozen(action));
});

test("action envelopes fail closed on client-supplied identities and score metadata", () => {
  for (const injected of [
    {uid: "admin"},
    {score: 999},
    {role: "liar"},
    {serverTimestamp: 0}
  ]) {
    assert.throws(() => validateActionEnvelope({...validAction(), ...injected}),
      GameContractError);
  }
});

test("action envelopes reject missing versions, revisions, and request IDs", () => {
  for (const change of [
    {rulesVersion: undefined},
    {rulesVersion: 2},
    {expectedRevision: -1},
    {expectedRevision: "0"},
    {requestId: ""},
    {requestId: "short"},
    {payload: null},
    {roomId: "wrong"}
  ]) {
    assert.throws(() => validateActionEnvelope({...validAction(), ...change}),
      GameContractError);
  }
});
