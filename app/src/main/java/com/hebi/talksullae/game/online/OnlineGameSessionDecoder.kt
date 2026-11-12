package com.hebi.talksullae.game.online

import com.hebi.talksullae.game.core.GameMode
import com.hebi.talksullae.game.core.GamePhase
import com.hebi.talksullae.game.core.GameSessionView

data class GameLobbyPlayer(
    val uid: String,
    val ready: Boolean
)

data class OnlineGameSnapshot(
    val session: GameSessionView,
    val mode: GameMode,
    val participants: List<GameLobbyPlayer>,
    val publicState: Map<String, Any?>,
    val fromCache: Boolean
)

/**
 * Decode only safe public session fields. Unknown server schema/phase/participants
 * cause an update-required error rather than silently switching to PLAYING.
 */
object OnlineGameSessionDecoder {
    private fun number(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> if (value.isFinite() && value % 1.0 == 0.0 &&
            value >= -9_007_199_254_740_991.0 && value <= 9_007_199_254_740_991.0
        ) value.toLong() else null
        else -> null
    }

    fun decode(
        roomId: String,
        sessionId: String,
        data: Map<String, Any?>,
        fromCache: Boolean = false
    ): OnlineGameSnapshot? {
        if (number(data["schemaVersion"]) != 1L ||
            data["roomId"] != roomId || data["sessionId"] != sessionId) return null

        val gameId = data["gameId"] as? String ?: return null
        val mode = GameMode.fromWire(data["mode"] as? String ?: return null) ?: return null
        val phase = GamePhase.fromWire(data["phase"] as? String ?: return null) ?: return null
        val version = number(data["rulesVersion"]) ?: return null
        val supportedGameIds = setOf("identity", "liar", "forbidden", "balance")
        if (gameId !in supportedGameIds || version != 1L ||
            (mode == GameMode.ASYNC && gameId != "balance")) return null
        val revision = number(data["revision"]) ?: return null
        val round = number(data["round"]) ?: return null

        if (version !in 1..Int.MAX_VALUE.toLong() ||
            round !in 1..Int.MAX_VALUE.toLong()) return null

        val rawPlayers = data["participants"] as? Map<*, *> ?: return null
        if (rawPlayers.size > 20 || rawPlayers.isEmpty()) return null
        val players = rawPlayers.entries.map { (key, value) ->
            val uid = key as? String ?: return null
            val payload = value as? Map<*, *> ?: return null
            val ready = payload["ready"] as? Boolean ?: return null
            if (uid.isBlank()) return null
            GameLobbyPlayer(uid = uid, ready = ready)
        }.sortedBy { it.uid }

        val rawPublic = data["publicState"] as? Map<*, *> ?: return null
        val allowed = setOf("prompt", "notice", "options", "publishedResult")
        if (rawPublic.keys.any { it !is String || it !in allowed }) return null
        if (phase != GamePhase.REVEAL && phase != GamePhase.FINISHED &&
            rawPublic.containsKey("publishedResult")) return null
        val publicState = rawPublic.entries.associate { (key, value) ->
            key as String to value
        }

        val deadline = number(data["deadlineEpochMillis"])
        val view = runCatching {
            GameSessionView(
                roomId = roomId,
                sessionId = sessionId,
                gameId = gameId,
                rulesVersion = version.toInt(),
                phase = phase,
                revision = revision,
                round = round.toInt(),
                deadlineEpochMillis = deadline
            )
        }.getOrNull() ?: return null

        return OnlineGameSnapshot(
            session = view, mode = mode, participants = players,
            publicState = publicState, fromCache = fromCache
        )
    }
}
