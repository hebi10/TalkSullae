package com.hebi.talksullae.game.online

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal interface OnlineGameSessionRepository {
    fun watchSession(
        roomId: String,
        sessionId: String,
        onResult: (OnlineGameSnapshot?) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration

    suspend fun findActiveSession(roomId: String): String?

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
                val safe = snapshot.data?.toMutableMap()?.apply {
                    put("deadlineEpochMillis", snapshot.getTimestamp("phaseEndsAt")?.toDate()?.time)
                }?.let {
                    OnlineGameSessionDecoder.decode(roomId, sessionId, it, snapshot.metadata.isFromCache)
                }
                if (safe == null) onError("게임 세션 버전을 해석할 수 없습니다. 앱 업데이트가 필요합니다.")
                else onResult(safe)
            }
        }

    private suspend fun invoke(functionName: String, body: Map<String, Any>): Map<*, *> {
        val result = functions.getHttpsCallable(functionName).call(body).await().data
        return result as? Map<*, *> ?: error("게임 서버 응답 형식이 올바르지 않습니다.")
    }

    override suspend fun findActiveSession(roomId: String): String? {
        val result = invoke("findActiveGameSession", mapOf("roomId" to roomId))
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
}
