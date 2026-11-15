package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CareerDecoderTest {
    @Test fun mapsFourServerVerifiedGameCounters() {
        val games = listOf("identity","liar","forbidden","balance")
            .associateWith { mapOf("plays" to 2L,"wins" to 1L) }
        val career = CareerDecoder.decode(mapOf(
            "roomCount" to 2L, "totalGames" to 8L,
            "totalWins" to 4L, "totalSeasonPoints" to 300L,
            "bestStreak" to 2L, "achievements" to listOf("first_game"),
            "games" to games
        ))
        assertEquals(2L, career.roomCount)
        assertEquals(8L, career.totalGames)
        assertEquals(1L, career.games["balance"]?.wins)
    }

    @Test fun refusesNegativeCounters() {
        val games = listOf("identity","liar","forbidden","balance")
            .associateWith { mapOf("plays" to 0L,"wins" to 0L) }
        assertThrows(IllegalArgumentException::class.java) {
            CareerDecoder.decode(mapOf(
                "roomCount" to -1L, "totalGames" to 0L,
                "totalWins" to 0L, "totalSeasonPoints" to 0L,
                "bestStreak" to 0L, "achievements" to emptyList<String>(),
                "games" to games
            ))
        }
    }
}
