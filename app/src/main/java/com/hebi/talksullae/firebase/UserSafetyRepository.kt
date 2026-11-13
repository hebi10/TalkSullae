package com.hebi.talksullae.firebase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

internal interface UserSafetyRepository {
    fun watchBlocked(uid: String, onResult: (Set<String>) -> Unit, onError: (String) -> Unit)
        : ListenerRegistration
    suspend fun setBlocked(uid: String, targetUid: String, blocked: Boolean)
    suspend fun reportMessage(
        roomId: String, messageId: String, requestId: String,
        category: String, details: String
    ): String
}

internal class FirestoreUserSafetyRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) : UserSafetyRepository {
    override fun watchBlocked(
        uid: String, onResult: (Set<String>) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration = db.collection("userSafety").document(uid)
        .collection("blocks").limit(100L)
        .addSnapshotListener { snapshot, error ->
            if (error != null) onError("차단 목록을 불러올 수 없습니다.")
            else if (snapshot != null) onResult(snapshot.documents.map { it.id }.toSet())
        }

    override suspend fun setBlocked(uid: String, targetUid: String, blocked: Boolean) {
        functions.getHttpsCallable("setUserBlock")
            .call(mapOf("targetUid" to targetUid, "blocked" to blocked)).await()
    }

    override suspend fun reportMessage(
        roomId: String, messageId: String, requestId: String,
        category: String, details: String
    ): String {
        val body = mapOf(
            "roomId" to roomId, "messageId" to messageId,
            "requestId" to requestId, "category" to category, "details" to details
        )
        val result = functions.getHttpsCallable("reportChatMessage").call(body).await()
            .data as? Map<*, *> ?: error("신고 응답 형식이 잘못되었습니다.")
        return result["reportId"] as? String ?: error("신고 번호를 확인할 수 없습니다.")
    }
}

internal data class PendingSafetyReport(
    val roomId: String, val messageId: String, val requestId: String,
    val category: String, val details: String
)

internal data class UserSafetyState(
    val blocked: Set<String> = emptySet(),
    val working: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val pending: PendingSafetyReport? = null
)

internal class UserSafetyViewModel(private val repository: UserSafetyRepository) : ViewModel() {
    private val mutable = MutableStateFlow(UserSafetyState())
    val state: StateFlow<UserSafetyState> = mutable
    private var listener: ListenerRegistration? = null
    private var uid: String? = null

    fun start(user: String) {
        if(uid==user && listener!=null) return
        stop()
        uid=user
        listener=repository.watchBlocked(user, { blocked ->
            if(uid==user)mutable.update { it.copy(blocked=blocked) }
        }, { message ->
            if(uid==user)mutable.update { it.copy(error=message) }
        })
    }
    fun stop() {
        listener?.remove()
        listener=null
        uid=null
        mutable.value=UserSafetyState()
    }

    fun setBlocked(targetUid: String, blocked: Boolean) {
        val user=uid ?: return
        if(targetUid==user||mutable.value.working)return
        mutable.update { it.copy(working=true,error=null,info=null) }
        viewModelScope.launch {
            try {
                repository.setBlocked(user,targetUid,blocked)
                if(uid==user) mutable.update { it.copy(
                    info=if(blocked)"차단했습니다. 내 화면에서 메시지를 숨깁니다."
                         else "차단을 해제했습니다."
                ) }
            } catch(_: Exception) {
                if(uid==user)mutable.update { it.copy(error="차단 변경에 실패했습니다. 다시 시도해 주세요.") }
            } finally {
                if(uid==user)mutable.update { it.copy(working=false) }
            }
        }
    }

    fun report(roomId: String, messageId: String, category: String, details: String) {
        if(mutable.value.working || details.trim().length !in 5..1000)return
        val pending=PendingSafetyReport(roomId,messageId,UUID.randomUUID().toString(),category,details.trim())
        mutable.update { it.copy(pending=pending) }
        submitReport(pending)
    }

    fun retryReport() {
        mutable.value.pending?.let { if(!mutable.value.working)submitReport(it) }
    }

    private fun submitReport(pending: PendingSafetyReport) {
        val user=uid ?: return
        mutable.update { it.copy(working=true,error=null) }
        viewModelScope.launch {
            try {
                val id=repository.reportMessage(
                    pending.roomId,pending.messageId,pending.requestId,
                    pending.category,pending.details
                )
                if(uid==user) mutable.update {
                    it.copy(info="신고 접수 번호: $id",pending=null)
                }
            } catch(_: Exception) {
                if(uid==user)mutable.update { it.copy(
                    error="신고 접수 결과를 확인하지 못했습니다. 같은 요청으로 다시 시도할 수 있습니다."
                ) }
            } finally {
                if(uid==user)mutable.update { it.copy(working=false) }
            }
        }
    }
    override fun onCleared(){stop();super.onCleared()}
}
