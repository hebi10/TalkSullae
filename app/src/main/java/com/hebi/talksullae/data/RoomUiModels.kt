package com.hebi.talksullae.data

data class RoomDraft(
    val name: String,
    val description: String,
    val requiresApproval: Boolean
)

object RoomInputRules {
    const val NAME_LIMIT = 24
    const val DESCRIPTION_LIMIT = 120

    fun nameError(raw: String): String? {
        val name = raw.trim()
        return when {
            name.length < 2 -> "방 이름은 2글자 이상 입력해 주세요."
            name.length > NAME_LIMIT -> "방 이름은 24글자 이하로 입력해 주세요."
            name.any { it == '\n' || it == '\r' } -> "방 이름에는 줄바꿈을 사용할 수 없습니다."
            else -> null
        }
    }

    fun descriptionError(value: String): String? =
        if (value.length > DESCRIPTION_LIMIT) "설명은 120글자 이하로 입력해 주세요." else null

    fun valid(name: String, description: String): Boolean =
        nameError(name) == null && descriptionError(description) == null
}

data class MemberPreview(val id: String, val name: String)

object DemoRoomMembers {
    const val MY_ID = "me"

    fun initialMembers(roomId: String): List<MemberPreview> = when (roomId) {
        "friday" -> listOf(
            MemberPreview("me", "나"),
            MemberPreview("minji", "민지"),
            MemberPreview("jihun", "지훈"),
            MemberPreview("subin", "수빈"),
            MemberPreview("doyun", "도윤"),
            MemberPreview("hyunwoo", "현우")
        )
        "friends" -> listOf(
            MemberPreview("me", "나"),
            MemberPreview("minji", "민지"),
            MemberPreview("jihun", "지훈"),
            MemberPreview("subin", "수빈"),
            MemberPreview("doyun", "도윤")
        )
        else -> listOf(MemberPreview(MY_ID, "나"))
    }

    fun initialOwner(roomId: String): String =
        if (roomId == "friends") "minji" else MY_ID
}

enum class RoomPreviewMode { CONTENT, EMPTY, LOADING, OFFLINE }

enum class JoinPreviewState {
    INVITED, PENDING, APPROVED, DECLINED, REMOVED, EXPIRED
}

fun joinStateMessage(state: JoinPreviewState): Pair<String, String> = when (state) {
    JoinPreviewState.INVITED -> "방 초대가 도착했어요" to "방 이름과 참가 인원을 확인한 후 가입을 요청할 수 있습니다."
    JoinPreviewState.PENDING -> "가입 승인을 기다리는 중" to "방장이 요청을 확인하면 상태가 변경됩니다."
    JoinPreviewState.APPROVED -> "가입이 승인됐어요" to "실제 앱에서는 승인 후 방으로 이동합니다."
    JoinPreviewState.DECLINED -> "가입 요청이 거절됐어요" to "방장에게 문의하거나 다른 방을 이용해 주세요."
    JoinPreviewState.REMOVED -> "이 방에서 내보내졌어요" to "이전 초대 링크로 다시 가입할 수 없습니다."
    JoinPreviewState.EXPIRED -> "초대 링크가 만료됐어요" to "방장에게 새로운 초대 링크를 요청해 주세요."
}
