package com.hebi.talksullae.game.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.game.core.GamePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

internal data class PendingLiarAction(
    val requestId: String,
    val revision: Long,
    val payload: Map<String, Any>,
    val gameId: String,
    val rulesVersion: Int
)

internal data class PendingLiarMessage(
    val requestId: String,
    val text: String
)

internal data class LiarGameState(
    val session: OnlineGameSnapshot? = null,
    val personal: Map<*, *>? = null,
    val personalLoading: Boolean = false,
    val personalError: String? = null,
    val otherGameActive: Boolean = false,
    val messages: List<LiarChatMessage> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val draft: String = "",
    val pendingAction: PendingLiarAction? = null,
    val pendingMessage: PendingLiarMessage? = null,
    val canLoadOlder: Boolean = false,
    val loadingOlder: Boolean = false
) {
    val role: String? get() = personal?.get("role") as? String
    val topic: String? get() = personal?.get("topic") as? String
    val hasSubmitted: Boolean get() = personal?.get("submitted") as? Boolean ?: false
}

internal class LiarGameViewModel(private val repository: OnlineGameSessionRepository) : ViewModel() {
    private val mutable = MutableStateFlow(LiarGameState())
    val state: StateFlow<LiarGameState> = mutable
    private var roomId: String? = null
    private var uid: String? = null
    private var sessionId: String? = null
    private var sessionListener: ListenerRegistration? = null
    private var messageListener: ListenerRegistration? = null
    private var recent: List<LiarChatMessage> = emptyList()
    private var older: List<LiarChatMessage> = emptyList()
    private var oldestCursor: DocumentSnapshot? = null
    private var sessionGeneration: Long = 0
    private var personalRequestRevision: Long? = null
    private var personalRefreshPending: Boolean = false

    fun start(room: String, user: String) {
        if(roomId == room && uid == user) return
        stop()
        roomId = room
        uid = user
        refresh()
    }

    fun stop() {
        sessionGeneration++
        personalRequestRevision = null
        personalRefreshPending = false
        sessionListener?.remove()
        messageListener?.remove()
        sessionListener = null
        messageListener = null
        roomId = null
        uid = null
        sessionId = null
        recent = emptyList()
        older = emptyList()
        oldestCursor = null
        mutable.value = LiarGameState()
    }

