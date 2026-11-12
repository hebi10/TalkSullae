package com.hebi.talksullae.firebase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

internal data class PendingRoomMessage(
    val requestId: String,
    val text: String
)

internal data class RoomChatState(
    val messages: List<RoomChatMessage> = emptyList(),
    val draft: String = "",
    val pending: PendingRoomMessage? = null,
    val initialLoading: Boolean = true,
    val loadingOlder: Boolean = false,
    val canLoadOlder: Boolean = false,
    val sending: Boolean = false,
    val fromCache: Boolean = false,
    val readError: String? = null,
    val historyError: String? = null,
    val sendError: String? = null,
    val info: String? = null
)

internal class RoomChatViewModel(private val repository: RoomChatRepository) : ViewModel() {
    private val mutable = MutableStateFlow(RoomChatState())
    val state: StateFlow<RoomChatState> = mutable
    private var listener: ListenerRegistration? = null
    private var activeRoomId: String? = null
    private var recent: List<RoomChatMessage> = emptyList()
    private var older: List<RoomChatMessage> = emptyList()
    private var oldestCursor: DocumentSnapshot? = null
    private var firstPageReceived = false

    fun start(roomId: String) {
        if (activeRoomId == roomId && listener != null) return
        stop()
        activeRoomId = roomId
        mutable.value = RoomChatState()
        listener = repository.watchRecent(roomId, { page ->
            if (activeRoomId == roomId) {
                val incomingIds = page.messages.map { it.id }.toSet()
                if (firstPageReceived) {
                    older = (older + recent.filterNot { it.id in incomingIds })
                        .associateBy { it.id }.values.toList()
                }
                recent = page.messages
                val firstPage = !firstPageReceived
                firstPageReceived = true
                if (firstPage) oldestCursor = page.oldestSnapshot
                mutable.update {
                    it.copy(
                        messages = merge(),
                        initialLoading = false,
                        readError = null,
                        fromCache = page.fromCache,
                        canLoadOlder = if (firstPage) page.hasMore else it.canLoadOlder
                    )
                }
            }
        }, { message ->
            if (activeRoomId == roomId) {
                recent = emptyList()
                older = emptyList()
                oldestCursor = null
                firstPageReceived = false
                mutable.update {
                    it.copy(
                        messages = emptyList(),
                        initialLoading = false,
                        canLoadOlder = false,
                        readError = message
                    )
                }
            }
        })
    }

    fun stop() {
        listener?.remove()
        listener = null
        activeRoomId = null
        recent = emptyList()
        older = emptyList()
        oldestCursor = null
        firstPageReceived = false
        mutable.value = RoomChatState()
    }

    private fun merge(): List<RoomChatMessage> =
        (older + recent)
            .associateBy { it.id }
            .values
            .sortedWith(compareBy<RoomChatMessage> { it.createdAtMillis ?: Long.MAX_VALUE }.thenBy { it.id })

    fun updateDraft(value: String) {
        if (mutable.value.pending == null) {
            mutable.update { it.copy(draft = value, sendError = null, info = null) }
        }
    }

    fun loadOlder() {
        val roomId = activeRoomId ?: return
        val cursor = oldestCursor ?: return
        if (!mutable.value.canLoadOlder || mutable.value.loadingOlder) return
        mutable.update { it.copy(loadingOlder = true, historyError = null) }
        viewModelScope.launch {
            try {
                val page = repository.loadOlder(roomId, cursor)
                if (activeRoomId != roomId) return@launch
                older = (older + page.messages).associateBy { it.id }.values.toList()
                if (page.oldestSnapshot != null) oldestCursor = page.oldestSnapshot
                mutable.update {
                    it.copy(
                        messages = merge(),
                        canLoadOlder = page.hasMore && page.oldestSnapshot != null,
                        fromCache = page.fromCache,
                        historyError = null
                    )
                }
            } catch (_: Exception) {
                if (activeRoomId == roomId) {
                    mutable.update { it.copy(historyError = "이전 메시지를 불러오지 못했습니다. 다시 시도해 주세요.") }
                }
            } finally {
                if (activeRoomId == roomId) mutable.update { it.copy(loadingOlder = false) }
            }
        }
    }

    fun send() {
        val roomId = activeRoomId ?: return
        if (mutable.value.sending || mutable.value.pending != null) return
        val draft = mutable.value.draft
        val issue = RoomChatInputPolicy.error(draft)
        if (issue != null) {
            mutable.update { it.copy(sendError = issue) }
            return
        }
        val pending = PendingRoomMessage(
            requestId = UUID.randomUUID().toString(),
            text = RoomChatInputPolicy.normalize(draft)
        )
        mutable.update { it.copy(pending = pending, sendError = null, info = null) }
        submit(roomId, pending)
    }

    fun retrySend() {
        val roomId = activeRoomId ?: return
        val pending = mutable.value.pending ?: return
        if (!mutable.value.sending) submit(roomId, pending)
    }

    fun abandonFailedSend() {
        if (mutable.value.sending || mutable.value.pending == null) return
        mutable.update {
            it.copy(
                pending = null,
                sendError = null,
                info = "이전 메시지의 서버 처리 여부는 확인되지 않았습니다. 새로 보내면 중복될 수 있습니다."
            )
        }
    }

    private fun submit(roomId: String, pending: PendingRoomMessage) {
        if (mutable.value.sending) return
        mutable.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            try {
                repository.sendMessage(roomId, pending.requestId, pending.text)
                if (activeRoomId == roomId && mutable.value.pending == pending) {
                    mutable.update {
                        it.copy(
                            draft = "",
                            pending = null,
                            sending = false,
                            sendError = null,
                            info = null
                        )
                    }
                }
            } catch (_: Exception) {
                if (activeRoomId == roomId && mutable.value.pending == pending) {
                    mutable.update {
                        it.copy(
                            sending = false,
                            sendError = "메시지 전송 결과를 확인하지 못했습니다. 같은 메시지 재시도를 이용해 주세요."
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
