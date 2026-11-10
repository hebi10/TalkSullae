package com.hebi.talksullae.data

/**
 * Local, disposable preview content. There is no network room or saved game result in 2-A.
 */
data class RoomPreview(
    val id: String,
    val name: String,
    val memberCount: Int,
    val recentMessage: String,
    val unreadCount: Int
)

data class GamePreview(
    val id: String,
    val name: String,
    val description: String,
    val minimumPlayers: Int,
    val maximumPlayers: Int,
    val duration: String
)

data class MessagePreview(
    val sender: String,
    val text: String,
    val isOwn: Boolean = false
)

data class RankingPreview(
    val place: Int,
    val name: String,
    val points: Int
)

object DemoCatalog {
    val rooms = listOf(
        RoomPreview("friday", "금요일 게임방", 6, "오늘도 게임 한 판 할까?", 2),
        RoomPreview("friends", "동네 친구들", 5, "이번 주에 다들 시간 돼?", 0)
    )

    val games = listOf(
        GamePreview("identity", "너 누구야?", "익명으로 대화하고 친구의 정체를 추리해요.", 4, 10, "약 5~10분"),
        GamePreview("liar", "라이어 채팅", "주제를 모르는 라이어를 찾아보세요.", 4, 10, "약 10~20분"),
        GamePreview("forbidden", "금지어 채팅", "상대가 자신의 금지어를 말하도록 유도해요.", 3, 10, "약 5~10분"),
        GamePreview("balance", "밸런스 예측", "친구의 A/B 선택을 예측해 보세요.", 2, 10, "약 5~15분")
    )

    val roomMessages = listOf(
        MessagePreview("민지", "다들 왔네! 오늘 뭐 할까?"),
        MessagePreview("지훈", "오랜만에 너 누구야? 하자."),
        MessagePreview("수빈", "이번에는 정체 절대 안 들킬 거야."),
        MessagePreview("나", "일단 준비 완료!", true)
    )

    val anonymousMessages = listOf(
        MessagePreview("고양이", "무조건 난 살아남을 자신 있음."),
        MessagePreview("토끼", "너 요리도 못 하잖아."),
        MessagePreview("나 · 부엉이", "일단 나무부터 구해야 하는 거 아냐?", true),
        MessagePreview("강아지", "난 물부터 확보할 거야."),
        MessagePreview("여우", "도구부터 만들어야지.")
    )

    val ranking = listOf(
        RankingPreview(1, "민지", 1560),
        RankingPreview(2, "나", 1240),
        RankingPreview(3, "수빈", 1010),
        RankingPreview(4, "도윤", 850),
        RankingPreview(5, "지훈", 610)
    )
}
