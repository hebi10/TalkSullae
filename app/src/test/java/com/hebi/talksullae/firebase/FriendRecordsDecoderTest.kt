package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FriendRecordsDecoderTest {
    private val ids=listOf("identity","liar","forbidden","balance")
    private fun response(hits:Long=1L):Map<String,Any> = mapOf(
        "friend" to mapOf("totalGames" to 2L,"totalWins" to 1L,"bestStreak" to 1L,
            "totalSeasonPoints" to 100L,"achievements" to listOf("first_game"),
            "games" to ids.associateWith { mapOf("plays" to 1L,"wins" to 0L) }),
        "comparison" to mapOf("sharedGames" to 1L,"myHigher" to 0L,"friendHigher" to 0L,"ties" to 1L,
            "games" to ids.associateWith { if(it=="identity")1L else 0L },
            "myIdentityAttempts" to 1L,"myIdentityHits" to hits,"friendIdentityAttempts" to 0L,
            "friendIdentityHits" to 0L,"balanceCompared" to 0L,"balanceMatched" to 0L),
        "reviewedGames" to 2L,"capped" to false,"recentLimit" to 30L)
    @Test fun decodeValidBoundedRecords(){
        val data=FriendRecordsDecoder.decode(response())
        assertEquals(1L,data.comparison.sharedGames)
        assertEquals(1L,data.comparison.myIdentityHits)
        assertEquals(2L,data.reviewedGames)
    }
    @Test fun rejectsImpossibleCounts(){
        assertThrows(IllegalArgumentException::class.java){ FriendRecordsDecoder.decode(response(2L)) }
        assertThrows(IllegalArgumentException::class.java){ FriendRecordsDecoder.decode(response()+("reviewedGames" to 31L)) }
    }
}
