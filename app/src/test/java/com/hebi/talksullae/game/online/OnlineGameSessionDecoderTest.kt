package com.hebi.talksullae.game.online

import com.hebi.talksullae.game.core.GameMode
import com.hebi.talksullae.game.core.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineGameSessionDecoderTest {
    private val roomId = "12345678901234567890"
    private val sessionId = "a".repeat(64)

    private fun valid(): Map<String, Any?> = mapOf(
        "schemaVersion" to 1L,
        "roomId" to roomId,
        "sessionId" to sessionId,
        "gameId" to "balance",
        "rulesVersion" to 1L,
        "mode" to "realtime",
        "phase" to "ready",
        "revision" to 4L,
        "round" to 1L,
        "deadlineEpochMillis" to null,
        "participants" to mapOf(
            "owner" to mapOf("ready" to true),
            "member" to mapOf("ready" to true)
        ),
        "publicState" to emptyMap<String, Any>()
    )

    @Test
    fun readsServerOwnedStateWithoutInterpretingPrivateRoles() {
        val decoded = OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + ("privateAssignments" to mapOf("owner" to mapOf("secret" to "test")))
        )
        requireNotNull(decoded)
        assertEquals(GamePhase.READY, decoded.session.phase)
        assertEquals(GameMode.REALTIME, decoded.mode)
        assertEquals(4L, decoded.session.revision)
        assertEquals(2, decoded.participants.size)
        assertTrue(decoded.participants.all { it.ready })
        assertFalse(decoded.publicState.containsKey("privateAssignments"))
    }

    @Test
    fun acceptsDeadlineAndOfflineCachedMetadata() {
        val decoded = OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + mapOf(
                "phase" to "playing",
                "deadlineEpochMillis" to 1762935600000L
            ),
            fromCache = true
        )
        requireNotNull(decoded)
        assertEquals(GamePhase.PLAYING, decoded.session.phase)
        assertEquals(1762935600000L, decoded.session.deadlineEpochMillis)
        assertTrue(decoded.fromCache)
    }

    @Test
    fun acceptsWholeNumberDoublesFromCallableButRejectsFractionalRevisions() {
        val fromCallable = OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + mapOf(
                "schemaVersion" to 1.0,
                "rulesVersion" to 1.0,
                "revision" to 4.0,
                "round" to 1.0,
                "deadlineEpochMillis" to 1762935600000.0
            )
        )
        requireNotNull(fromCallable)
        assertEquals(4L, fromCallable.session.revision)
        assertNull(OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + ("revision" to 1.5)
        ))
    }

    @Test
    fun unknownProtocolOrPhaseIsNotDowngradedToPlaying() {
        assertNull(OnlineGameSessionDecoder.decode(roomId, sessionId, valid() + ("schemaVersion" to 2L)))
        assertNull(OnlineGameSessionDecoder.decode(roomId, sessionId, valid() + ("phase" to "admin")))
        assertNull(OnlineGameSessionDecoder.decode(roomId, sessionId, valid() + ("mode" to "unknown")))
        assertNull(OnlineGameSessionDecoder.decode(roomId, sessionId, valid() + ("rulesVersion" to 0L)))
        assertNull(OnlineGameSessionDecoder.decode(roomId, sessionId, valid() + ("rulesVersion" to 2L)))
        assertNull(OnlineGameSessionDecoder.decode(roomId, sessionId, valid() + ("gameId" to "unknown")))
        assertNull(OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + mapOf("gameId" to "identity", "mode" to "async")
        ))
        assertNull(OnlineGameSessionDecoder.decode(roomId, sessionId, valid() + ("round" to 0L)))
    }

    @Test
    fun rejectsForgedPublicRolesAndMalformedParticipants() {
        assertNull(OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + ("publicState" to mapOf("secretRole" to "liar"))
        ))
        assertNull(OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + ("publicState" to mapOf("publishedResult" to "너가 라이어"))
        ))
        assertNull(OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + ("participants" to mapOf("owner" to mapOf("ready" to "true")))
        ))
        assertNull(OnlineGameSessionDecoder.decode(
            roomId, sessionId,
            valid() + ("roomId" to "different-room")
        ))
    }
}
