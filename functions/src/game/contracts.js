"use strict";

/**
 * GAME-00: pure contracts only. No Functions exports, database access, or game actions.
 * The catalog is pinned to explicit audited modules, not dynamically executable code.
 */
const rawCatalog = require("./catalog.v1.json");

const CATALOG_SCHEMA_VERSION = 1;
const GAME_PHASES = Object.freeze([
  "waiting", "ready", "assigning", "playing", "voting",
  "reveal", "finished", "cancelled"
]);
const GAME_MODES = Object.freeze(["realtime", "async"]);
const TERMINAL_PHASES = Object.freeze(["finished", "cancelled"]);
const ROOM_ID_PATTERN = /^[A-Za-z0-9]{20}$/;
const SESSION_ID_PATTERN = /^[A-Za-z0-9_-]{8,100}$/;
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,80}$/;
const GAME_ID_PATTERN = /^[a-z][a-z0-9_-]{1,39}$/;
const ALLOWED_ENVELOPE_KEYS = new Set([
  "roomId", "sessionId", "gameId", "rulesVersion",
  "expectedRevision", "requestId", "payload"
]);

class GameContractError extends Error {
  constructor(message) {
    super(message);
    this.name = "GameContractError";
  }
}

function fail(message) {
  throw new GameContractError(message);
}

function isPlainObject(value) {
  return value != null && typeof value === "object" &&
    !Array.isArray(value) && Object.getPrototypeOf(value) === Object.prototype;
}

function validateCatalog(value) {
  if (!isPlainObject(value) || value.schemaVersion !== CATALOG_SCHEMA_VERSION ||
      !Array.isArray(value.games) || value.games.length === 0) {
    fail("Invalid game catalog schema");
  }
  const ids = new Set();
  const games = value.games.map(item => {
    if (!isPlainObject(item) ||
        typeof item.id !== "string" || !GAME_ID_PATTERN.test(item.id) ||
        !Number.isSafeInteger(item.rulesVersion) || item.rulesVersion < 1 ||
        !Number.isSafeInteger(item.minPlayers) || item.minPlayers < 2 ||
        !Number.isSafeInteger(item.maxPlayers) || item.maxPlayers > 20 ||
        item.maxPlayers < item.minPlayers ||
        !Array.isArray(item.modes) || item.modes.length === 0 ||
        new Set(item.modes).size !== item.modes.length ||
        item.modes.some(mode => !GAME_MODES.includes(mode)) ||
        typeof item.hasSecretAssignments !== "boolean" ||
        typeof item.hasAnonymousMessages !== "boolean") {
      fail("Invalid game descriptor");
    }
    if (ids.has(item.id)) fail("Duplicate game id");
    ids.add(item.id);
    return Object.freeze({
      id: item.id, rulesVersion: item.rulesVersion,
      minPlayers: item.minPlayers, maxPlayers: item.maxPlayers,
      modes: Object.freeze([...item.modes]),
      hasSecretAssignments: item.hasSecretAssignments,
      hasAnonymousMessages: item.hasAnonymousMessages
    });
  });
  return Object.freeze(games);
}

const GAME_DEFINITIONS = validateCatalog(rawCatalog);
const gamesById = new Map(GAME_DEFINITIONS.map(game => [game.id, game]));

function getGameDescriptor(gameId, rulesVersion) {
  const descriptor = gamesById.get(gameId);
  if (!descriptor || (rulesVersion !== undefined && rulesVersion !== descriptor.rulesVersion)) {
    fail("Unsupported game type or rules version");
  }
  return descriptor;
}

function isTerminalPhase(phase) {
  if (!GAME_PHASES.includes(phase)) fail("Unknown game phase");
  return TERMINAL_PHASES.includes(phase);
}

/**
 * Every gameplay action will supply an authenticated callable request.
 * The envelope never accepts client-provided uid, role, scores or timestamps.
 * GAME-02 must validate each game's payload against the game-specific rules.
 */
function validateActionEnvelope(value) {
  if (!isPlainObject(value)) fail("Game action envelope must be an object");
  if (Object.keys(value).some(key => !ALLOWED_ENVELOPE_KEYS.has(key))) {
    fail("Unknown action envelope field");
  }
  if (typeof value.roomId !== "string" || !ROOM_ID_PATTERN.test(value.roomId) ||
      typeof value.sessionId !== "string" || !SESSION_ID_PATTERN.test(value.sessionId) ||
      !Number.isSafeInteger(value.expectedRevision) || value.expectedRevision < 0 ||
      typeof value.requestId !== "string" || !REQUEST_ID_PATTERN.test(value.requestId)) {
    fail("Invalid session identity or request revision");
  }
  if (!Number.isSafeInteger(value.rulesVersion) || value.rulesVersion < 1) {
    fail("Missing or invalid rules version");
  }
  getGameDescriptor(value.gameId, value.rulesVersion);
  if (!isPlainObject(value.payload)) fail("Game-specific payload must be an object");
  return Object.freeze({
    roomId: value.roomId,
    sessionId: value.sessionId,
    gameId: value.gameId,
    rulesVersion: value.rulesVersion,
    expectedRevision: value.expectedRevision,
    requestId: value.requestId,
    payload: Object.freeze({...value.payload})
  });
}

module.exports = {
  CATALOG_SCHEMA_VERSION,
  GAME_PHASES,
  GAME_MODES,
  TERMINAL_PHASES,
  GAME_DEFINITIONS,
  GameContractError,
  getGameDescriptor,
  isTerminalPhase,
  validateCatalog,
  validateActionEnvelope
};
