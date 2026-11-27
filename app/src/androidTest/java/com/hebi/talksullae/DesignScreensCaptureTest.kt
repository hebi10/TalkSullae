package com.hebi.talksullae

import androidx.compose.ui.test.performTextInput
import com.hebi.talksullae.ui.MainNavigation
import com.hebi.talksullae.ui.ProfileScreen
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.design.AppTopBar
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.*
import com.hebi.talksullae.game.core.*
import com.hebi.talksullae.game.online.*
import com.hebi.talksullae.ui.entry.*
import com.hebi.talksullae.ui.rooms.*
import java.security.MessageDigest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesignScreensCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val roomId = "a".repeat(20)
    private val people = listOf(
        CloudMember("owner","민지","active","고양이"),
        CloudMember("friend1","지훈","active","토끼"),
        CloudMember("friend2","수빈","active","부엉이"),
        CloudMember("friend3","도윤","active","여우")
    )
    private val room = CloudRoom(roomId,"금요일 게임방","친구들과 주말에 모이는 방",
        "owner",4,1,true,roomRevision=2,roomIcon="고양이")
    private val code = roomId+"."+"B".repeat(43)
    private val dark get() = InstrumentationRegistry.getArguments().getString("designTheme") != "light"

    @Before fun setup() {
        DesignCaptureIO.requireMode()
        val activity = compose.activity
        compose.runOnUiThread {
            activity.enableEdgeToEdge(
                statusBarStyle = if(dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT,Color.TRANSPARENT),
                navigationBarStyle = if(dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT,Color.TRANSPARENT)
            )
        }
    }
    private fun mount(title:String,mainTab:String?=null,content:@Composable ()->Unit) {
        compose.setContent {
            TalkSullaeTheme(darkTheme=dark) {
                Scaffold(containerColor=androidx.compose.material3.MaterialTheme.colorScheme.background,
                    topBar={if(title.isNotBlank())AppTopBar(title,onBack={})},
                    bottomBar={mainTab?.let { MainNavigation(it,{}) }}) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
                }
            }
        }
    }
    private fun tag(value:String):SemanticsNodeInteraction {
        compose.waitUntil(12000) { compose.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithTag(value)
    }
    private fun label(value:String):SemanticsNodeInteraction {
        compose.waitUntil(12000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(value)
    }
    private fun capture(name:String,anchor:SemanticsNodeInteraction,scroll:Boolean=false) {
        if(scroll && runCatching { anchor.assertIsDisplayed() }.isFailure) anchor.performScrollTo()
        anchor.assertIsDisplayed()
        compose.waitForIdle()
        DesignCaptureIO.save(name)
    }
    private inner class Rooms(private val pendingOnly:Boolean=false) : OnlineRoomsRepository {
        private var current=room
        private var callback:((CloudRoom?)->Unit)?=null
        override fun watchMyRooms(uid:String,onResult:(List<CloudRoomIndex>)->Unit,onError:(String)->Unit):ListenerRegistration {
            val now=System.currentTimeMillis()
            onResult(if(pendingOnly)listOf(CloudRoomIndex(roomId,room.name,"pending"))
                else listOf(CloudRoomIndex(roomId,room.name,"active",now,3,now-120000,"고양이",4,false),
                    CloudRoomIndex("b".repeat(20),"대학 동기","pending")))
            return ListenerRegistration {}
        }
        override fun watchRoom(roomId:String,onResult:(CloudRoom?)->Unit,onError:(String)->Unit):ListenerRegistration {
            callback=onResult;onResult(current);return ListenerRegistration { callback=null }
        }
        override fun watchMembers(roomId:String,onResult:(List<CloudMember>)->Unit,onError:(String)->Unit):ListenerRegistration {
            onResult(people+CloudMember("guest","하늘","pending","판다"));return ListenerRegistration {}
        }
        override suspend fun createRoom(name:String,description:String,approval:Boolean):String=error("capture only")
        override suspend fun createInvitation(roomId:String):Invitation {
            val hash=MessageDigest.getInstance("SHA-256").digest(code.substringAfter('.').toByteArray())
                .joinToString(""){"%02x".format(it.toInt() and 0xff)}
            val expires=System.currentTimeMillis()+3600000
            current=current.copy(activeInviteHash=hash,activeInviteExpiresAtMillis=expires)
            callback?.invoke(current)
            return Invitation(code,expires)
        }
        override suspend fun previewInvitation(code:String)=InvitationPreview(roomId,room.name,room.description,4,true,System.currentTimeMillis()+3600000)
        override suspend fun requestJoin(code:String)=JoinResult(roomId,"pending")
        override suspend fun changeMember(roomId:String,targetUid:String,action:String)=Unit
        override suspend fun leaveRoom(roomId:String)=Unit
    }
    private class Chat(private val cache:Boolean=false):RoomChatRepository {
        override fun watchRecent(roomId:String,onPage:(RoomChatPage)->Unit,onError:(String)->Unit):ListenerRegistration {
            val now=System.currentTimeMillis()
            onPage(RoomChatPage(listOf(
                RoomChatMessage("one","friend1","지훈","오늘 저녁에 게임할 사람?",now-180000,senderAvatar="토끼"),
                RoomChatMessage("two","owner","민지","좋아. 너 누구야?부터 해 보자!",now-120000,senderAvatar="고양이"),
                RoomChatMessage("three","friend2","수빈","나는 준비됐어.",now-60000,senderAvatar="부엉이")
            ),null,false,cache))
            return ListenerRegistration {}
        }
        override suspend fun loadOlder(roomId:String,cursor:DocumentSnapshot)=RoomChatPage(emptyList(),null,false,false)
        override suspend fun sendMessage(roomId:String,requestId:String,text:String):String=error("capture only")
        override suspend fun markRead(roomId:String,messageId:String)=true
    }
    private fun mountRooms(pending:Boolean=false,invite:Boolean=false,showMainNav:Boolean=false) {
        val repo=Rooms(pending)
        mount(if(showMainNav)"" else "톡술래",mainTab=if(showMainNav)"rooms" else null) {
            OnlineRoomsHost("owner","민지",{},{},repo,chatRepository=Chat(),
                incomingInviteCode=if(invite)code else null,incomingInviteDeliveryId=if(invite)1L else 0L,
                showClose=!showMainNav)
        }
    }
    private fun enterManagement() {
        label("입장").performScrollTo().performClick()
        // The compact workspace has a fixed header action, not a scroll child.
        // The expanded workspace may place it in a scrollable side panel.
        val members = tag("live-room-tab-members")
        if (runCatching { members.assertIsDisplayed() }.isFailure) members.performScrollTo()
        members.assertIsDisplayed().assertIsEnabled().performClick()
        tag("room-edit-open")
    }
    @Test fun d01Welcome(){
        mount("톡술래"){WelcomeScreen(true,{},{},allowPreview=false)}
        capture("D01_welcome",tag("welcome-start"),true)
    }
    @Test fun d02Login(){
        mount("로그인"){OnboardingScreen({},onGoogleSignIn={},allowDemo=false)}
        capture("D02_login",tag("onboarding-google-signin"),true)
    }
    @Test fun d03Notices(){
        mount("이용약관 및 개인정보 안내"){
            ServiceNoticeScreen(ServiceNotice("2026-11-25.1","2026-11-25.1",
                "테스트 문구: 친구방과 채팅게임을 안전하게 이용해 주세요.",
                "테스트 문구: 계정·채팅·게임 기록의 처리 목적과 삭제 방법을 확인해 주세요."),
                false,null,{},{},{},"fixture-owner")
        }
        capture("D03_notice",tag("terms-accept"),true)
        capture("D03_notice_bottom",tag("service-notice-continue"),true)
    }
    @Test fun d04Setup(){
        mount("닉네임 설정"){UserProfileEditorScreen(null,true,false,null,{_,_->})}
        capture("D04_profile_setup",tag("profile-nickname"),true)
        capture("D04_profile_setup_bottom",tag("profile-save"),true)
    }
    @Test fun d05Rooms(){
        mountRooms(showMainNav=true)
        capture("D05_rooms",tag("online-room-"+roomId),true)
    }
    @Test fun d06Create(){
        mountRooms()
        tag("online-create-toggle").performScrollTo().performClick()
        label("친구들과 사용할 공간을 만들어 보세요.").assertExists()
        capture("D06_create",tag("online-room-name"),true)
        capture("D06_create_bottom",tag("online-create-submit"),true)
    }
    @Test fun d07Invite(){
        mountRooms(pending=true,invite=true)
        label("가입 승인 필요").assertExists()
        capture("D07_invite_preview",tag("live-invite-confirm"))
    }
    @Test fun d08Pending(){
        mountRooms(pending=true)
        tag("room-membership-detail-"+roomId).assertExists()
        capture("D08_pending",tag("online-room-"+roomId),true)
    }
    @Test fun d09Chat(){
        val chat=Chat()
        mount(""){
            OnlineRoomWorkspace(room,"chat",{},{}) {
                OnlineRoomChatHost("owner",roomId,room.name,{},chat,showHeader=false)
            }
        }
        capture("D09_chat",tag("online-chat-input"))
    }
    @Test fun d09CacheHidden(){
        val chat=Chat(true)
        mount(""){
            OnlineRoomWorkspace(room,"chat",{},{}) {
                OnlineRoomChatHost("owner",roomId,room.name,{},chat,showHeader=false)
            }
        }
        capture("D09_cache_hidden",label("방 접근 권한을 확인할 수 없어 채팅을 숨겼습니다. 연결되면 다시 표시합니다."))
    }
    @Test fun d10Management(){
        mountRooms()
        enterManagement()
        capture("D10_management",tag("room-edit-open"),true)
        label("가입 요청 1명").assertExists()
        label("멤버 목록 4명").assertExists()
        capture("D10_members",label("승인"),true)
        capture("D10_active_members",label("지훈"),true)
        // Fixture ordering is fixed. Verify the named target in the real dialog, and cancel.
        val kick = compose.onAllNodesWithText("강퇴")[0]
        kick.performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        label("지훈님에게 '강퇴'을 적용하시겠습니까? 서버에서 권한을 다시 검증합니다.").assertExists()
        capture("D10_remove_confirm",label("확인"))
        label("취소").performClick()
        val transfer = compose.onAllNodesWithText("방장 위임")[0]
        transfer.performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        label("지훈님에게 '방장 위임'을 적용하시겠습니까? 서버에서 권한을 다시 검증합니다.").assertExists()
        capture("D10_transfer_confirm",label("확인"))
        label("취소").performClick()
        label("지훈").assertExists()
    }
    @Test fun d11InviteManagement(){
        mountRooms()
        enterManagement()
        tag("live-invite-generate").performScrollTo().performClick()
        label("초대 코드 숨김").assertExists()
        compose.onNodeWithText(code).assertDoesNotExist()
        tag("live-invite-reveal").performScrollTo().performClick()
        label(code).assertExists()
        tag("live-invite-reveal").performScrollTo().performClick()
        compose.onNodeWithText(code).assertDoesNotExist()
        capture("D11_invite_manage",label("코드 복사"),true)
        tag("live-invite-revoke").performScrollTo().performClick()
        capture("D11_invite_revoke",label("초대 코드 취소"))
    }
    @Test fun d12Edit(){
        mount("방 관리"){OnlineRoomEditDialog(room,false,null,{}, {_,_,_,_->})}
        capture("D12_room_edit",tag("room-edit-name"))
    }
    @Test fun d12Icon(){
        mount("방 관리"){RoomIconDialog(room,false,null,{}, {_,_->})}
        label("선택: 고양이").assertExists()
        capture("D12_room_icon",tag("room-icon-고양이"),true)
    }
    @Test fun d13Catalog(){
        mount(""){
            OnlineRoomWorkspace(room,"games",{},{}){OnlineGameCatalog(room,{},{})}
        }
        capture("D13_catalog",tag("live-game-card-identity"),true)
        capture("D13_catalog_bottom",tag("live-game-balance"),true)
    }

    private inner class Game(
        private val id:String,private val phase:GamePhase,private val role:String="citizen",
        private val guess:Boolean=false,private val eliminated:Boolean=false,private val submitted:Boolean=false
    ):OnlineGameSessionRepository {
        private val sessionId="c".repeat(64)
        private val deadline=System.currentTimeMillis()+180000L
        private fun snapshot():OnlineGameSnapshot {
            val terminal=phase==GamePhase.FINISHED||phase==GamePhase.REVEAL
            val public:Map<String,Any> = when(id){
                "identity"-> if(terminal)mapOf("publishedResult" to "고양이: 민지\n토끼: 지훈\n부엉이: 수빈\n여우: 도윤")
                    else mapOf("prompt" to "우리 중 여행 계획을 가장 잘 세울 사람은?","options" to listOf("고양이","토끼","부엉이","여우"))
                "liar"-> if(terminal)mapOf("publishedResult" to "라이어: 도윤\n승리: 일반 참가자")
                    else if(phase==GamePhase.VOTING)mapOf("notice" to if(guess)"guess" else "vote",
                        "prompt" to if(guess)"마지막 주제를 추리하세요." else "라이어를 지목해 주세요.",
                        "options" to if(guess)listOf("여행","영화","치킨","커피") else people.map{it.uid})
                    else mapOf("prompt" to "서로 힌트를 주고받으세요.")
                "forbidden"->if(terminal)mapOf("publishedResult" to "생존: 민지, 수빈\n탈락: 지훈",
                    "eliminatedUids" to listOf("friend1"))
                    else mapOf("prompt" to "친구의 금지어를 유도하세요.","eliminatedUids" to if(eliminated)listOf("owner") else emptyList<String>())
                else-> if(terminal)mapOf("publishedResult" to "A: 평생 여름 (2명)\nB: 평생 겨울 (2명)")
                    else mapOf("prompt" to "둘 중 하나를 선택하세요.","options" to listOf("평생 여름","평생 겨울"),
                        "notice" to if(phase==GamePhase.PLAYING)"choose" else "predict")
            }
            return OnlineGameSnapshot(
                session=GameSessionView(roomId,sessionId,id,1,phase,1L,1,if(terminal)null else deadline),
                mode=GameMode.REALTIME,
                participants=people.map{GameLobbyPlayer(it.uid,phase!=GamePhase.WAITING||it.uid=="owner")},
                publicState=public,fromCache=false)
        }
        override fun watchSession(roomId:String,sessionId:String,onResult:(OnlineGameSnapshot?)->Unit,onError:(String)->Unit):ListenerRegistration {
            onResult(snapshot());return ListenerRegistration {}
        }
        override fun watchIdentityMessages(roomId:String,sessionId:String,onPage:(IdentityChatPage)->Unit,onError:(String)->Unit):ListenerRegistration {
            val now=System.currentTimeMillis()
            onPage(IdentityChatPage(listOf(IdentityChatMessage("one","토끼","나는 즉흥 여행이 좋아.",now-90000),
                IdentityChatMessage("two","부엉이","계획이 있어야 편하지!",now-60000),
                IdentityChatMessage("three","여우","나는 둘 다 괜찮아.",now-30000),
                IdentityChatMessage("four","고양이","나는 미리 계획하는 게 좋아!",now-15000)),null,false))
            return ListenerRegistration {}
        }
        override fun watchLiarMessages(roomId:String,sessionId:String,onPage:(LiarChatPage)->Unit,onError:(String)->Unit):ListenerRegistration {
            val now=System.currentTimeMillis()
            onPage(LiarChatPage(listOf(LiarChatMessage("one","friend1","아침에 꼭 필요한 것 같아.",now-60000),
                LiarChatMessage("two","friend2","나는 따뜻한 게 더 좋아.",now-30000)),null,false))
            return ListenerRegistration {}
        }
        override suspend fun loadOlderIdentityMessages(roomId:String,sessionId:String,cursor:DocumentSnapshot)=IdentityChatPage(emptyList(),null,false)
        override suspend fun loadOlderLiarMessages(roomId:String,sessionId:String,cursor:DocumentSnapshot)=LiarChatPage(emptyList(),null,false)
        override suspend fun sendIdentityMessage(roomId:String,sessionId:String,requestId:String,text:String):String=error("capture only")
        override suspend fun findActiveSession(roomId:String):String?=sessionId
        override suspend fun createSession(roomId:String,gameId:String,mode:String,requestId:String)=sessionId
        override suspend fun submitAction(roomId:String,sessionId:String,gameId:String,rulesVersion:Int,revision:Long,requestId:String,action:Map<String,Any>)=revision+1
        override suspend fun restoreMySession(roomId:String,sessionId:String):Pair<OnlineGameSnapshot,Map<*,*>?> =
            snapshot() to when(id){
                "identity"->mapOf("alias" to "고양이","submitted" to submitted)
                "liar"->if(role=="liar")mapOf("role" to role,"submitted" to submitted) else mapOf("role" to role,"topic" to "커피","submitted" to submitted)
                "forbidden"->mapOf("otherWords" to listOf(mapOf("uid" to "friend1","word" to "커피"),mapOf("uid" to "friend2","word" to "여행")),"eliminated" to eliminated)
                else->mapOf("submitted" to submitted,"ownChoice" to if(submitted)"A" else null)
            }
    }
    private fun mountGame(id:String,phase:GamePhase,role:String="citizen",guess:Boolean=false,eliminated:Boolean=false,submitted:Boolean=false){
        val repo=Game(id,phase,role,guess,eliminated,submitted)
        mount(if(id=="identity")"" else "톡술래"){
            when(id){
                "identity"->OnlineIdentityGameHost("owner",roomId,room.name,"owner",people,{},repo)
                "liar"->OnlineLiarGameHost("owner",roomId,room.name,"owner",people,{},repo)
                "forbidden"->OnlineForbiddenGameHost("owner",roomId,room.name,"owner",people,{},repo)
                else->OnlineBalanceGameHost("owner",roomId,room.name,"owner",people,{},repo)
            }
        }
    }
    @Test fun d14Lobby(){mountGame("identity",GamePhase.WAITING);capture("D14_lobby",tag("identity-ready"),true)}
    @Test fun d15IdentityChat(){
        mountGame("identity",GamePhase.PLAYING)
        capture("D15_identity_chat",label("내 동물 별칭: 고양이"),true)
        tag("identity-chat-input").performClick().performTextInput("나는 미리 계획하는 게 좋아!")
        capture("D15_identity_input",tag("identity-chat-input"),true)
    }
    @Test fun d16IdentityGuess(){
        mountGame("identity",GamePhase.VOTING)
        tag("identity-guess-select-토끼").performScrollTo().performClick()
        tag("identity-guess-option-토끼-friend1").performClick()
        tag("identity-guess-select-부엉이").performScrollTo().performClick()
        tag("identity-guess-option-부엉이-friend2").performClick()
        tag("identity-guess-select-여우").performScrollTo().performClick()
        tag("identity-guess-option-여우-friend3").performClick()
        capture("D16_identity_guess",tag("identity-guess-select-토끼"),true)
        capture("D16_identity_guess_bottom",tag("identity-guess-send"),true)
    }
    @Test fun d17IdentityResult(){mountGame("identity",GamePhase.FINISHED);capture("D17_identity_result",label("정체 공개·결과"),true)}
    @Test fun d18Liar(){
        mountGame("liar",GamePhase.PLAYING,role="liar")
        tag("liar-secret-role").assertExists()
        compose.onNodeWithText("비밀 주제:",substring=true).assertDoesNotExist()
        capture("D18_liar_secret",tag("liar-secret-role"),true)
    }
    @Test fun d18Citizen(){
        mountGame("liar",GamePhase.PLAYING)
        label("비밀 주제: 커피").assertExists()
        capture("D18_citizen_secret",tag("liar-secret-role"),true)
        tag("liar-chat-input").performScrollTo().performClick().performTextInput("나는 아침에 마시는 편이야.")
        capture("D18_liar_input",tag("liar-chat-input"),true)
    }
    @Test fun d19LiarVote(){
        mountGame("liar",GamePhase.VOTING)
        tag("liar-vote-friend3").performScrollTo().performClick()
        capture("D19_liar_vote",tag("liar-vote-friend3"),true)
    }
    @Test fun d20LiarGuess(){
        mountGame("liar",GamePhase.VOTING,role="liar",guess=true)
        tag("liar-topic-option-커피").performScrollTo().performClick()
        capture("D20_liar_guess",tag("liar-topic-option-커피"),true)
    }
    @Test fun d21LiarResult(){mountGame("liar",GamePhase.FINISHED);capture("D21_liar_result",tag("liar-result"),true)}
    @Test fun d22Forbidden(){
        mountGame("forbidden",GamePhase.PLAYING)
        label("내 금지어는 공개되지 않습니다.").assertExists()
        compose.onNodeWithTag("forbidden-other-owner").assertDoesNotExist()
        capture("D22_forbidden_chat",tag("forbidden-other-friend1"),true)
        tag("forbidden-chat-input").performScrollTo().performClick().performTextInput("오늘 어떤 계획이 있어?")
        capture("D22_forbidden_input",tag("forbidden-chat-input"),true)
        val send=tag("forbidden-chat-send").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("D22_forbidden_send",send) // No actual message is sent.
    }
    @Test fun d22Eliminated(){mountGame("forbidden",GamePhase.PLAYING,eliminated=true);capture("D22_eliminated",label("금지어를 말해 탈락했습니다. 채팅을 보낼 수 없습니다."),true)}
    @Test fun d23ForbiddenResult(){mountGame("forbidden",GamePhase.FINISHED);capture("D23_forbidden_result",tag("forbidden-result"),true)}
    @Test fun d24BalanceChoice(){
        mountGame("balance",GamePhase.PLAYING)
        tag("balance-choice-A").performScrollTo().performClick()
        tag("balance-question").assertExists()
        capture("D24_balance_choice",tag("balance-choice-A"),true)
    }
    @Test fun d24BalanceSubmitted(){mountGame("balance",GamePhase.PLAYING,submitted=true);capture("D24_balance_submitted",label("선택을 제출했습니다. 결과 공개 전까지 다른 사람의 선택은 비공개입니다."),true)}
    @Test fun d25BalancePredict(){
        mountGame("balance",GamePhase.VOTING)
        tag("balance-predict-submit").assertIsNotEnabled()
        tag("balance-predict-target-friend1").performScrollTo().performClick()
        tag("balance-predict-submit").assertIsNotEnabled()
        tag("balance-predict-choice-A").performScrollTo().performClick()
        label("지훈님은 어떤 선택을 했을까요?").assertExists()
        capture("D25_balance_predict",tag("balance-predict-choice-A"),true)
        val submit=tag("balance-predict-submit").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("D25_balance_submit",submit) // Do not submit a game action.
    }
    @Test fun d26BalanceResult(){mountGame("balance",GamePhase.FINISHED);capture("D26_balance_result",tag("balance-result"),true)}
    private val records=RoomRecordsState("2026-11",false,
        ranking=listOf(SeasonRank("owner",420,5,3),SeasonRank("friend1",360,5,2),SeasonRank("friend2",280,4,1)),
        totalGames=12,totalWins=7,bestStreak=3,achievements=setOf("first_game","wins_5","games_10"),
        recentGames=listOf(RecentGameRecord("identity",110,true,"2026-11"),RecentGameRecord("liar",85,false,"2026-11")),
        events=listOf(RoomRecordEvent("identity","2026-11",4),RoomRecordEvent("balance","2026-11",4)),loading=false)
    private fun mountRecords(state:RoomRecordsState){
        mount(""){
            OnlineRoomWorkspace(room,"records",{},{}) {
                RoomRecordsContent(room.name,state,people.associate{it.uid to it.name},
                    {},{},true,false,{},people.filter{it.uid!="owner"}.associate{it.uid to it.name},{},
                    avatars=people.associate{it.uid to it.avatar}, showHeader=false, ownUid="owner")
            }
        }
    }
    @Test fun d27Ranking(){mountRecords(records);capture("D27_ranking",tag("records-ranked-owner"),true)}
    @Test fun d27Closed(){mountRecords(records.copy(seasonId="2026-10",closed=true,finalResult=SeasonFinalResult(420,1,12)));capture("D27_closed",tag("season-final"),true)}
    @Test fun d28Chronology(){
        mountRecords(records)
        tag("records-chronology-event-0").performScrollTo()
        tag("records-chronology-event-0").assertIsDisplayed()
        capture("D28_chronology",tag("records-chronology-event-0"),true)
        val second=tag("records-chronology-event-1").performScrollTo().assertIsDisplayed()
        capture("D28_chronology_bottom",second)
    }
    @Test fun d28EmptyChronology(){
        mountRecords(records.copy(events=emptyList()))
        val empty=tag("records-chronology-empty")
        label("현재 표시할 정산 기록이 없습니다.").assertExists()
        capture("D28_chronology_empty",empty,true)
    }
    @Test fun d28ErrorChronology(){
        mountRecords(records.copy(events=emptyList(),error="방 연대기를 확인할 수 없습니다."))
        val error=tag("records-chronology-empty")
        label("방 연대기를 표시할 수 없습니다. 연결 상태를 확인해 주세요.").assertExists()
        capture("D28_chronology_error",error,true)
    }
    private val gameStats=listOf("identity","liar","forbidden","balance").associateWith{FriendGameStats(5,3)}
    private val pair=FriendComparison(12,5,4,3,mapOf("identity" to 4L,"liar" to 3L,"forbidden" to 2L,"balance" to 3L),4,3,4,2,3,2)
    @Test fun d29Friend(){
        val detail=mutableStateOf(false)
        mount(""){
            OnlineRoomWorkspace(room,"records",{},{}) {
                RoomRecordsContent(room.name,records,people.associate{it.uid to it.name},
                    {},{},true,false,{},avatars=people.associate{it.uid to it.avatar}, showHeader=false, ownUid="owner")
            }
            FriendRecordsDialog("지훈",FriendRecords(20,12,3,1200,gameStats,listOf("first_game","wins_5"),pair,20,false,comparisonLoaded=detail.value),false,null,{},{},{detail.value=true},avatar="토끼")
        }
        capture("D29_friend_summary",tag("friend-records-dialog"))
        tag("friend-load-comparison").performScrollTo().performClick()
        capture("D29_friend_comparison",tag("friend-shared-games"),true)
        capture("D29_friend_rates",label("밸런스 선택 일치율"),true)
    }
    @Test fun d30Career(){
        val value=MyCareer(3,28,16,1600,4,listOf("first_game","games_10","wins_5"),
            listOf("identity","liar","forbidden","balance").associateWith{CareerGame(7,4)},5,true,null)
        val repo=object:CareerRepository{override suspend fun getMyCareer()=value}
        mount("",mainTab="profile"){
            ProfileScreen(true,{},{},"민지",actualProfile=UserProfile("민지","고양이"),
                actualUid="owner",onEditProfile={},careerRepository=repo)
        }
        capture("D30_career_section",tag("career-level"),true)
        capture("D30_career_bottom",tag("my-career-refresh").performScrollTo().assertIsDisplayed().assertIsEnabled())
    }
    @Test fun d30CareerPartial(){
        val value=MyCareer(3,28,16,1600,4,listOf("first_game","games_10","wins_5"),
            listOf("identity","liar","forbidden","balance").associateWith{CareerGame(7,4)},5,true,"fixture-next-page")
        val repo=object:CareerRepository{override suspend fun getMyCareer()=value}
        mount("",mainTab="profile"){
            ProfileScreen(true,{},{}, "민지",actualProfile=UserProfile("민지","고양이"),
                actualUid="owner",onEditProfile={},careerRepository=repo)
        }
        label("아직 모든 기록을 조회하지 않았습니다. 전체 전적과 레벨은 추가 조회 후 확인할 수 있습니다.").assertExists()
        compose.onNodeWithTag("career-level").assertDoesNotExist()
        capture("D30_career_partial",tag("my-career-more").performScrollTo().assertIsDisplayed().assertIsEnabled())
    }
    @Test fun d31ProfileEdit(){
        mount("프로필 편집"){UserProfileEditorScreen(UserProfile("민지","고양이"),false,false,null,{_,_->})}
        capture("D31_profile_edit",tag("profile-nickname"),true)
    }
}
