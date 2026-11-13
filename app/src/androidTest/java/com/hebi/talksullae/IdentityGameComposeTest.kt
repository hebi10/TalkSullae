package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.game.core.GameMode
import com.hebi.talksullae.game.core.GamePhase
import com.hebi.talksullae.game.core.GameSessionView
import com.hebi.talksullae.game.online.IdentityChatMessage
import com.hebi.talksullae.game.online.IdentityChatPage
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import com.hebi.talksullae.game.online.OnlineGameSnapshot
import com.hebi.talksullae.game.online.GameLobbyPlayer
import com.hebi.talksullae.ui.rooms.OnlineIdentityGameHost
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IdentityGameComposeTest {
    @get:Rule val compose = createComposeRule()
    private val roomId = "12345678901234567890"
    private val sessionId = "a".repeat(64)
    private val users = listOf(
        CloudMember("owner","민지","active"),
        CloudMember("player2","지훈","active"),
        CloudMember("player3","수빈","active"),
        CloudMember("player4","도윤","active")
    )

    private inner class FakeGameRepo(
        private var phase: GamePhase, private val failFirstRestore: Boolean = false,
        private val deferFirstRestore: Boolean = false
    ) : OnlineGameSessionRepository {
        @Volatile var restoreAttempts = 0
        private var revision: Long = 1L
        private var onSession: ((OnlineGameSnapshot?) -> Unit)? = null
        private val firstRestoreGate = CompletableDeferred<Unit>()

        fun advanceToPlaying() {
            phase = GamePhase.PLAYING
            revision = 2L
            onSession?.invoke(snapshot())
        }

        fun finishFirstRestore() { firstRestoreGate.complete(Unit) }
        @Volatile var lastAction: Map<String, Any>? = null
        @Volatile var lastMessage: String? = null

        private fun snapshot() = OnlineGameSnapshot(
            session = GameSessionView(
                roomId = roomId, sessionId = sessionId, gameId = "identity",
                rulesVersion = 1, phase = phase, revision = revision, round = 1,
                deadlineEpochMillis = System.currentTimeMillis()+60000
            ),
            mode = GameMode.REALTIME,
            participants = users.map { GameLobbyPlayer(it.uid, true) },
            publicState = mapOf(
                "prompt" to "가장 오래 살아남을 친구는?",
                "options" to listOf("고양이","토끼","부엉이","여우")
            ),
            fromCache = false
        )

        override fun watchSession(
            roomId: String, sessionId: String,
            onResult: (OnlineGameSnapshot?) -> Unit,
            onError: (String) -> Unit
        ): ListenerRegistration {
            onSession = onResult
            onResult(snapshot())
            return listener()
        }

        override fun watchIdentityMessages(
            roomId: String, sessionId: String,
            onPage: (IdentityChatPage) -> Unit,
            onError: (String) -> Unit
        ): ListenerRegistration {
            onPage(IdentityChatPage(
                messages = listOf(
                    IdentityChatMessage("one","토끼","안녕하세요",System.currentTimeMillis())
                ),cursor = null,hasMore = false
            ))
            return listener()
        }

        private fun listener() = object : ListenerRegistration {
            override fun remove() = Unit
        }

        override suspend fun loadOlderIdentityMessages(
            roomId: String, sessionId: String, cursor: DocumentSnapshot
        ): IdentityChatPage = IdentityChatPage(emptyList(),null,false)

        override suspend fun sendIdentityMessage(
            roomId: String, sessionId: String, requestId: String, text: String
        ): String {
            lastMessage = text
            return "stored"
        }

        override suspend fun findActiveSession(roomId: String): String? = sessionId

        override suspend fun createSession(
            roomId: String, gameId: String, mode: String, requestId: String
        ): String = sessionId

        override suspend fun submitAction(
            roomId: String, sessionId: String, gameId: String,
            rulesVersion: Int, revision: Long, requestId: String,
            action: Map<String, Any>
        ): Long {
            lastAction = action
            return revision+1
        }

        override suspend fun restoreMySession(
            roomId: String, sessionId: String
        ): Pair<OnlineGameSnapshot, Map<*, *>?> {
            val captured = snapshot()
            restoreAttempts++
            if (deferFirstRestore && restoreAttempts == 1) firstRestoreGate.await()
            if (failFirstRestore && restoreAttempts == 1) error("Temporary restore failure")
            val personal = if (captured.session.phase == GamePhase.WAITING ||
                captured.session.phase == GamePhase.READY) null
            else mapOf("alias" to "고양이", "submitted" to false)
            return captured to personal
        }
    }

    @Test
    fun fourPersonLobbyShowsReadyAndSubmitsOnlyPlayerIntent() {
        val repo = FakeGameRepo(GamePhase.WAITING)
        compose.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineIdentityGameHost(
                    uid = "owner",roomId = roomId,roomName = "실제 친구방",
                    ownerId = "owner",members = users,onBack = {},
                    repository = repo
                )
            }
        }
        compose.waitUntil(6000) {
            compose.onAllNodesWithTag("identity-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("민지").assertExists()
        compose.onNodeWithText("지훈").assertExists()
        compose.onNodeWithTag("identity-ready").performClick()
        compose.waitUntil(6000) { repo.lastAction != null }
        compose.runOnIdle {
            assertEquals("set_ready",repo.lastAction?.get("kind"))
        }
    }

    @Test
    fun anonymousGameChatShowsAliasNotRealIdentityAndSendsText() {
        val repo = FakeGameRepo(GamePhase.PLAYING)
        compose.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineIdentityGameHost(
                    uid = "owner",roomId = roomId,roomName = "실제 친구방",
                    ownerId = "owner",members = users,onBack = {},
                    repository = repo
                )
            }
        }
        compose.waitUntil(6000) {
            compose.onAllNodesWithText("내 동물 별칭: 고양이").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("토끼").assertExists()
        compose.onNodeWithText("안녕하세요").assertExists()
        compose.onNodeWithTag("identity-chat-input").performTextInput("비밀 대화")
        compose.onNodeWithTag("identity-chat-send").performClick()
        compose.waitUntil(6000) { repo.lastMessage != null }
        compose.runOnIdle { assertEquals("비밀 대화", repo.lastMessage) }
    }
    @Test
    fun finishedGuessPermutationCanBeSwappedBeforeSubmission() {
        val repo = FakeGameRepo(GamePhase.VOTING)
        compose.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineIdentityGameHost(
                    uid = "owner",roomId = roomId,roomName = "실제 친구방",
                    ownerId = "owner",members = users,onBack = {},
                    repository = repo
                )
            }
        }
        compose.waitUntil(6000) {
            compose.onAllNodesWithTag("identity-guess-select-토끼")
                .fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("개인 별칭 확인 중…")
                    .fetchSemanticsNodes().isEmpty()
        }
        fun select(alias: String, candidate: String) {
            compose.onNodeWithTag("identity-guess-select-$alias").performClick()
            compose.onNodeWithTag("identity-guess-option-$alias-$candidate").performClick()
        }
        select("토끼", "player2")
        select("부엉이", "player3")
        select("여우", "player4")
        compose.onNodeWithTag("identity-guess-send").assertIsEnabled()
        select("토끼", "player3")
        compose.onNodeWithTag("identity-guess-select-토끼").assertTextContains("수빈")
        compose.onNodeWithTag("identity-guess-select-부엉이").assertTextContains("지훈")
        compose.onNodeWithTag("identity-guess-send").assertIsEnabled()
    }

    @Test
    fun failedPersonalRestoreCanBeRetriedWithoutReenteringTheGame() {
        val repo = FakeGameRepo(GamePhase.PLAYING, failFirstRestore = true)
        compose.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineIdentityGameHost(
                    uid = "owner",roomId = roomId,roomName = "실제 친구방",
                    ownerId = "owner",members = users,onBack = {},
                    repository = repo
                )
            }
        }
        compose.waitUntil(6000) {
            compose.onAllNodesWithTag("identity-personal-retry")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("identity-chat-input").assertIsNotEnabled()
        compose.onNodeWithTag("identity-personal-retry").performClick()
        compose.waitUntil(6000) {
            compose.onAllNodesWithText("내 동물 별칭: 고양이")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("identity-chat-input").assertIsEnabled()
        compose.runOnIdle { assertEquals(2,repo.restoreAttempts) }
    }

    @Test
    fun playingSnapshotDuringReadyRestoreTriggersFreshPersonalRead() {
        val repo = FakeGameRepo(GamePhase.READY, deferFirstRestore = true)
        compose.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineIdentityGameHost(
                    uid = "owner",roomId = roomId,roomName = "실제 친구방",
                    ownerId = "owner",members = users,onBack = {},
                    repository = repo
                )
            }
        }
        compose.waitUntil(6000) { repo.restoreAttempts >= 1 }
        compose.runOnIdle {
            repo.advanceToPlaying()
            repo.finishFirstRestore()
        }
        compose.waitUntil(6000) {
            repo.restoreAttempts >= 2 &&
                compose.onAllNodesWithText("내 동물 별칭: 고양이")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("identity-chat-input").assertIsEnabled()
        compose.runOnIdle { assertEquals(2,repo.restoreAttempts) }
    }

}
