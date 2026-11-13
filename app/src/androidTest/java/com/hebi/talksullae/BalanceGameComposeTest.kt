package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.game.core.GameMode
import com.hebi.talksullae.game.core.GamePhase
import com.hebi.talksullae.game.core.GameSessionView
import com.hebi.talksullae.game.online.GameLobbyPlayer
import com.hebi.talksullae.game.online.IdentityChatPage
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import com.hebi.talksullae.game.online.OnlineGameSnapshot
import com.hebi.talksullae.ui.rooms.OnlineBalanceGameHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BalanceGameComposeTest {
    @get:Rule val compose=createComposeRule()
    private val roomId="12345678901234567890"
    private val sessionId="d".repeat(64)
    private val people=listOf(
        CloudMember("owner","민지","active"),
        CloudMember("friend","지훈","active")
    )
    private inner class Fake(private val phase:GamePhase,
        private val gameId:String="balance",private val submitted:Boolean=false
    ):OnlineGameSessionRepository {
        @Volatile var lastAction:Map<String,Any>?=null
        @Volatile var createdMode:String?=null
        private fun listener()=object:ListenerRegistration{override fun remove()=Unit}
        private fun snapshot()=OnlineGameSnapshot(
            session=GameSessionView(roomId,sessionId,gameId,1,phase,1L,1,
                System.currentTimeMillis()+60000),
            mode=GameMode.REALTIME,
            participants=people.map{GameLobbyPlayer(it.uid,phase!=GamePhase.WAITING)},
            publicState=mapOf("options" to listOf("평생 여름","평생 겨울"),
                "notice" to if(phase==GamePhase.PLAYING)"choose" else "predict",
                "prompt" to "둘 중 하나를 선택하세요.",
                "publishedResult" to "A: 평생 여름 (1명)"),
            fromCache=false
        )
        override fun watchSession(roomId:String,sessionId:String,
            onResult:(OnlineGameSnapshot?)->Unit,onError:(String)->Unit):ListenerRegistration {
            onResult(snapshot())
            return listener()
        }
        override fun watchIdentityMessages(roomId:String,sessionId:String,
            onPage:(IdentityChatPage)->Unit,onError:(String)->Unit):ListenerRegistration=listener()
        override suspend fun loadOlderIdentityMessages(roomId:String,sessionId:String,
            cursor:DocumentSnapshot):IdentityChatPage=IdentityChatPage(emptyList(),null,false)
        override suspend fun sendIdentityMessage(roomId:String,sessionId:String,
            requestId:String,text:String):String=error("not identity")
        override suspend fun findActiveSession(roomId:String):String? =
            if(phase==GamePhase.FINISHED)null else sessionId
        override suspend fun findRecentCompletedSession(roomId:String,gameId:String):String? =
            if(phase==GamePhase.FINISHED && gameId=="balance")sessionId else null
        override suspend fun createSession(roomId:String,gameId:String,
            mode:String,requestId:String):String {
            createdMode=mode
            return sessionId
        }
        override suspend fun submitAction(roomId:String,sessionId:String,
            gameId:String,rulesVersion:Int,revision:Long,requestId:String,
            action:Map<String,Any>):Long {
            if(action["kind"]!="advance")lastAction=action
            return revision+1
        }
        override suspend fun restoreMySession(roomId:String,sessionId:String)
            :Pair<OnlineGameSnapshot,Map<*, *>?> =snapshot() to mapOf(
                "submitted" to submitted,"ownChoice" to if(submitted)"A" else null
            )
    }
    private fun mount(repo:Fake){
        compose.setContent {
            TalkSullaeTheme(darkTheme=true){
                OnlineBalanceGameHost(uid="owner",roomId=roomId,roomName="친구 모임",
                    ownerId="owner",members=people,onBack={},repository=repo)
            }
        }
    }
    @Test fun ownABSelectionUsesOnlyServerPayload(){
        val repo=Fake(GamePhase.PLAYING)
        mount(repo)
        compose.waitUntil(8000){
            compose.onAllNodesWithTag("balance-choice-A").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("balance-choice-A").performClick()
        compose.onNodeWithTag("balance-own-submit").performClick()
        compose.waitUntil(8000){repo.lastAction!=null}
        compose.runOnIdle{
            assertEquals("submit",repo.lastAction?.get("kind"))
            assertEquals("A",repo.lastAction?.get("choice"))
        }
    }
    @Test fun predictionTargetsAnotherPlayerAndABChoice(){
        val repo=Fake(GamePhase.VOTING)
        mount(repo)
        compose.waitUntil(8000){
            compose.onAllNodesWithTag("balance-predict-target-friend").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("balance-predict-target-friend").performClick()
        compose.onNodeWithTag("balance-predict-choice-B").performClick()
        compose.onNodeWithTag("balance-predict-submit").performClick()
        compose.waitUntil(8000){repo.lastAction!=null}
        compose.runOnIdle{
            assertEquals("submit",repo.lastAction?.get("kind"))
            assertEquals(mapOf("targetUid" to "friend","prediction" to "B"),
                repo.lastAction?.get("choice"))
        }
    }
    @Test fun otherGameCannotShowBalanceControls(){
        val repo=Fake(GamePhase.PLAYING,gameId="identity")
        mount(repo)
        compose.waitUntil(8000){
            compose.onAllNodesWithText("다른 게임이 진행 중입니다.",substring=true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0,compose.onAllNodesWithTag("balance-own-submit").fetchSemanticsNodes().size)
    }    @Test fun completedAsyncSessionCanBeReopenedAndShowsFinalResults(){
        val repo=Fake(GamePhase.FINISHED)
        mount(repo)
        compose.waitUntil(8000){
            compose.onAllNodesWithTag("balance-result").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("A: 평생 여름 (1명)",substring=true).assertExists()
        compose.onNodeWithTag("balance-create-next").assertExists()
        compose.onNodeWithTag("balance-next-refresh").assertExists()
    }

}
