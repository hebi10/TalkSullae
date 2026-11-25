package com.hebi.talksullae.firebase

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class CareerGame(val plays: Long, val wins: Long)

internal data class CareerProgress(val xp: Long, val level: Long, val title: String)

internal fun careerProgress(totalGames: Long): CareerProgress {
    val games = totalGames.coerceIn(0L, Long.MAX_VALUE / 10L)
    val xp = games * 10L
    val level = xp / 100L + 1L
    val title = when {
        games >= 100L -> "추리 달인"
        games >= 50L -> "숙련 탐정"
        games >= 10L -> "초급 탐정"
        else -> "신입 탐정"
    }
    return CareerProgress(xp, level, title)
}

internal data class MyCareer(
    val roomCount: Long,
    val totalGames: Long,
    val totalWins: Long,
    val totalSeasonPoints: Long,
    val bestStreak: Long,
    val achievements: List<String>,
    val games: Map<String, CareerGame>,
    val recordedRoomCount: Long = roomCount,
    val retainedScope: Boolean = false,
    val nextCursor: String? = null
) {
    fun append(next: MyCareer): MyCareer {
        require(retainedScope && next.retainedScope && nextCursor != null && next.nextCursor != nextCursor)
        return MyCareer(Math.addExact(roomCount,next.roomCount),Math.addExact(totalGames,next.totalGames),
            Math.addExact(totalWins,next.totalWins),Math.addExact(totalSeasonPoints,next.totalSeasonPoints),
            maxOf(bestStreak,next.bestStreak),(achievements+next.achievements).distinct().sorted(),
            games.mapValues{(id,game)->
                val incoming=next.games.getValue(id)
                CareerGame(Math.addExact(game.plays,incoming.plays),Math.addExact(game.wins,incoming.wins))
            },Math.addExact(recordedRoomCount,next.recordedRoomCount),true,next.nextCursor)
    }
}

internal object CareerDecoder {
    private fun number(value: Any?, label: String): Long {
        val n = (value as? Number)?.toDouble() ?: error("$label 정보가 없습니다.")
        require(n.isFinite() && n >= 0 && n <= 9_007_199_254_740_991.0 && n % 1.0 == 0.0) {
            "$label 값이 올바르지 않습니다."
        }
        return n.toLong()
    }

    fun decode(data: Map<*, *>): MyCareer {
        val games = data["games"] as? Map<*, *> ?: error("게임별 기록이 없습니다.")
        val byGame = listOf("identity", "liar", "forbidden", "balance").associateWith { gameId ->
            val stats = games[gameId] as? Map<*, *> ?: error("게임 전적이 없습니다.")
            CareerGame(
                plays = number(stats["plays"], "경기"),
                wins = number(stats["wins"], "승리")
            ).also { require(it.wins <= it.plays) }
        }
        val achievements = (data["achievements"] as? List<*>)
            ?.map { it as? String ?: error("업적 데이터가 올바르지 않습니다.") }
            ?: error("업적이 없습니다.")
        val rooms = number(data["roomCount"], "참여 방")
        val retained = data["scope"] == "retained"
        val recorded = if(retained) number(data["recordedRoomCount"],"기록 방") else rooms
        require(recorded >= rooms)
        val cursor = data["nextCursor"]
        require(cursor == null || (retained && cursor is String && cursor.isNotBlank() && cursor.length <= 128 && '/' !in cursor))
        return MyCareer(
            roomCount = rooms,
            totalGames = number(data["totalGames"], "누적 경기"),
            totalWins = number(data["totalWins"], "누적 승리"),
            totalSeasonPoints = number(data["totalSeasonPoints"], "누적 시즌점"),
            bestStreak = number(data["bestStreak"], "최고 연승"),
            achievements = achievements,
            games = byGame,
            recordedRoomCount = recorded,
            retainedScope = retained,
            nextCursor = cursor as? String
        ).also { require(it.totalWins <= it.totalGames) }
    }
}

internal interface CareerRepository {
    suspend fun getMyCareer(): MyCareer
    suspend fun getMyCareerPage(cursor: String?): MyCareer =
        if(cursor == null) getMyCareer() else error("추가 기록 조회를 지원하지 않습니다.")
}

internal class FirebaseCareerRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) : CareerRepository {
    override suspend fun getMyCareer(): MyCareer = getMyCareerPage(null)

    override suspend fun getMyCareerPage(cursor: String?): MyCareer {
        val args = mutableMapOf<String,Any>("scope" to "retained")
        cursor?.let { args["cursor"] = it }
        val data = functions.getHttpsCallable("getMyCareer")
            .call(args).await().data as? Map<*, *>
            ?: error("나의 기록을 확인할 수 없습니다.")
        return CareerDecoder.decode(data)
    }
}
