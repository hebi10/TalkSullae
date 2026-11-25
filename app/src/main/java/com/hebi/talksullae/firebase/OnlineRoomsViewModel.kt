package com.hebi.talksullae.firebase

import com.hebi.talksullae.ui.entry.InviteLinkCodec
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineRoomsState(
    val entries: List<CloudRoomIndex> = emptyList(),
    val selectedId: String? = null,
    val selectedRoom: CloudRoom? = null,
    val members: List<CloudMember> = emptyList(),
    val working: Boolean = false,
    val loading: Boolean = true,
    val invite: Invitation? = null,
    val joinPreview: InvitationPreview? = null,
    val pendingJoinCode: String? = null,
    val createdRoomId: String? = null,
    val info: String? = null,
    val error: String? = null
)

internal class OnlineRoomsViewModel(
    private val repository: OnlineRoomsRepository,
    private val uid: String
) : ViewModel() {
    private val stateMutable = MutableStateFlow(OnlineRoomsState())
    val state: StateFlow<OnlineRoomsState> = stateMutable
    private var listListener: ListenerRegistration? = null
    private var roomListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null

    fun start() {
        if (listListener != null) return
        listListener = repository.watchMyRooms(uid,
            { result ->
                val selectedId = stateMutable.value.selectedId
                if (selectedId != null && result.none {
                    it.id == selectedId && it.status == "active"
                }) {
                    revokeSelectedRoom("이 방의 가입 상태가 변경되어 목록으로 돌아왔습니다.")
                    stateMutable.update { it.copy(entries = result, error = null, loading = false) }
                } else {
                    stateMutable.update { it.copy(entries = result, error = null, loading = false) }
                }
            },
            { message -> stateMutable.update { it.copy(error = message, loading = false) } }
        )
    }

    fun retry() {
        listListener?.remove()
        listListener = null
        stateMutable.update { it.copy(loading = true, error = null) }
        start()
    }

    fun stop(preserveInvite: Boolean = false) {
        listListener?.remove()
        listListener = null
        closeRoomListeners()
        val old = stateMutable.value
        stateMutable.value = if (preserveInvite && old.pendingJoinCode != null) {
            OnlineRoomsState(
                joinPreview = old.joinPreview,
                pendingJoinCode = old.pendingJoinCode,
                loading = true
            )
        } else {
            OnlineRoomsState()
        }
    }

    fun openRoom(roomId: String) {
        closeRoomListeners()
        stateMutable.update { it.copy(
            selectedId = roomId, selectedRoom = null, members = emptyList(), invite = null, error = null
        ) }
        val entry = stateMutable.value.entries.firstOrNull { it.id == roomId }
        if (entry?.status != "active") return
        roomListener = repository.watchRoom(roomId,
            { value -> stateMutable.update { it.copy(selectedRoom = value) } },
            { message -> revokeSelectedRoom(message) }
        )
        membersListener = repository.watchMembers(roomId,
            { value -> stateMutable.update { it.copy(members = value) } },
            { message -> revokeSelectedRoom(message) }
        )
    }

    private fun revokeSelectedRoom(message: String) {
        closeRoomListeners()
        stateMutable.update {
            it.copy(
                selectedId = null, selectedRoom = null,
                members = emptyList(), invite = null, info = message
            )
        }
    }

    fun closeRoom() {
        closeRoomListeners()
        stateMutable.update {
            it.copy(selectedId = null, selectedRoom = null, members = emptyList(), invite = null)
        }
    }

    fun clearNotice() = stateMutable.update { it.copy(info = null, error = null) }
    fun clearCreatedRoom() = stateMutable.update { it.copy(createdRoomId = null) }
    fun dismissJoinPreview() = stateMutable.update {
        it.copy(joinPreview = null, pendingJoinCode = null)
    }

    private fun action(successMessage: String, block: suspend () -> Unit) {
        if (stateMutable.value.working) return
        stateMutable.update { it.copy(working = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                block()
                if (successMessage.isNotEmpty()) {
                    stateMutable.update { if (it.info == null) it.copy(info = successMessage) else it }
                }
            } catch (e: Exception) {
                stateMutable.update { it.copy(error = e.localizedMessage ?: "연결 오류가 발생했습니다.") }
            } finally {
                stateMutable.update { it.copy(working = false) }
            }
        }
    }

    fun createRoom(name: String, description: String, approval: Boolean) =
        action("새 온라인 방이 생성됐습니다.") {
            val newRoomId = repository.createRoom(name, description, approval)
            stateMutable.update { it.copy(createdRoomId = newRoomId) }
        }

    fun updateRoom(roomId: String, name: String, description: String, approval: Boolean, expectedRevision: Long) {
        if (stateMutable.value.selectedId != roomId || stateMutable.value.selectedRoom?.ownerId != uid) return
        action("방 정보를 수정했습니다.") {
            repository.updateRoom(roomId, name, description, approval, expectedRevision)
        }
    }

    fun archiveRoom(roomId: String) {
        if (stateMutable.value.selectedId != roomId || stateMutable.value.selectedRoom?.ownerId != uid) return
        action("방을 종료했습니다.") {
            repository.archiveRoom(roomId)
            if (stateMutable.value.selectedId == roomId) closeRoom()
        }
    }

    fun generateInvitation(roomId: String) =
        action("초대 코드가 생성됐습니다. 신뢰하는 친구에게만 공유해 주세요.") {
            val result = repository.createInvitation(roomId)
            stateMutable.update { state ->
                if (state.selectedId == roomId && state.selectedRoom?.ownerId == uid) state.copy(invite = result)
                else state
            }
        }

    fun revokeInvitation(roomId: String, expectedHash: String) {
        if (stateMutable.value.selectedId != roomId || stateMutable.value.selectedRoom?.ownerId != uid) return
        action("초대 코드를 취소했습니다.") {
            repository.revokeInvitation(roomId, expectedHash)
            stateMutable.update { if (it.selectedId == roomId) it.copy(invite = null) else it }
        }
    }

    fun openNotification(target: RoomNotificationTarget): Boolean {
        val current = stateMutable.value
        if (target.recipientUid != uid || current.loading || current.error != null || current.working) return false
        if (current.entries.none { it.id == target.roomId && it.status == "active" }) {
            stateMutable.update { it.copy(info = "현재 접근할 수 없는 방입니다.") }
            return true
        }
        if (current.selectedId != target.roomId) openRoom(target.roomId)
        return true
    }

    fun previewInvite(value: String): Boolean {
        if (stateMutable.value.working) return false
        val code = InviteLinkCodec.normalizeInput(value)
        if (code == null) {
            stateMutable.update { it.copy(error = "초대 코드 또는 링크 형식이 올바르지 않습니다.") }
            return true
        }
        stateMutable.update { it.copy(joinPreview = null, pendingJoinCode = null) }
        action("") {
            val result = repository.previewInvitation(code)
            stateMutable.update {
                it.copy(joinPreview = result, pendingJoinCode = code, info = null)
            }
        }
        return true
    }

    fun join(value: String) {
        val code = InviteLinkCodec.normalizeInput(value)
        if (code == null) {
            stateMutable.update { it.copy(error = "초대 코드가 올바르지 않습니다.") }
            return
        }
        action("") {
            val result = repository.requestJoin(code)
            stateMutable.update {
                it.copy(
                    joinPreview = null, pendingJoinCode = null,
                    info = if (result.status == "pending") {
                        "가입 요청이 접수됐습니다. 방장의 승인을 기다립니다."
                    } else {
                        "가입이 완료됐습니다."
                    }
                )
            }
        }
    }

    fun confirmJoin() {
        val code = stateMutable.value.pendingJoinCode ?: return
        join(code)
    }

    fun manage(roomId: String, memberId: String, operation: String) =
        action("멤버 관리 요청이 서버에서 처리됐습니다.") {
            repository.changeMember(roomId, memberId, operation)
        }

    fun leave(roomId: String) = action("방을 나갔습니다.") {
        repository.leaveRoom(roomId)
        closeRoom()
    }

    private fun closeRoomListeners() {
        roomListener?.remove()
        roomListener = null
        membersListener?.remove()
        membersListener = null
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
