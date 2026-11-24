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
    val info: String? = null,
    val deletingId: String? = null
)

internal class RoomChatViewModel(
    private val repository: RoomChatRepository,
    private val draftStore: RoomChatDraftStore? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(RoomChatState())
    val state: StateFlow<RoomChatState> = mutable
    private var listener: ListenerRegistration? = null
    private var activeRoomId: String? = null
    private var recent: List<RoomChatMessage> = emptyList()
    private var older: List<RoomChatMessage> = emptyList()
    private var oldestCursor: DocumentSnapshot? = null
    private var firstPageReceived = false
    private var readGeneration = 0L
    private var historyGeneration = 0L
    private var deletionListener: ListenerRegistration? = null
    private var lastDeletionRevision: Long? = null

    fun start(roomId: String) {
        if (activeRoomId == roomId && listener != null) return
        stop()
        activeRoomId = roomId
        val saved = draftStore?.load(roomId)
        mutable.value = RoomChatState(draft = saved?.draft.orEmpty(), pending = saved?.pending,
            sendError = if (saved?.pending != null) "이전 전송 결과를 확인해 주세요. 같은 메시지 재시도로 중복 저장을 방지합니다." else null)
        val generation = readGeneration
        listener = repository.watchRecent(roomId, { page ->
            if (activeRoomId == roomId && generation == readGeneration) {
                if (page.fromCache) {
                    historyGeneration++
                    recent = emptyList()
                    older = emptyList()
                    oldestCursor = null
                    firstPageReceived = false
                    mutable.update { it.copy(messages = emptyList(), initialLoading = false,
                        fromCache = true, canLoadOlder = false, loadingOlder = false) }
                    return@watchRecent
                }
                if (firstPageReceived) {
                    older = retainOlderChatMessages(older, recent, page.messages, page.hasMore)
                }
                recent = page.messages
                val firstPage = !firstPageReceived
                firstPageReceived = true
                val canRefreshCursor = firstPage || older.isEmpty()
                if (canRefreshCursor) oldestCursor = page.oldestSnapshot
                mutable.update {
                    it.copy(
                        messages = merge(),
                        initialLoading = false,
                        readError = null,
                        fromCache = page.fromCache,
                        canLoadOlder = if (canRefreshCursor) page.hasMore else it.canLoadOlder
                    )
                }
            }
        }, { message ->
            if (activeRoomId == roomId && generation == readGeneration) {
                historyGeneration++
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
        deletionListener = repository.watchDeletionRevision(roomId, { revision ->
            if (activeRoomId == roomId && generation == readGeneration) {
                val previous = lastDeletionRevision
                lastDeletionRevision = revision
                if (previous != null && revision != previous) retryRead()
            }
        }, { error ->
            if (activeRoomId == roomId && generation == readGeneration) {
                historyGeneration++
                recent = emptyList()
                older = emptyList()
                oldestCursor = null
                firstPageReceived = false
                mutable.update { it.copy(messages = emptyList(), canLoadOlder = false,
                    loadingOlder = false, initialLoading = false, readError = error) }
            }
        })
    }

    fun stop() {
        readGeneration++
        historyGeneration++
        deletionListener?.remove()
        deletionListener = null
        lastDeletionRevision = null
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
            .filter { it.expiresAtMillis == null || it.expiresAtMillis > System.currentTimeMillis() }
            .sortedWith(compareBy<RoomChatMessage> { it.createdAtMillis ?: Long.MAX_VALUE }.thenBy { it.id })

    fun retryRead() {
        val room = activeRoomId ?: return
        val draft = mutable.value.draft
        val pending = mutable.value.pending
        val sending = mutable.value.sending
        val sendError = mutable.value.sendError
        listener?.remove()
        listener = null
        start(room)
        mutable.update {
            it.copy(draft = draft, pending = pending, sending = sending, sendError = sendError)
        }
    }

    fun updateDraft(value: String) {
        mutable.update { it.copy(draft = value, info = null,
            sendError = if (it.pending == null) null else it.sendError) }
        activeRoomId?.let { room ->
            if (!persist(room, mutable.value)) mutable.update { it.copy(info = "이 기기에 초안을 저장하지 못했습니다.") }
        }
    }

    private fun persist(roomId: String, state: RoomChatState, durable: Boolean = false): Boolean =
        draftStore?.save(roomId, StoredRoomDraft(state.draft, state.pending), durable) ?: true

    fun loadOlder() {
        val roomId = activeRoomId ?: return
        val cursor = oldestCursor ?: return
        if (!mutable.value.canLoadOlder || mutable.value.loadingOlder || mutable.value.fromCache) return
        val generation = historyGeneration
        mutable.update { it.copy(loadingOlder = true, historyError = null) }
        viewModelScope.launch {
            try {
                val page = repository.loadOlder(roomId, cursor)
                if (activeRoomId != roomId || generation != historyGeneration || mutable.value.fromCache || mutable.value.readError != null || page.fromCache) return@launch
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
                if (activeRoomId == roomId && generation == historyGeneration && !mutable.value.fromCache) {
                    mutable.update { it.copy(historyError = "이전 메시지를 불러오지 못했습니다. 다시 시도해 주세요.") }
                }
            } finally {
                if (activeRoomId == roomId && generation == historyGeneration) mutable.update { it.copy(loadingOlder = false) }
            }
        }
    }

    fun send() {
        val roomId = activeRoomId ?: return
        if (mutable.value.sending || mutable.value.pending != null || mutable.value.fromCache ||
            mutable.value.initialLoading || mutable.value.readError != null) return
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
        val next = mutable.value.copy(pending = pending, draft = "", sendError = null, info = null)
        if (!persist(roomId, next, durable = true)) {
            mutable.update { it.copy(sendError = "전송 복구 정보를 저장할 수 없어 전송하지 않았습니다.") }
            return
        }
        mutable.value = next
        submit(roomId, pending)
    }

    fun retrySend() {
        val roomId = activeRoomId ?: return
        val pending = mutable.value.pending ?: return
        if (!mutable.value.sending && !mutable.value.fromCache && mutable.value.readError == null &&
            !mutable.value.initialLoading) submit(roomId, pending)
    }

    fun abandonFailedSend() {
        val roomId = activeRoomId ?: return
        if (mutable.value.sending || mutable.value.pending == null) return
        val previous = mutable.value
        mutable.update {
            it.copy(
                draft = if (it.draft.isBlank()) it.pending?.text.orEmpty() else it.draft,
                pending = null,
                sendError = null,
                info = "이전 메시지의 서버 처리 여부는 확인되지 않았습니다. 새로 보내면 중복될 수 있습니다."
            )
        }
        if (!persist(roomId, mutable.value, durable = true)) {
            mutable.value = previous.copy(sendError = "기기에 저장된 전송 정보를 변경하지 못했습니다.")
        }
    }

    fun deleteMessage(messageId: String) {
        val roomId = activeRoomId ?: return
        if (mutable.value.deletingId != null || mutable.value.fromCache || mutable.value.readError != null) return
        mutable.update { it.copy(deletingId = messageId, info = null) }
        viewModelScope.launch {
            try {
                repository.deleteMessage(roomId, messageId)
                if (activeRoomId == roomId) {
                    fun tombstone(messages: List<RoomChatMessage>) = messages.map {
                        if (it.id == messageId) it.copy(text = "삭제된 메시지입니다.", deleted = true) else it
                    }
                    recent = tombstone(recent)
                    older = tombstone(older)
                    mutable.update { it.copy(messages = merge(), info = "메시지를 삭제했습니다.") }
                }
            } catch (_: Exception) {
                if (activeRoomId == roomId) mutable.update { it.copy(info = "메시지를 삭제하지 못했습니다. 다시 확인해 주세요.") }
            } finally {
                if (activeRoomId == roomId) mutable.update { it.copy(deletingId = null) }
            }
        }
    }

    private fun submit(roomId: String, pending: PendingRoomMessage) {
        if (mutable.value.sending) return
        mutable.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            try {
                repository.sendMessage(roomId, pending.requestId, pending.text)
                draftStore?.complete(roomId, pending.requestId)
                if (activeRoomId == roomId && mutable.value.pending == pending) {
                    mutable.update {
                        it.copy(
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

internal fun retainOlderChatMessages(
    older: List<RoomChatMessage>,
    previous: List<RoomChatMessage>,
    incoming: List<RoomChatMessage>,
    fullPage: Boolean
): List<RoomChatMessage> {
    // A missing message within the current window may have been removed, not paged out.
    if (!fullPage || incoming.isEmpty()) return emptyList()
    val order = compareBy<RoomChatMessage> { it.createdAtMillis ?: Long.MAX_VALUE }.thenBy { it.id }
    val boundary = incoming.minWithOrNull(order) ?: return emptyList()
    val ids = incoming.map { it.id }.toSet()
    return (older + previous).filter { it.id !in ids && order.compare(it, boundary) < 0 }
        .associateBy { it.id }.values.toList()
}
