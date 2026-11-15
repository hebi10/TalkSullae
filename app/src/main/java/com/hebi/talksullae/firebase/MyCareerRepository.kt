package com.hebi.talksullae.firebase

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class CareerGame(val plays: Long, val wins: Long)

internal data class MyCareer(
    val roomCount: Long,
    val totalGames: Long,
    val totalWins: Long,
    val totalSeasonPoints: Long,
    val bestStreak: Long,
    val achievements: List<String>,
    val games: Map<String, CareerGame>
)

internal object CareerDecoder {
    private fun number(value: Any?, label: String): Long {
        val n = (value as? Number)?.toLong() ?: error("$label 정보가 없습니다.")
        require(n >= 0) { "$label 값이 올바르지 않습니다." }
        return n
    }

    fun decode(data: Map<*, *>): MyCareer {
        val games = data["games"] as? Map<*, *> ?: error("게임별 기록이 없습니다.")
        val byGame = listOf("identity", "liar", "forbidden", "balance").associateWith { gameId ->
            val stats = games[gameId] as? Map<*, *> ?: error("게임 전적이 없습니다.")
            CareerGame(
                plays = number(stats["plays"], "경기"),
                wins = number(stats["wins"], "승리")
            )
        }
        val achievements = (data["achievements"] as? List<*>)
            ?.map { it as? String ?: error("업적 데이터가 올바르지 않습니다.") }
            ?: error("업적이 없습니다.")
        return MyCareer(
            roomCount = number(data["roomCount"], "참여 방"),
            totalGames = number(data["totalGames"], "누적 경기"),
            totalWins = number(data["totalWins"], "누적 승리"),
            totalSeasonPoints = number(data["totalSeasonPoints"], "누적 시즌점"),
            bestStreak = number(data["bestStreak"], "최고 연승"),
            achievements = achievements,
            games = byGame
        )
    }
}

internal interface CareerRepository {
    suspend fun getMyCareer(): MyCareer
}

internal class FirebaseCareerRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) : CareerRepository {
    override suspend fun getMyCareer(): MyCareer {
        val data = functions.getHttpsCallable("getMyCareer")
            .call(emptyMap<String, Any>()).await().data as? Map<*, *>
            ?: error("나의 기록을 확인할 수 없습니다.")
        return CareerDecoder.decode(data)
    }
}
