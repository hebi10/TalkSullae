package com.hebi.talksullae

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
            return InvitationPreview("a".repeat(20), "친구 게임방", "우리만의 방",
                3, true, System.currentTimeMillis()+60_000)
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
        compose.setContent {
            TalkSullaeTheme {
                OnlineRoomsHost(
                    uid = "invite-test-user", accountName = "실제닉네임",
                    onClose = {}, onSignOut = {}, repository = repo,
                    incomingInviteCode = pending,
                    onInviteCodeHandled = { pending = null; handled++ }
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-invite-confirm").assertExists().performClick()
        compose.waitForIdle()
        assertEquals(1, repo.previewCalls)
        assertEquals(1, repo.acceptedCalls)
        assertEquals(1, handled)
    }
}
