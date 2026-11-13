package com.hebi.talksullae.game.online

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class IdentityChatMessage(
    val id: String,
    val alias: String,
    val text: String,
    val createdAtMillis: Long?
)

internal data class IdentityChatPage(
    val messages: List<IdentityChatMessage>,
    val cursor: DocumentSnapshot?,
    val hasMore: Boolean
)

internal data class LiarChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val createdAtMillis: Long?
)

internal data class LiarChatPage(
    val messages: List<LiarChatMessage>,
    val cursor: DocumentSnapshot?,
    val hasMore: Boolean
)

internal interface OnlineGameSessionRepository {
    fun watchSession(
        roomId: String,
        sessionId: String,
        onResult: (OnlineGameSnapshot?) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration

    fun watchIdentityMessages(
        roomId: String,
        sessionId: String,
        onPage: (IdentityChatPage) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration

    suspend fun loadOlderIdentityMessages(
        roomId: String, sessionId: String, cursor: DocumentSnapshot
    ): IdentityChatPage

    suspend fun sendIdentityMessage(
        roomId: String, sessionId: String, requestId: String, text: String
    ): String

    suspend fun findActiveSession(roomId: String): String?
    suspend fun findRecentCompletedSession(roomId: String, gameId: String): String? = null

    suspend fun createSession(
        roomId: String,
        gameId: String,
        mode: String,
        requestId: String
    ): String

    suspend fun submitAction(
        roomId: String,
        sessionId: String,
        gameId: String,
        rulesVersion: Int,
        revision: Long,
        requestId: String,
        action: Map<String, Any>
    ): Long

    suspend fun restoreMySession(
        roomId: String,
        sessionId: String
    ): Pair<OnlineGameSnapshot, Map<*, *>?>

    fun watchLiarMessages(
        roomId: String, sessionId: String,
        onPage: (LiarChatPage) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration = error("Liar chat repository is not configured")

    suspend fun loadOlderLiarMessages(
        roomId: String, sessionId: String, cursor: DocumentSnapshot
    ): LiarChatPage = error("Liar chat repository is not configured")

    suspend fun sendLiarMessage(
        roomId: String, sessionId: String, requestId: String, text: String
    ): String = error("Liar chat repository is not configured")

    fun watchForbiddenMessages(
        roomId: String, sessionId: String,
        onPage: (LiarChatPage) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration = watchLiarMessages(roomId,sessionId,onPage,onError)

    suspend fun loadOlderForbiddenMessages(
        roomId: String,sessionId: String,cursor: DocumentSnapshot
    ): LiarChatPage = loadOlderLiarMessages(roomId,sessionId,cursor)

    suspend fun sendForbiddenMessage(
        roomId: String,sessionId: String,requestId: String,text: String
    ): String = error("Forbidden chat repository is not configured")
}

/**
 * GAME-02 transport, not an active Game UI.
 * The only server-trusted identity is Firebase request.auth.uid.
 */
internal class FirestoreOnlineGameSessionRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) : OnlineGameSessionRepository {
    override fun watchSession(
        roomId: String,
        sessionId: String,
        onResult: (OnlineGameSnapshot?) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = db.collection("rooms").document(roomId)
        .collection("gameSessions").document(sessionId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError("게임 세션을 확인할 수 없습니다. 멤버 권한과 연결 상태를 확인해 주세요.")
            } else if (snapshot == null || !snapshot.exists()) {
                onResult(null)
            } else {
                val safe = snapshot.data?.let { raw ->
                    val fields = mutableMapOf<String, Any?>()
                    fields.putAll(raw)
                    fields["deadlineEpochMillis"] = snapshot.getTimestamp("phaseEndsAt")?.toDate()?.time
                    OnlineGameSessionDecoder.decode(
                        roomId, sessionId, fields, snapshot.metadata.isFromCache
                    )
                }
                if (safe == null) onError("게임 세션 버전을 해석할 수 없습니다. 앱 업데이트가 필요합니다.")
                else onResult(safe)
            }
        }

    private suspend fun invoke(functionName: String, body: Map<String, Any>): Map<*, *> {
        val result = functions.getHttpsCallable(functionName).call(body).await().data
        return result as? Map<*, *> ?: error("게임 서버 응답 형식이 올바르지 않습니다.")
    }

    private fun identityQuery(roomId: String, sessionId: String) =
        db.collection("rooms").document(roomId)
            .collection("gameSessions").document(sessionId)
            .collection("publicMessages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30L)

    private fun identityPage(docs: List<DocumentSnapshot>): IdentityChatPage =
        IdentityChatPage(
            messages = docs.mapNotNull { snapshot ->
                val alias = snapshot.getString("alias") ?: return@mapNotNull null
                val text = snapshot.getString("text") ?: return@mapNotNull null
                IdentityChatMessage(
                    id = snapshot.id, alias = alias, text = text,
                    createdAtMillis = snapshot.getTimestamp("createdAt")?.toDate()?.time
                )
            },
            cursor = docs.lastOrNull(),
            hasMore = docs.size == 30
        )

    override fun watchIdentityMessages(
        roomId: String,
        sessionId: String,
        onPage: (IdentityChatPage) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = identityQuery(roomId, sessionId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) onError("익명 채팅 기록을 불러올 수 없습니다.")
            else if (snapshot != null) onPage(identityPage(snapshot.documents))
        }

    override suspend fun loadOlderIdentityMessages(
        roomId: String,
        sessionId: String,
        cursor: DocumentSnapshot
    ): IdentityChatPage {
        val result = identityQuery(roomId, sessionId).startAfter(cursor).get().await()
        return identityPage(result.documents)
    }

    override suspend fun sendIdentityMessage(
        roomId: String, sessionId: String, requestId: String, text: String
    ): String {
        val result = invoke("sendIdentityMessage", mapOf(
            "roomId" to roomId, "sessionId" to sessionId,
            "requestId" to requestId, "text" to text
        ))
        return result["messageId"] as? String ?: error("익명 메시지 저장 결과가 없습니다.")
    }

    override suspend fun findActiveSession(roomId: String): String? {
        val result = invoke("findActiveGameSession", mapOf("roomId" to roomId))
        return result["sessionId"] as? String
    }

    override suspend fun findRecentCompletedSession(roomId: String, gameId: String): String? {
        val result = invoke("findRecentCompletedGameSession", mapOf(
            "roomId" to roomId,"gameId" to gameId
        ))
        return result["sessionId"] as? String
    }

    override suspend fun createSession(
        roomId: String,
        gameId: String,
        mode: String,
        requestId: String
    ): String {
        val result = invoke("createGameSession", mapOf(
            "roomId" to roomId, "gameId" to gameId,
            "mode" to mode, "requestId" to requestId
        ))
        return result["sessionId"] as? String ?: error("생성된 게임 세션을 확인할 수 없습니다.")
    }

    override suspend fun submitAction(
        roomId: String,
        sessionId: String,
        gameId: String,
        rulesVersion: Int,
        revision: Long,
        requestId: String,
        action: Map<String, Any>
    ): Long {
        val result = invoke("gameSessionAction", mapOf(
            "roomId" to roomId,
            "sessionId" to sessionId,
            "gameId" to gameId,
            "rulesVersion" to rulesVersion,
            "expectedRevision" to revision,
            "requestId" to requestId,
            "payload" to action
        ))
        return (result["revision"] as? Number)?.toLong()
            ?: error("변경된 게임 상태를 확인할 수 없습니다.")
    }

    override suspend fun restoreMySession(
        roomId: String,
        sessionId: String
    ): Pair<OnlineGameSnapshot, Map<*, *>?> {
        val result = invoke("getMyGameSession", mapOf(
            "roomId" to roomId, "sessionId" to sessionId
        ))
        val public = result["public"] as? Map<*, *>
            ?: error("게임 공개 상태가 없습니다.")
        val fields = public.entries.associate { (key, value) ->
            (key as? String ?: error("게임 상태 필드가 잘못되었습니다.")) to value
        }
        val snapshot = OnlineGameSessionDecoder.decode(roomId, sessionId, fields)
            ?: error("지원하지 않는 게임 데이터 버전입니다.")
        return snapshot to (result["personal"] as? Map<*, *>)
    }
    private fun liarPage(docs: List<DocumentSnapshot>): LiarChatPage =
        LiarChatPage(
            messages = docs.mapNotNull { snapshot ->
                val senderUid = snapshot.getString("senderUid") ?: return@mapNotNull null
                val text = snapshot.getString("text") ?: return@mapNotNull null
                LiarChatMessage(
                    id = snapshot.id, senderUid = senderUid, text = text,
                    createdAtMillis = snapshot.getTimestamp("createdAt")?.toDate()?.time
                )
            },
            cursor = docs.lastOrNull(),
            hasMore = docs.size == 30
        )

    override fun watchLiarMessages(
        roomId: String, sessionId: String,
        onPage: (LiarChatPage) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration = identityQuery(roomId, sessionId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) onError("라이어 게임 채팅 기록을 불러올 수 없습니다.")
            else if (snapshot != null) onPage(liarPage(snapshot.documents))
        }

    override suspend fun loadOlderLiarMessages(
        roomId: String, sessionId: String, cursor: DocumentSnapshot
    ): LiarChatPage {
        val result = identityQuery(roomId, sessionId).startAfter(cursor).get().await()
        return liarPage(result.documents)
    }

    override suspend fun sendLiarMessage(
        roomId: String, sessionId: String, requestId: String, text: String
    ): String {
        val result = invoke("sendLiarMessage", mapOf(
            "roomId" to roomId, "sessionId" to sessionId,
            "requestId" to requestId, "text" to text
        ))
        return result["messageId"] as? String ?: error("라이어 메시지 저장 결과가 없습니다.")
    }

    override fun watchForbiddenMessages(
        roomId: String,sessionId: String,
        onPage: (LiarChatPage) -> Unit,onError: (String) -> Unit
    ): ListenerRegistration = watchLiarMessages(roomId,sessionId,onPage,onError)

    override suspend fun loadOlderForbiddenMessages(
        roomId: String,sessionId: String,cursor: DocumentSnapshot
    ): LiarChatPage = loadOlderLiarMessages(roomId,sessionId,cursor)

    override suspend fun sendForbiddenMessage(
        roomId: String,sessionId: String,requestId: String,text: String
    ): String {
        val result=invoke("sendForbiddenMessage",mapOf(
            "roomId" to roomId,"sessionId" to sessionId,
            "requestId" to requestId,"text" to text
        ))
        return result["messageId"] as? String
            ?: error("금지어 메시지 저장 결과가 없습니다.")
    }

}
