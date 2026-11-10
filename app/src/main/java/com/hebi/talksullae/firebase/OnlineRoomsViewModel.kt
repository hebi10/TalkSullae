package com.hebi.talksullae.firebase

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
    val invite: Invitation? = null,
    val info: String? = null,
    val error: String? = null
)

class OnlineRoomsViewModel(
    private val repository: CloudRoomsRepository,
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
            { result -> stateMutable.update { it.copy(entries = result, error = null) } },
            { message -> stateMutable.update { it.copy(error = message) } }
        )
    }

    fun stop() {
        listListener?.remove()
        listListener = null
        closeRoomListeners()
        stateMutable.value = OnlineRoomsState()
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
            { message -> stateMutable.update { it.copy(error = message) } }
        )
        membersListener = repository.watchMembers(roomId,
            { value -> stateMutable.update { it.copy(members = value) } },
            { message -> stateMutable.update { it.copy(error = message) } }
        )
    }

    fun closeRoom() {
        closeRoomListeners()
        stateMutable.update { it.copy(selectedId = null, selectedRoom = null, invite = null) }
    }

    fun clearNotice() = stateMutable.update { it.copy(info = null, error = null) }

    private fun action(successMessage: String, block: suspend () -> Unit) {
        if (stateMutable.value.working) return
        viewModelScope.launch {
            stateMutable.update { it.copy(working = true, error = null, info = null) }
            try {
                block()
                stateMutable.update { if (it.info == null) it.copy(info = successMessage) else it }
            } catch (e: Exception) {
                stateMutable.update { it.copy(error = e.localizedMessage ?: "연결 오류가 발생했습니다.") }
            } finally {
                stateMutable.update { it.copy(working = false) }
            }
        }
    }

    fun createRoom(name: String, description: String, approval: Boolean) =
        action("온라인 방 생성이 완료됐습니다.") {
            repository.createRoom(name, description, approval)
            // User's room index is populated by the server; its listener updates the list.
            stateMutable.update { it.copy(info = "새 온라인 방이 생성됐습니다.") }
            // The new room is selected only after the index becomes visible.
        }

    fun generateInvitation(roomId: String) =
        action("초대 코드가 생성됐습니다. 신뢰하는 친구에게만 공유해 주세요.") {
            val result = repository.createInvitation(roomId)
            stateMutable.update { it.copy(invite = result) }
        }

    fun join(code: String) =
        action("가입 상태를 확인해 주세요.") {
            val result = repository.requestJoin(code.trim())
            stateMutable.update { it.copy(info = if (result.status == "pending") {
                "가입 요청이 접수됐습니다. 방장의 승인을 기다립니다."
            } else {
                "가입이 완료됐습니다."
            }) }
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
