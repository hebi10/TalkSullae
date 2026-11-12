package com.hebi.talksullae.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameContractsTest {
    private fun validView() = GameSessionView(
        roomId = "12345678901234567890",
        sessionId = "session-0123456789",
        gameId = "identity",
        rulesVersion = 1,
        phase = GamePhase.WAITING,
        revision = 0,
        round = 1,
        deadlineEpochMillis = null
    )

    @Test
    fun wireValuesUseStableLowercaseSchema() {
        assertEquals("waiting", GamePhase.WAITING.wireValue)
        assertEquals("reveal", GamePhase.REVEAL.wireValue)
        assertEquals("realtime", GameMode.REALTIME.wireValue)
        assertEquals("async", GameMode.ASYNC.wireValue)
        assertEquals(GamePhase.VOTING, GamePhase.fromWire("voting"))
        assertEquals(GameMode.ASYNC, GameMode.fromWire("async"))
    }

    @Test
    fun unknownFutureStatesMustNotBeSilentlyRenderedAsPlaying() {
        assertNull(GamePhase.fromWire("unknown_future_phase"))
        assertNull(GameMode.fromWire("unknown_mode"))
        assertFalse(GamePhase.PLAYING.isTerminal)
        assertTrue(GamePhase.CANCELLED.isTerminal)
        assertTrue(GamePhase.FINISHED.isTerminal)
    }

    @Test
    fun sessionSnapshotHasStableServerRevisionAndSafeMetadata() {
        assertEquals(0L, validView().revision)
        assertEquals(1, validView().rulesVersion)
        assertEquals(GamePhase.WAITING, validView().phase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingRuleVersion() {
        validView().copy(rulesVersion = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRoomId() {
        validView().copy(roomId = "../other-room")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeRevision() {
        validView().copy(revision = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidServerDeadline() {
        validView().copy(deadlineEpochMillis = 0)
    }
}
