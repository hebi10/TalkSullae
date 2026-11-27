package com.hebi.talksullae

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.firebase.CloudRoom
import com.hebi.talksullae.firebase.CloudRoomIndex
import com.hebi.talksullae.firebase.Invitation
import com.hebi.talksullae.firebase.InvitationPreview
import com.hebi.talksullae.firebase.JoinResult
import com.hebi.talksullae.firebase.OnlineRoomsRepository
import com.hebi.talksullae.ui.entry.InviteLinkCodec
import com.hebi.talksullae.ui.rooms.OnlineRoomsHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnlineInviteFlowTest {
    @get:Rule val compose = createComposeRule()

    private class PreviewRepository : OnlineRoomsRepository {
        var previewCalls = 0
        var acceptedCalls = 0
        var failNextPreview = false
        var requiresApproval = true
        private fun listener() = object : ListenerRegistration {
            override fun remove() = Unit
        }
        override fun watchMyRooms(uid: String, onResult: (List<CloudRoomIndex>) -> Unit,
                                  onError: (String) -> Unit): ListenerRegistration {
            onResult(emptyList())
            return listener()
        }
        override fun watchRoom(roomId: String, onResult: (CloudRoom?) -> Unit,
                               onError: (String) -> Unit): ListenerRegistration = listener()
        override fun watchMembers(roomId: String, onResult: (List<CloudMember>) -> Unit,
                                  onError: (String) -> Unit): ListenerRegistration = listener()
        override suspend fun createRoom(name: String, description: String,
                                        approval: Boolean): String = error("not used")
        override suspend fun createInvitation(roomId: String): Invitation = error("not used")
        override suspend fun previewInvitation(code: String): InvitationPreview {
            previewCalls++
            if (failNextPreview) {
                failNextPreview = false
                error("일시적인 연결 오류")
            }
            return InvitationPreview("a".repeat(20), "친구 게임방", "우리만의 방",
                3, requiresApproval, System.currentTimeMillis()+60_000)
        }
        override suspend fun requestJoin(code: String): JoinResult {
            acceptedCalls++
            return JoinResult("a".repeat(20), "pending")
        }
        override suspend fun changeMember(roomId: String, targetUid: String,
                                          action: String) = Unit
        override suspend fun leaveRoom(roomId: String) = Unit
    }

    @Test fun incomingInviteShowsServerPreviewBeforeConfirmedJoin() {
        val code = "a".repeat(20) + "." + "B".repeat(43)
        val repo = PreviewRepository()
        var handled = 0
        var pending by mutableStateOf(InviteLinkCodec.read(InviteLinkCodec.encode(code)))
        var deliveryId by mutableLongStateOf(1L)
        compose.setContent {
            TalkSullaeTheme {
                OnlineRoomsHost(
                    uid = "invite-test-user", accountName = "실제닉네임",
                    onClose = {}, onSignOut = {}, repository = repo,
                    incomingInviteCode = pending,
                    incomingInviteDeliveryId = deliveryId,
                    onInviteCodeHandled = { pending = null; handled++ }
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("가입 승인 필요").assertExists()
        compose.onNodeWithTag("live-invite-confirm").assertExists().performClick()
        compose.waitForIdle()
        assertEquals(1, repo.previewCalls)
        assertEquals(1, repo.acceptedCalls)
        assertEquals(1, handled)
    }

    @Test fun sameExternalInviteRedeliveryRetriesAfterNetworkFailure() {
        val code = "a".repeat(20) + "." + "B".repeat(43)
        val repo = PreviewRepository().apply { failNextPreview = true }
        var pending by mutableStateOf<String?>(code)
        var deliveryId by mutableLongStateOf(1L)
        compose.setContent {
            TalkSullaeTheme {
                OnlineRoomsHost(
                    uid = "retry-invite-user", accountName = "실제닉네임",
                    onClose = {}, onSignOut = {}, repository = repo,
                    incomingInviteCode = pending,
                    incomingInviteDeliveryId = deliveryId,
                    onInviteCodeHandled = { pending = null }
                )
            }
        }
        compose.waitForIdle()
        assertEquals(1, repo.previewCalls)
        compose.runOnIdle { deliveryId++ }
        compose.waitForIdle()
        compose.onNodeWithTag("live-invite-confirm").assertExists()
        assertEquals(2, repo.previewCalls)
    }


    @Test fun directJoinDoesNotShowApprovalOnlyRestriction() {
        val code = "a".repeat(20) + "." + "B".repeat(43)
        val repo = PreviewRepository().apply { requiresApproval = false }
        compose.setContent {
            TalkSullaeTheme {
                OnlineRoomsHost(uid = "direct-invite-user", accountName = "QA",
                    onClose = {}, onSignOut = {}, repository = repo,
                    incomingInviteCode = code, incomingInviteDeliveryId = 1L)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("바로 가입 가능").assertExists()
        compose.onNodeWithText("승인 전에는 방의 채팅·멤버 정보·게임 기록을 볼 수 없습니다.").assertDoesNotExist()
        compose.onNodeWithTag("live-invite-confirm").assertExists()
        compose.runOnIdle { assertEquals(0, repo.acceptedCalls) }
    }
}
