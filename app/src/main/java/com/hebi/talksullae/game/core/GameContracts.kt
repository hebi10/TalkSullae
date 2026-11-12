package com.hebi.talksullae.game.core

/**
 * GAME-00: read-only client transport contract.
 * Server-owned game rules, scoring, deadlines, and private roles are not represented here.
 */
enum class GamePhase(val wireValue: String) {
    WAITING("waiting"),
    READY("ready"),
    ASSIGNING("assigning"),
    PLAYING("playing"),
    VOTING("voting"),
    REVEAL("reveal"),
    FINISHED("finished"),
    CANCELLED("cancelled");

    companion object {
        fun fromWire(value: String): GamePhase? = entries.firstOrNull { it.wireValue == value }
    }

    val isTerminal: Boolean
        get() = this == FINISHED || this == CANCELLED
}

enum class GameMode(val wireValue: String) {
    REALTIME("realtime"),
    ASYNC("async");

    companion object {
        fun fromWire(value: String): GameMode? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Only safe, shareable session metadata. Do not add user->alias mappings,
 * hidden roles, vote secrets, scoring authority or device-local deadlines.
 */
data class GameSessionView(
    val roomId: String,
    val sessionId: String,
    val gameId: String,
    val rulesVersion: Int,
    val phase: GamePhase,
    val revision: Long,
    val round: Int,
    val deadlineEpochMillis: Long?
) {
    init {
        require(ROOM_ID.matches(roomId)) { "Invalid room ID" }
        require(SESSION_ID.matches(sessionId)) { "Invalid session ID" }
        require(GAME_ID.matches(gameId)) { "Invalid game ID" }
        require(rulesVersion >= 1) { "Missing rules version" }
        require(revision >= 0) { "Invalid server revision" }
        require(round >= 1) { "Invalid round" }
        require(deadlineEpochMillis == null || deadlineEpochMillis > 0) {
            "Invalid server deadline"
        }
    }

    companion object {
        private val ROOM_ID = Regex("^[A-Za-z0-9]{20}$")
        private val SESSION_ID = Regex("^[A-Za-z0-9_-]{8,100}$")
        private val GAME_ID = Regex("^[a-z][a-z0-9_-]{1,39}$")
    }
}
