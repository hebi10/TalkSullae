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

    @Test fun retainedCareerMergesDistinctPagesWithoutDroppingHistoricalWins() {
        val games=listOf("identity","liar","forbidden","balance").associateWith { CareerGame(2,1) }
        val first=MyCareer(1,8,4,300,2,listOf("first_game"),games,3,true,"cursor-1")
        val second=MyCareer(0,8,4,400,3,listOf("first_game","wins_5"),games,2,true,null)
        val all=first.append(second)
        assertEquals(1L,all.roomCount)
        assertEquals(5L,all.recordedRoomCount)
        assertEquals(16L,all.totalGames)
        assertEquals(8L,all.totalWins)
        assertEquals(3L,all.bestStreak)
        assertEquals(listOf("first_game","wins_5"),all.achievements)
        assertEquals(4L,all.games["balance"]?.plays)
        assertEquals(null,all.nextCursor)
        assertThrows(IllegalArgumentException::class.java){first.append(second.copy(nextCursor="cursor-1"))}
    }
}
