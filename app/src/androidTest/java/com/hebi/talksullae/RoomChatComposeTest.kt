package com.hebi.talksullae

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class RoomChatComposeTest {
    @get:Rule val composeRule = createComposeRule()

    private class FakeChatRepository(
        private val blockSend: Boolean = false,
        private val startCached: Boolean = false,
        private val tracked: Boolean = false
    ) : RoomChatRepository {
        @Volatile var sentText: String? = null
        @Volatile var sentRequestId: String? = null
        @Volatile var markedRead: String? = null
        val sendGate = CompletableDeferred<Unit>()
        private var onPageCallback: ((RoomChatPage) -> Unit)? = null

        fun emitPage(fromCache: Boolean) {
            onPageCallback?.invoke(RoomChatPage(
                messages = listOf(RoomChatMessage(
                    id = "message-1", senderUid = "friend", senderName = "친구",
                    text = "안녕하세요!", createdAtMillis = 1_730_000_000_000L,
                    trackedForUnread = tracked
                )), oldestSnapshot = null, hasMore = false, fromCache = fromCache))
        }

        override fun watchRecent(
            roomId: String,
            onPage: (RoomChatPage) -> Unit,
            onError: (String) -> Unit
        ): ListenerRegistration {
            onPageCallback = onPage
            emitPage(startCached)
            return object : ListenerRegistration {
                override fun remove() { onPageCallback = null }
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
            sentRequestId = requestId
            sentText = text
            if (blockSend) sendGate.await()
            return "stored-message-id"
        }

        override suspend fun markRead(roomId: String, messageId: String): Boolean {
            markedRead = messageId
            return true
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("online-chat-input").assertTextContains("테스트 전송")
        composeRule.onNodeWithTag("online-chat-send").assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 15000) { repo.sentText != null }
        composeRule.runOnIdle {
            assertEquals("테스트 전송", repo.sentText)
            assertEquals(36, repo.sentRequestId?.length)
        }
    }

    @Test
    fun sendingShowsPendingBubbleAndDoesNotOverwriteNextDraft() {
        val repo = FakeChatRepository(blockSend = true)
        composeRule.setContent {
            TalkSullaeTheme(darkTheme = false) {
                OnlineRoomChatHost(uid = "me", roomId = "12345678901234567890",
                    roomName = "채팅", onBack = {}, repository = repo)
            }
        }
        try {
            composeRule.onNodeWithTag("online-chat-input").performTextInput("첫 메시지")
            composeRule.onNodeWithTag("online-chat-send").assertIsEnabled().performClick()
            composeRule.waitUntil(timeoutMillis = 15000) { repo.sentText != null }
            composeRule.onNodeWithTag("online-chat-pending-message").assertExists().assertTextContains("첫 메시지")
            composeRule.onNodeWithTag("online-chat-send").assertIsNotEnabled()
            composeRule.onNodeWithTag("online-chat-input").assertIsEnabled().performTextInput("다음 메시지")
            repo.sendGate.complete(Unit)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("online-chat-input").assertTextContains("다음 메시지")
            composeRule.onNodeWithTag("online-chat-pending-message").assertDoesNotExist()
            composeRule.onNodeWithTag("online-chat-send").assertIsEnabled()
            assertEquals("첫 메시지", repo.sentText)
        } finally {
            repo.sendGate.complete(Unit)
        }
    }

    @Test
    fun cacheOnlyChatIsHiddenAndOnlyConfirmedVisibleMessageMarksRead() {
        val repo = FakeChatRepository(startCached = true, tracked = true)
        composeRule.setContent {
            TalkSullaeTheme {
                OnlineRoomChatHost(uid = "me", roomId = "12345678901234567890",
                    roomName = "권한 확인", onBack = {}, repository = repo)
            }
        }
        composeRule.onNodeWithText("안녕하세요!").assertDoesNotExist()
        composeRule.onNodeWithTag("online-chat-send").assertIsNotEnabled()
        assertEquals(null, repo.markedRead)
        composeRule.runOnIdle { repo.emitPage(false) }
        composeRule.onNodeWithText("안녕하세요!").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 15000) { repo.markedRead == "message-1" }
        composeRule.runOnIdle { repo.emitPage(true) }
        composeRule.onNodeWithText("안녕하세요!").assertDoesNotExist()
        composeRule.onNodeWithTag("online-chat-send").assertIsNotEnabled()
    }
}
