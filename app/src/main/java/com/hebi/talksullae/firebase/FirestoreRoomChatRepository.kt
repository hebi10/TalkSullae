package com.hebi.talksullae.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal const val CHAT_PAGE_SIZE = 30

internal data class RoomChatMessage(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val text: String,
    val createdAtMillis: Long?
)

internal data class RoomChatPage(
    val messages: List<RoomChatMessage>,
    val oldestSnapshot: DocumentSnapshot?,
    val hasMore: Boolean,
    val fromCache: Boolean
)

internal object RoomChatInputPolicy {
    const val MAX_LENGTH = 1000

    fun normalize(value: String): String =
        value.replace("\r\n", "\n").replace("\r", "\n").trim()

    fun error(value: String): String? {
        val text = normalize(value)
        return when {
            text.isEmpty() -> "메시지를 입력해 주세요."
            text.length > MAX_LENGTH -> "메시지는 1000자 이하로 입력해 주세요."
            text.any {
                it.code in 0..8 || it.code == 11 || it.code == 12 ||
                    it.code in 14..31 || it.code == 127
            } -> "제어 문자가 포함되어 메시지를 전송할 수 없습니다."
            else -> null
        }
    }
}

internal interface RoomChatRepository {
    fun watchRecent(
        roomId: String,
        onPage: (RoomChatPage) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration

    suspend fun loadOlder(roomId: String, cursor: DocumentSnapshot): RoomChatPage
    suspend fun sendMessage(roomId: String, requestId: String, text: String): String
}

internal class FirestoreRoomChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) : RoomChatRepository {
    private fun messages(roomId: String) =
        db.collection("rooms").document(roomId).collection("messages")

    private fun recentQuery(roomId: String) =
        messages(roomId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(CHAT_PAGE_SIZE.toLong())

    private fun parse(snapshot: DocumentSnapshot): RoomChatMessage? {
        val uid = snapshot.getString("senderUid") ?: return null
        val text = snapshot.getString("text") ?: return null
        return RoomChatMessage(
            id = snapshot.id,
            senderUid = uid,
            senderName = snapshot.getString("senderName").orEmpty().ifBlank { "멤버" },
            text = text,
            createdAtMillis = snapshot.getTimestamp("createdAt")?.toDate()?.time
        )
    }

    override fun watchRecent(
        roomId: String,
        onPage: (RoomChatPage) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = recentQuery(roomId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError("채팅 내용을 불러올 수 없습니다. 방 접근 권한과 연결 상태를 확인해 주세요.")
        } else if (snapshot != null) {
            onPage(
                RoomChatPage(
                    messages = snapshot.documents.mapNotNull(::parse),
                    oldestSnapshot = snapshot.documents.lastOrNull(),
                    hasMore = snapshot.size() == CHAT_PAGE_SIZE,
                    fromCache = snapshot.metadata.isFromCache
                )
            )
        }
    }

    override suspend fun loadOlder(roomId: String, cursor: DocumentSnapshot): RoomChatPage {
        val snapshot = recentQuery(roomId).startAfter(cursor).get().await()
        return RoomChatPage(
            messages = snapshot.documents.mapNotNull(::parse),
            oldestSnapshot = snapshot.documents.lastOrNull(),
            hasMore = snapshot.size() == CHAT_PAGE_SIZE,
            fromCache = snapshot.metadata.isFromCache
        )
    }

    override suspend fun sendMessage(roomId: String, requestId: String, text: String): String {
        val result = functions.getHttpsCallable("sendRoomMessage")
            .call(mapOf("roomId" to roomId, "requestId" to requestId, "text" to text))
            .await().data as? Map<*, *> ?: error("메시지 응답 형식이 올바르지 않습니다.")
        return result["messageId"] as? String ?: error("메시지 저장 결과가 없습니다.")
    }
}
