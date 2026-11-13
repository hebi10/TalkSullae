package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import com.hebi.talksullae.game.online.LiarChatPage
import com.hebi.talksullae.game.online.LiarChatMessage
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import com.hebi.talksullae.game.online.OnlineGameSnapshot
import com.hebi.talksullae.ui.rooms.OnlineForbiddenGameHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ForbiddenGameComposeTest {
    @get:Rule val compose=createComposeRule()
    private val roomId="12345678901234567890"
    private val sessionId="c".repeat(64)
    private val users=listOf(
        CloudMember("owner","민지","active"),
        CloudMember("friend1","지훈","active"),
        CloudMember("friend2","수빈","active")
    )
    private inner class Fake(private val phase:GamePhase,
        private val activeGame:String="forbidden"):OnlineGameSessionRepository {
        @Volatile var lastAction:Map<String,Any>?=null
        @Volatile var lastText:String?=null
        private fun listener()=object:ListenerRegistration{override fun remove()=Unit}
        private fun snapshot()=OnlineGameSnapshot(
            session=GameSessionView(roomId,sessionId,activeGame,1,phase,1L,1,
                System.currentTimeMillis()+60000),
            mode=GameMode.REALTIME,
            participants=users.map{GameLobbyPlayer(it.uid,phase!=GamePhase.WAITING)},
            publicState=mapOf(
                "prompt" to "친구의 금지어를 유도하세요.",
                "eliminatedUids" to emptyList<String>()
            ),
            fromCache=false
        )
        override fun watchSession(roomId:String,sessionId:String,
            onResult:(OnlineGameSnapshot?)->Unit,onError:(String)->Unit):ListenerRegistration{
            onResult(snapshot())
            return listener()
        }
        override fun watchIdentityMessages(roomId:String,sessionId:String,
            onPage:(IdentityChatPage)->Unit,onError:(String)->Unit):ListenerRegistration=listener()
        override suspend fun loadOlderIdentityMessages(roomId:String,sessionId:String,
            cursor:DocumentSnapshot):IdentityChatPage=IdentityChatPage(emptyList(),null,false)
        override suspend fun sendIdentityMessage(roomId:String,sessionId:String,
            requestId:String,text:String):String=error("wrong game")
        override fun watchForbiddenMessages(roomId:String,sessionId:String,
            onPage:(LiarChatPage)->Unit,onError:(String)->Unit):ListenerRegistration{
            onPage(LiarChatPage(listOf(
                LiarChatMessage("msg1","friend1","안녕하세요",System.currentTimeMillis())
            ),null,false))
            return listener()
        }
        override suspend fun loadOlderForbiddenMessages(roomId:String,sessionId:String,
            cursor:DocumentSnapshot):LiarChatPage=LiarChatPage(emptyList(),null,false)
        override suspend fun sendForbiddenMessage(roomId:String,sessionId:String,
            requestId:String,text:String):String{
            lastText=text
            return "saved"
        }
        override suspend fun findActiveSession(roomId:String):String?=sessionId
        override suspend fun createSession(roomId:String,gameId:String,
            mode:String,requestId:String):String=sessionId
        override suspend fun submitAction(roomId:String,sessionId:String,gameId:String,
            rulesVersion:Int,revision:Long,requestId:String,
            action:Map<String,Any>):Long{
            lastAction=action
            return revision+1
        }
        override suspend fun restoreMySession(roomId:String,sessionId:String)
            :Pair<OnlineGameSnapshot,Map<*, *>?> =snapshot() to mapOf(
                "otherWords" to listOf(
                    mapOf("uid" to "friend1","word" to "커피"),
                    mapOf("uid" to "friend2","word" to "여행")
                ),
                "eliminated" to false
            )
    }
    private fun mount(repo:Fake){
        compose.setContent{
            TalkSullaeTheme(darkTheme=true){
                OnlineForbiddenGameHost(
                    uid="owner",roomId=roomId,roomName="실제 친구방",
                    ownerId="owner",members=users,onBack={},repository=repo
                )
            }
        }
    }
    @Test fun lobbyReadinessIsServerSubmitted(){
        val repo=Fake(GamePhase.WAITING)
        mount(repo)
        compose.waitUntil(8000){
            compose.onAllNodesWithTag("forbidden-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("forbidden-ready").performClick()
        compose.waitUntil(8000){repo.lastAction!=null}
        compose.runOnIdle{
            assertEquals("set_ready",repo.lastAction?.get("kind"))
        }
    }
    @Test fun onlyOtherPlayersForbiddenWordsAppearAndChatUsesRealSender(){
        val repo=Fake(GamePhase.PLAYING)
        mount(repo)
        compose.waitUntil(8000){
            compose.onAllNodesWithText("지훈 · 커피").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0,compose.onAllNodesWithText("치킨").fetchSemanticsNodes().size)
        compose.onNodeWithTag("forbidden-chat-input").performTextInput("안전한 메시지")
        compose.onNodeWithTag("forbidden-chat-send").performClick()
        compose.waitUntil(8000){repo.lastText!=null}
        compose.runOnIdle{assertEquals("안전한 메시지",repo.lastText)}
    }
    @Test fun otherActiveGameCannotControlForbiddenGame(){
        val repo=Fake(GamePhase.PLAYING,activeGame="identity")
        mount(repo)
        compose.waitUntil(8000){
            compose.onAllNodesWithText("다른 게임이 진행 중입니다.",substring=true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0,compose.onAllNodesWithTag("forbidden-chat-send").fetchSemanticsNodes().size)
        assertEquals(0,compose.onAllNodesWithTag("forbidden-create").fetchSemanticsNodes().size)
    }
}