    fun refresh() {
        val room = roomId ?: return
        val generation = sessionGeneration
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val active = repository.findActiveSession(room)
                if(generation != sessionGeneration || roomId != room) return@launch
                if(active == null){
                    unbind()
                    mutable.update { it.copy(
                        loading = false, session = null, personal = null,
                        otherGameActive = false
                    ) }
                }else bind(room,active)
            }catch(e: Exception){
                if(generation == sessionGeneration && roomId == room){
                    mutable.update { it.copy(loading = false, error = e.localizedMessage ?: "게임 조회에 실패했습니다.") }
                }
            }
        }
    }

    private fun unbind() {
        personalRequestRevision = null
        personalRefreshPending = false
        sessionListener?.remove()
        messageListener?.remove()
        sessionListener = null
        messageListener = null
        sessionId = null
        recent = emptyList()
        older = emptyList()
        oldestCursor = null
        mutable.update { it.copy(
            session = null, personal = null, personalLoading = false,
            personalError = null, messages = emptyList()
        ) }
    }

    private fun bind(room: String, id: String) {
        if(sessionId == id && sessionListener != null){
            mutable.update { it.copy(loading = false) }
            return
        }
        unbind()
        sessionId = id
        val generation = sessionGeneration
        sessionListener = repository.watchSession(room,id, { snapshot ->
            if(generation != sessionGeneration || sessionId != id) return@watchSession
            if(snapshot == null){
                unbind()
                mutable.update { it.copy(loading = false, error = "게임 세션이 없어졌습니다.") }
            }else{
                if(snapshot.session.gameId != "liar"){
                    mutable.update { it.copy(
                        session = null, personal = null, loading = false,
                        otherGameActive = true, messages = emptyList()
                    ) }
                    return@watchSession
                }
                val member = snapshot.participants.any { it.uid == uid }
                mutable.update {
                    it.copy(
                        session = snapshot,
                        loading = false,
                        otherGameActive = false,
                        error = null,
                        personal = if(member) it.personal else null,
                        personalLoading = if(member) it.personalLoading else false,
                        personalError = if(member) it.personalError else null,
                        messages = if(member) it.messages else emptyList()
                    )
                }
                if(member){
                    refreshPersonal(room,id,generation)
                    if(snapshot.session.phase !in setOf(GamePhase.WAITING,GamePhase.READY)) {
                        watchMessages(room,id,generation)
                    }
                }else {
                    messageListener?.remove()
                    messageListener = null
                    recent = emptyList()
                    older = emptyList()
                }
            }
        }, { error ->
            if(generation == sessionGeneration && sessionId == id){
                unbind()
                mutable.update { it.copy(loading = false, error = error) }
            }
        })
    }


    fun retryPersonal() {
        val room = roomId ?: return
        val id = sessionId ?: return
        val user = uid ?: return
        if (mutable.value.session?.participants?.any { it.uid == user } != true) return
        refreshPersonal(room,id,sessionGeneration)
    }


    private fun refreshPersonal(room: String,id: String,generation: Long) {
        val expectedRevision = mutable.value.session?.session?.revision ?: return
        if(mutable.value.personalLoading) {
            if(personalRequestRevision != expectedRevision) personalRefreshPending = true
            return
        }
        personalRequestRevision = expectedRevision
        mutable.update { it.copy(personalLoading = true, personalError = null) }
        viewModelScope.launch {
            try {
                val (returned, personal) = repository.restoreMySession(room,id)
                if(generation == sessionGeneration && sessionId == id){
                    val current = mutable.value.session?.session
                    when {
                        current?.revision != expectedRevision -> personalRefreshPending = true
                        returned.session.revision != expectedRevision -> {
                            mutable.update { it.copy(
                                personalError = "개인 게임 상태가 갱신됐습니다. 다시 불러와 주세요."
                            ) }
                        }
                        personal == null &&
                            (current?.phase == GamePhase.PLAYING ||
                                current?.phase == GamePhase.VOTING) -> {
                            mutable.update { it.copy(
                                personalError = "개인 역할을 복원하지 못했습니다. 다시 불러와 주세요."
                            ) }
                        }
                        else -> {
                            mutable.update { it.copy(personal = personal, personalError = null) }
                        }
                    }
                }
            }catch(_: Exception) {
                if(generation == sessionGeneration && sessionId == id){
                    mutable.update { it.copy(
                        personalError = "개인 역할을 복원하지 못했습니다. 다시 불러와 주세요."
                    ) }
                }
            }finally {
                if(generation == sessionGeneration && sessionId == id){
                    personalRequestRevision = null
                    mutable.update { it.copy(personalLoading = false) }
                    if(personalRefreshPending) {
                        personalRefreshPending = false
                        refreshPersonal(room,id,generation)
                    }
                }
            }
        }
    }

    private fun mergeMessages(): List<LiarChatMessage> =
        (older + recent).associateBy { it.id }.values.sortedWith(
            compareBy<LiarChatMessage> { it.createdAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )

    private fun watchMessages(room: String,id: String,generation: Long) {
        if(messageListener != null) return
        messageListener = repository.watchLiarMessages(room,id, { page ->
            if(generation != sessionGeneration || sessionId != id) return@watchLiarMessages
            val ids = page.messages.map { it.id }.toSet()
            older = (older + recent.filterNot { it.id in ids })
                .associateBy { it.id }.values.toList()
            recent = page.messages
            if(oldestCursor == null || older.isEmpty()) oldestCursor = page.cursor
            mutable.update { it.copy(
                messages = mergeMessages(),
                canLoadOlder = page.hasMore || it.canLoadOlder
            ) }
        }, { message ->
            if(generation == sessionGeneration && sessionId == id){
                recent = emptyList()
                older = emptyList()
                mutable.update { it.copy(messages = emptyList(), error = message) }
            }
        })
    }

    fun loadOlder() {
        val room = roomId ?: return
        val id = sessionId ?: return
        val cursor = oldestCursor ?: return
        if(mutable.value.loadingOlder || !mutable.value.canLoadOlder) return
        val generation = sessionGeneration
        mutable.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            try{
                val page = repository.loadOlderLiarMessages(room,id,cursor)
                if(generation == sessionGeneration && sessionId == id){
                    older = (older + page.messages).associateBy { it.id }.values.toList()
                    if(page.cursor != null) oldestCursor = page.cursor
                    mutable.update { it.copy(
                        messages = mergeMessages(),canLoadOlder = page.hasMore
                    ) }
                }
            }catch(_: Exception){
                if(generation == sessionGeneration){
                    mutable.update { it.copy(info = "과거 메시지 조회에 실패했습니다.") }
                }
            }finally{
                if(generation == sessionGeneration){
                    mutable.update { it.copy(loadingOlder = false) }
                }
            }
        }
    }

    fun create() {
        val room = roomId ?: return
        if(mutable.value.busy || mutable.value.session != null ||
            mutable.value.otherGameActive) return
        val generation = sessionGeneration
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try{
                val id = repository.createSession(room,"liar","realtime",UUID.randomUUID().toString())
                if(generation == sessionGeneration && roomId == room) bind(room,id)
            }catch(e:Exception){
                if(generation == sessionGeneration){
                    mutable.update { it.copy(error = e.localizedMessage ?: "게임 생성에 실패했습니다.") }
                }
            }finally{
                if(generation == sessionGeneration) mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun action(kind: String, extra: Map<String, Any> = emptyMap()) {
        val snap = mutable.value.session ?: return
        if(mutable.value.busy || mutable.value.pendingAction != null) return
        val req = PendingLiarAction(
            requestId = UUID.randomUUID().toString(),
            revision = snap.session.revision,
            payload = mapOf("kind" to kind) + extra,
            gameId = snap.session.gameId,
            rulesVersion = snap.session.rulesVersion
        )
        mutable.update { it.copy(pendingAction = req, error = null) }
        submitAction(req)
    }

    fun retryAction() {
        val pending = mutable.value.pendingAction ?: return
        if(!mutable.value.busy) submitAction(pending)
    }

    fun discardFailedAction() {
        if(mutable.value.busy) return
        mutable.update { it.copy(
            pendingAction = null,error = null,
            info = "서버 처리가 끝났을 수 있습니다. 현재 게임 상태를 확인해 주세요."
        ) }
        refresh()
    }

    private fun submitAction(pending: PendingLiarAction) {
        val room = roomId ?: return
        val id = sessionId ?: return
        val generation = sessionGeneration
        mutable.update { it.copy(busy = true,error = null) }
        viewModelScope.launch {
            try {
                repository.submitAction(
                    room,id,pending.gameId,pending.rulesVersion,pending.revision,
                    pending.requestId,pending.payload
                )
                if(generation == sessionGeneration && sessionId == id){
                    mutable.update { it.copy(pendingAction = null,error = null) }
                    refreshPersonal(room,id,generation)
                }
            }catch(e:Exception){
                if(generation == sessionGeneration && sessionId == id){
                    mutable.update { it.copy(
                        error = (e.localizedMessage ?: "게임 요청 실패") + " · 같은 요청 재시도 가능"
                    ) }
                }
            }finally{
                if(generation == sessionGeneration) mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun editMessage(value: String) {
        if(mutable.value.pendingMessage == null){
            mutable.update { it.copy(draft = value) }
        }
    }

    fun sendMessage() {
        val raw = mutable.value.draft.trim()
        if(raw.isEmpty()||raw.length>500||mutable.value.pendingMessage!=null) return
        val pending=PendingLiarMessage(UUID.randomUUID().toString(),raw)
        mutable.update { it.copy(pendingMessage = pending,error = null) }
        submitMessage(pending)
    }

    fun retryMessage() {
        val pending=mutable.value.pendingMessage ?: return
        if(!mutable.value.sending) submitMessage(pending)
    }

    fun abandonMessage() {
        if(mutable.value.sending)return
        mutable.update { it.copy(
            pendingMessage = null,error = null,
            info = "기존 라이어 채팅 메시지가 저장됐을 수 있습니다. 새 전송은 중복될 수 있습니다."
        ) }
    }

    private fun submitMessage(pending: PendingLiarMessage) {
        val room = roomId ?: return
        val id = sessionId ?: return
        val generation=sessionGeneration
        mutable.update { it.copy(sending = true,error = null) }
        viewModelScope.launch {
            try{
                repository.sendLiarMessage(room,id,pending.requestId,pending.text)
                if(generation == sessionGeneration && sessionId == id){
                    mutable.update { it.copy(
                        sending = false,pendingMessage = null,draft = "",error = null
                    ) }
                }
            }catch(e:Exception){
                if(generation == sessionGeneration && sessionId == id){
                    mutable.update { it.copy(
                        sending = false,
                        error = (e.localizedMessage ?: "라이어 채팅 메시지 전송 실패") + " · 같은 메시지 재시도 가능"
                    ) }
                }
            }
        }
    }

    override fun onCleared(){
        stop()
        super.onCleared()
    }
}
