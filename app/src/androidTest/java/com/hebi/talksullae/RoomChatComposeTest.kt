package com.hebi.talksullae

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.RoomChatMessage
import com.hebi.talksullae.firebase.RoomChatPage
import com.hebi.talksullae.firebase.RoomChatRepository
import com.hebi.talksullae.ui.rooms.OnlineRoomChatHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoomChatComposeTest {
    @get:Rule val composeRule = createComposeRule()

    private class FakeChatRepository : RoomChatRepository {
        var sentText: String? = null
        var sentRequestId: String? = null

        override fun watchRecent(
            roomId: String,
            onPage: (RoomChatPage) -> Unit,
            onError: (String) -> Unit
        ): ListenerRegistration {
            onPage(
                RoomChatPage(
                    messages = listOf(
                        RoomChatMessage(
                            id = "message-1",
                            senderUid = "friend",
                            senderName = "친구",
                            text = "안녕하세요!",
                            createdAtMillis = 1_730_000_000_000L
                        )
                    ),
                    oldestSnapshot = null,
                    hasMore = false,
                    fromCache = false
                )
            )
            return object : ListenerRegistration {
                override fun remove() = Unit
            }
        }

        override suspend fun loadOlder(
            roomId: String,
            cursor: DocumentSnapshot
        ): RoomChatPage = RoomChatPage(emptyList(), null, false, false)

        override suspend fun sendMessage(
            roomId: String,
            requestId: String,
            text: String
        ): String {
            sentText = text
            sentRequestId = requestId
            return "stored-message-id"
        }
    }

    @Test
    fun activeRoomDisplaysStreamAndSendsTextViaInjectedRepository() {
        val repo = FakeChatRepository()
        composeRule.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineRoomChatHost(
                    uid = "me",
                    roomId = "12345678901234567890",
                    roomName = "온라인 채팅 테스트",
                    onBack = {},
                    repository = repo
                )
            }
        }
        composeRule.onNodeWithTag("online-chat-host").assertExists()
        composeRule.onNodeWithText("안녕하세요!").assertExists()
        composeRule.onNodeWithTag("online-chat-input").performTextInput("테스트 전송")
        composeRule.onNodeWithTag("online-chat-send").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) { repo.sentText != null }
        composeRule.runOnIdle {
            assertEquals("테스트 전송", repo.sentText)
            assertEquals(36, repo.sentRequestId?.length)
        }
    }
}
