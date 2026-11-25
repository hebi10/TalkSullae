package com.hebi.talksullae.firebase

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class FriendGameStats(val plays: Long, val wins: Long)
internal data class FriendComparison(
    val sharedGames: Long, val myHigher: Long, val friendHigher: Long, val ties: Long,
    val games: Map<String, Long>,
    val myIdentityAttempts: Long, val myIdentityHits: Long,
    val friendIdentityAttempts: Long, val friendIdentityHits: Long,
    val balanceCompared: Long, val balanceMatched: Long
)
internal data class FriendRecords(
    val totalGames: Long, val totalWins: Long, val bestStreak: Long, val totalSeasonPoints: Long,
    val games: Map<String, FriendGameStats>, val achievements: List<String>,
    val comparison: FriendComparison, val reviewedGames: Long, val capped: Boolean,
    val comparisonLoaded: Boolean = true,
    val identitySuppressed: Boolean = false,
    val balanceSuppressed: Boolean = false
)

internal object FriendRecordsDecoder {
    private val ids = listOf("identity", "liar", "forbidden", "balance")
    private fun map(value: Any?): Map<*, *> = value as? Map<*, *> ?: error("전적 형식 오류")
    private fun count(value: Any?): Long {
        val n = value as? Number ?: error("전적 수치 누락")
        val d = n.toDouble()
        require(d.isFinite() && d >= 0 && d <= 9_007_199_254_740_991.0 && d % 1.0 == 0.0)
        return n.toLong()
    }

    fun decode(data: Map<*, *>): FriendRecords {
        val friend = map(data["friend"])
        val pair = map(data["comparison"])
        val sourceGames = map(friend["games"])
        val games = ids.associateWith { id ->
            val item = map(sourceGames[id])
            FriendGameStats(count(item["plays"]), count(item["wins"])).also { require(it.wins <= it.plays) }
        }
        val pairGames = map(pair["games"])
        val comparison = FriendComparison(count(pair["sharedGames"]), count(pair["myHigher"]),
            count(pair["friendHigher"]), count(pair["ties"]), ids.associateWith { count(pairGames[it]) },
            count(pair["myIdentityAttempts"]), count(pair["myIdentityHits"]),
            count(pair["friendIdentityAttempts"]), count(pair["friendIdentityHits"]),
            count(pair["balanceCompared"]), count(pair["balanceMatched"]))
        val reviewed = count(data["reviewedGames"])
        require(count(data["recentLimit"]) == 30L && reviewed <= 30 && comparison.sharedGames <= reviewed)
        require(comparison.myHigher + comparison.friendHigher + comparison.ties == comparison.sharedGames)
        require(comparison.games.values.sum() == comparison.sharedGames)
        require(comparison.myIdentityHits <= comparison.myIdentityAttempts &&
            comparison.friendIdentityHits <= comparison.friendIdentityAttempts &&
            comparison.myIdentityAttempts <= comparison.games.getValue("identity") &&
            comparison.friendIdentityAttempts <= comparison.games.getValue("identity") &&
            comparison.balanceMatched <= comparison.balanceCompared &&
            comparison.balanceCompared <= comparison.games.getValue("balance"))
        val totalGames = count(friend["totalGames"])
        val wins = count(friend["totalWins"])
        require(wins <= totalGames)
        val achievements = (friend["achievements"] as? List<*>)?.map {
            it as? String ?: error("업적 형식 오류")
        } ?: error("업적 누락")
        return FriendRecords(totalGames, wins, count(friend["bestStreak"]), count(friend["totalSeasonPoints"]),
            games, achievements, comparison, reviewed, data["capped"] as? Boolean ?: error("조회 범위 누락"),
            data["comparisonLoaded"] as? Boolean ?: true,
            data["identitySuppressed"] as? Boolean ?: false,
            data["balanceSuppressed"] as? Boolean ?: false)
    }
}

internal interface FriendRecordsRepository {
    suspend fun get(roomId: String, targetUid: String, includeComparison: Boolean = true): FriendRecords
}

internal class FirebaseFriendRecordsRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) : FriendRecordsRepository {
    override suspend fun get(roomId: String, targetUid: String, includeComparison: Boolean): FriendRecords {
        val result = functions.getHttpsCallable("getRoomFriendRecords")
            .call(mapOf("roomId" to roomId, "targetUid" to targetUid, "includeComparison" to includeComparison)).await().data as? Map<*, *>
            ?: error("전적 정보를 확인하지 못했습니다.")
        return FriendRecordsDecoder.decode(result)
    }
}
