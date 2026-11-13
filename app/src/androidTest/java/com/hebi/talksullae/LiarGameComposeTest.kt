package com.hebi.talksullae

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.game.core.GameMode
import com.hebi.talksullae.game.core.GamePhase
import com.hebi.talksullae.game.core.GameSessionView
import com.hebi.talksullae.game.online.GameLobbyPlayer
import com.hebi.talksullae.game.online.IdentityChatPage
import com.hebi.talksullae.game.online.LiarChatMessage
import com.hebi.talksullae.game.online.LiarChatPage
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import com.hebi.talksullae.game.online.OnlineGameSnapshot
import com.hebi.talksullae.ui.rooms.OnlineLiarGameHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LiarGameComposeTest {
    @get:Rule val compose = createComposeRule()
    private val roomId = "12345678901234567890"
    private val sessionId = "b".repeat(64)
    private val people = listOf(
        CloudMember("owner","민지","active"),
        CloudMember("friend1","지훈","active"),
        CloudMember("friend2","수빈","active"),
        CloudMember("friend3","도윤","active")
    )

    private inner class FakeRepo(
        private val phase: GamePhase,
        private val role: String = "citizen",
        private val notice: String = "vote",
        private val activeGame: String = "liar"
    ) : OnlineGameSessionRepository {
        @Volatile var lastAction: Map<String, Any>? = null
        @Volatile var advanceRequested: Boolean = false
        @Volatile var lastText: String? = null

        private fun snapshot() = OnlineGameSnapshot(
            session = GameSessionView(
                roomId = roomId, sessionId = sessionId, gameId = activeGame,
                rulesVersion = 1, phase = phase, revision = 1L, round = 1,
                deadlineEpochMillis = System.currentTimeMillis()+60000
            ),
            mode = GameMode.REALTIME,
            participants = people.map { GameLobbyPlayer(it.uid,phase != GamePhase.WAITING) },
            publicState = when(phase) {
                GamePhase.VOTING -> mapOf(
                    "notice" to notice,
                    "prompt" to "라이어를 지목해 주세요.",
                    "options" to if(notice == "guess")
                        listOf("여행","영화","치킨","커피")
                    else people.map { it.uid }
                )
                GamePhase.FINISHED -> mapOf("publishedResult" to "라이어: 도윤\n승리: 일반 참가자")
                else -> mapOf("prompt" to "서로 힌트를 주고받으세요.")
            },
            fromCache = false
        )

        private fun listener() = object : ListenerRegistration {
            override fun remove() = Unit
        }

        override fun watchSession(
            roomId: String, sessionId: String,
            onResult: (OnlineGameSnapshot?) -> Unit, onError: (String) -> Unit
        ): ListenerRegistration {
            onResult(snapshot())
            return listener()
        }

        override fun watchIdentityMessages(
            roomId: String,sessionId: String,
            onPage: (IdentityChatPage) -> Unit,onError: (String) -> Unit
        ): ListenerRegistration = listener()

        override suspend fun loadOlderIdentityMessages(
            roomId: String,sessionId: String,cursor: DocumentSnapshot
        ): IdentityChatPage = IdentityChatPage(emptyList(),null,false)

        override suspend fun sendIdentityMessage(
            roomId: String,sessionId: String,requestId: String,text: String
        ): String = error("wrong game")

        override fun watchLiarMessages(
            roomId: String,sessionId: String,
            onPage: (LiarChatPage) -> Unit,onError: (String) -> Unit
        ): ListenerRegistration {
            onPage(LiarChatPage(
                messages = listOf(LiarChatMessage("one","friend1","안녕하세요",System.currentTimeMillis())),
                cursor = null,hasMore = false
            ))
            return listener()
        }

        override suspend fun loadOlderLiarMessages(
            roomId: String,sessionId: String,cursor: DocumentSnapshot
        ): LiarChatPage = LiarChatPage(emptyList(),null,false)

        override suspend fun sendLiarMessage(
            roomId: String,sessionId: String,requestId: String,text: String
        ): String {
            lastText = text
            return "saved"
        }

        override suspend fun findActiveSession(roomId: String): String? = sessionId
        override suspend fun createSession(
            roomId: String,gameId: String,mode: String,requestId: String
        ): String = sessionId

        override suspend fun submitAction(
            roomId: String,sessionId: String,gameId: String,
            rulesVersion: Int,revision: Long,requestId: String,
            action: Map<String, Any>
        ): Long {
            if(action["kind"] == "advance") advanceRequested = true
            else lastAction = action
            return revision + 1
        }

        override suspend fun restoreMySession(
            roomId: String,sessionId: String
        ): Pair<OnlineGameSnapshot,Map<*, *>?> = snapshot() to when(role) {
            "liar" -> mapOf("role" to "liar","submitted" to false)
            else -> mapOf("role" to "citizen","topic" to "커피","submitted" to false)
        }
    }

    private fun mount(repo: FakeRepo) {
        compose.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineLiarGameHost(
                    uid = "owner",roomId = roomId,roomName = "친구 모임",
                    ownerId = "owner",members = people,onBack = {},
                    repository = repo
                )
            }
        }
    }

    @Test
    fun lobbySupportsReadyActionWithoutPrivateRole() {
        val repo = FakeRepo(GamePhase.WAITING)
        mount(repo)
        compose.waitUntil(8000) {
            compose.onAllNodesWithTag("liar-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("liar-ready").performClick()
        compose.waitUntil(8000) { repo.lastAction != null }
        compose.runOnIdle {
            assertEquals("set_ready",repo.lastAction?.get("kind"))
            assertEquals(true,repo.lastAction?.get("ready"))
        }
    }

    @Test
    fun liarCanChatWithoutSeeingThePrivateTopic() {
        val repo = FakeRepo(GamePhase.PLAYING,role = "liar")
        mount(repo)
        compose.waitUntil(8000) {
            compose.onAllNodesWithText("내 역할: 라이어",substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("내 역할: 라이어",substring = true).assertExists()
        compose.onNodeWithTag("liar-chat-input").performTextInput("비밀을 추리해요")
        compose.onNodeWithTag("liar-chat-send").assertIsEnabled().performClick()
        compose.waitUntil(8000) { repo.lastText != null }
        compose.runOnIdle { assertEquals("비밀을 추리해요",repo.lastText) }
        compose.onNodeWithText("비밀 주제: 커피",substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun citizenSeesOnlyTheirTopicAndCanVoteForAnotherMember() {
        val repo = FakeRepo(GamePhase.VOTING,role = "citizen")
        mount(repo)
        compose.waitUntil(8000) {
            compose.onAllNodesWithTag("liar-vote-friend3")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("비밀 주제: 커피",substring = true).assertExists()
        compose.onNodeWithTag("liar-vote-friend3").performClick()
        compose.onNodeWithTag("liar-vote-send").assertIsEnabled().performClick()
        compose.waitUntil(8000) { repo.lastAction != null }
        compose.runOnIdle {
            assertEquals("submit",repo.lastAction?.get("kind"))
            assertEquals("friend3",repo.lastAction?.get("choice"))
        }
        compose.waitUntil(8000) { repo.advanceRequested }
        compose.runOnIdle {
            assertEquals(true,repo.advanceRequested)
        }
    }

    @Test
    fun onlyLiarCanSubmitTheFinalTopicGuess() {
        val liarRepo = FakeRepo(GamePhase.VOTING,role = "liar",notice = "guess")
        mount(liarRepo)
        compose.waitUntil(8000) {
            compose.onAllNodesWithTag("liar-topic-option-커피")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("liar-topic-option-커피").performClick()
        compose.onNodeWithTag("liar-topic-send").assertIsEnabled().performClick()
        compose.waitUntil(8000) { liarRepo.lastAction != null }
        compose.runOnIdle { assertEquals("커피",liarRepo.lastAction?.get("choice")) }
        compose.waitUntil(8000) { liarRepo.advanceRequested }
    }

    @Test
    fun anotherGameInTheSameRoomDoesNotShowLiarControls() {
        val repo = FakeRepo(GamePhase.PLAYING,activeGame = "identity")
        mount(repo)
        compose.waitUntil(8000) {
            compose.onAllNodesWithText("다른 게임이 진행 중입니다.",substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0,compose.onAllNodesWithTag("liar-chat-send").fetchSemanticsNodes().size)
        assertEquals(0,compose.onAllNodesWithTag("liar-create").fetchSemanticsNodes().size)
    }
}
