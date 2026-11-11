Warning: truncated output (original token count: 10495)
Total output lines: 953

package com.hebi.talksullae.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.data.DemoRoomMembers
import com.hebi.talksullae.data.RoomDraft
import com.hebi.talksullae.data.RoomPreview
import com.hebi.talksullae.data.RoomPreviewMode
import com.hebi.talksullae.ui.entry.CreateRoomScreen
import com.hebi.talksullae.ui.entry.OnboardingScreen
import com.hebi.talksullae.ui.entry.WelcomeScreen
import com.hebi.talksullae.ui.rooms.InviteScreen
import com.hebi.talksullae.ui.rooms.MembersScreen
import com.hebi.talksullae.data.DemoCatalog
import com.hebi.talksullae.data.GamePreview
import com.hebi.talksullae.data.MessagePreview
import com.hebi.talksullae.design.AppTopBar
import com.hebi.talksullae.design.CenteredInfo
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.GameCard
import com.hebi.talksullae.design.MessageBubble
import com.hebi.talksullae.design.PreviewNotice
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.design.RoomCard
import com.hebi.talksullae.design.SectionTitle
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.FirebaseBridge
import com.hebi.talksullae.firebase.GoogleLogin
import com.hebi.talksullae.ui.rooms.OnlineRoomsHost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

private const val Welcome = "welcome"
private const val Onboarding = "onboarding"
private const val Members = "members"
private const val Online = "online"
private const val Main = "main"
private const val Room = "room"
private const val Game = "game"
private const val Create = "create"
private const val Invite = "invite"
private const val Settings = "settings"

private data class NavItem(val id: String, val title: String, val icon: ImageVector)

@Composable
fun TalkSullaeApp(
    initialDarkTheme: Boolean = true,
    onDarkThemeChanged: (Boolean) -> Unit = {}
) {
    var isDark by rememberSaveable { mutableStateOf(initialDarkTheme) }
    SideEffect { onDarkThemeChanged(isDark) }
    val context = LocalContext.current
    val activity = context as? Activity
    val configured = remember(context) { FirebaseBridge.isReady(context) }
    val auth = remember(configured) { if (configured) FirebaseAuth.getInstance() else null }
    var signedUser by remember { mutableStateOf<FirebaseUser?>(auth?.currentUser) }
    var loginBusy by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { signedUser = it.currentUser }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }
    var route by rememberSaveable { mutableStateOf(Welcome) }
    var mainTab by rememberSaveable { mutableStateOf("rooms") }
    var roomTab by rememberSaveable { mutableStateOf("chat") }
    var gameId by rememberSaveable { mutableStateOf("identity") }
    var gamePhase by rememberSaveable { mutableStateOf("lobby") }
    var selectedRoom by rememberSaveable { mutableStateOf("friday") }
    var chatInput by rememberSaveable { mutableStateOf("") }
    var previewNickname by rememberSaveable { mutableStateOf("게스트") }
    var pendingInvitePreview by rememberSaveable { mutableStateOf(false) }
    val previewRooms = remember { mutableStateListOf<RoomPreview>().apply { addAll(DemoCatalog.rooms) } }
    val kickedMembers = remember { mutableStateMapOf<String, Set<String>>() }
    val ownerOverrides = remember { mutableStateMapOf<String, String>() }
    val roomMembers = DemoRoomMembers.initialMembers(selectedRoom).filterNot {
        it.id in (kickedMembers[selectedRoom] ?: emptySet())
    }
    val ownerId = ownerOverrides[selectedRoom] ?: DemoRoomMembers.initialOwner(selectedRoom)
    val isRoomOwner = ownerId == DemoRoomMembers.MY_ID
    val localMessages = remember { mutableStateListOf<MessagePreview>() }

    val openRoom: (String) -> Unit = {
        selectedRoom = it
        roomTab = "chat"
        route = Room
    }
    val goBack: () -> Unit = {
        when (route) {
            Onboarding -> route = Welcome
            Create, Settings -> route = Main
            Invite -> {
                route = if (pendingInvitePreview) Main else Room
                pendingInvitePreview = false
            }
            Members, Game -> route = Room
            Online -> route = Main
            else -> route = Main
        }
    }
    BackHandler(enabled = route != Main && route != Welcome) { goBack() }

    val currentRoom = previewRooms.firstOrNull { it.id == selectedRoom } ?: previewRooms.first()
    val selectedGame = DemoCatalog.games.firstOrNull { it.id == gameId } ?: DemoCatalog.games.first()
    val editingRoomChat = route == Room && roomTab == "chat" &&
        WindowInsets.ime.getBottom(LocalDensity.current) > 0

    TalkSullaeTheme(darkTheme = isDark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AppTopBar(
                    title = when (route) {
                        Welcome -> "톡술래"
                        Onboarding -> "닉네임 · 아바타"
                        Main -> "톡술래"
                        Room, Invite -> currentRoom.name
                        Members -> "멤버 관리"
                        Online -> "온라인 방"
                        Game -> selectedGame.name
                        Create -> "새 방 만들기"
                        Settings -> "설정"
                        else -> "톡술래"
                    },
                    subtitle = when (route) {
                        Room -> if (editingRoomChat) null else "UI 미리보기"
                        Game -> "게임 흐름 미리보기 · 실제 플레이 아님"
                        Online -> "초대 코드 · 멤버 관리"
                        Welcome, Onboarding -> null
                        else -> "채팅으로 즐기는 추리 게임 · 데모"
                    },
                    onBack = if (route == Main || route == Welcome) null else goBack
                )
            },
            bottomBar = {
                if (route == Main) {
                    MainNavigation(current = mainTab, onSelect = { mainTab = it })
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (route) {
                    Welcome -> WelcomeScreen(
                        onlineConfigured = configured && activity != null,
                        onContinue = { pendingInvitePreview = false; route = Onboarding },
                        onInvitePreview = { pendingInvitePreview = true; route = Onboarding }
                    )
                    Onboarding -> OnboardingScreen(
                        onContinueDemo = {
                            previewNickname = it
                            if (pendingInvitePreview) {
                                selectedRoom = "friends"
                                route = Invite
                            } else {
                                route = Main
                            }
                        },
                        onGoogleSignIn = if (configured && activity != null) ({
                            scope.launch {
                                loginBusy = true
                                loginError = null
                                try {
                                    GoogleLogin(activity).signIn()
                                    route = Main
                                    mainTab = "rooms"
                                } catch (e: Exception) {
                                    loginError = e.localizedMessage ?: "Google 로그인에 실패했습니다."
                                } finally {
                                    loginBusy = false
                                }
                            }
                        }) else null,
                        signInBusy = loginBusy,
                        authError = loginError
                    )
                    Main -> when (mainTab) {
                        "rooms" -> RoomsScreen(
                            rooms = previewRooms,
                            openRoom = openRoom,
                            onCreate = { route = Create },
                            onOpenOnline = if (signedUser != null) ({ route = Online }) else null,
                            onInvitePreview = {
                                selectedRoom = "friends"
                                pendingInvitePreview = true
                                route = Invite
                            }
                        )
                        "records" -> RankingsScreen(title = "내 기록")
                        else -> ProfileScreen(
                            darkTheme = isDark,
                            onThemeChange = { isDark = it },
                            onSettings = { route = Settings },
                            nickname = previewNickname
                        )
                    }
                    Room -> DemoRoomLayout(
                        currentTab = roomTab,
                        onSelectTab = { roomTab = it },
                        memberCount = roomMembers.size,
                        onMembers = { route = Members },
                        onInvite = if (isRoomOwner) ({ pendingInvitePreview = false; route = Invite }) else null,
                        editingChat = editingRoomChat
                    ) { scrollableActions ->
                        when (roomTab) {
                            "chat" -> RoomChatScreen(
                                messages = DemoCatalog.roomMessages + localMessages,
                                editingChat = editingRoomChat,
                                scrollableActions = scrollableActions,
                                input = chatInput,
                                onInput = { chatInput = it },
                                onSend = {
                                    if (chatInput.isNotBlank()) {
                                        localMessages.add(MessagePreview("나", chatInput.trim(), true))
                                        chatInput = ""
                                    }
                                },
                                onGame = {
                                    gameId = "identity"
                                    gamePhase = "lobby"
                                    route = Game
                                }
                            )
                            "games" -> GamesScreen { game ->
                                gameId = game.id
                                gamePhase = "lobby"
                                route = Game
                            }
                            else -> RankingsScreen(title = "금요일 게임방 · 시즌 3")
                        }
                    }
                    Game -> GamePreviewScreen(
                        game = selectedGame,
                        phase = gamePhase,
                        onNext = { gamePhase = it },
                        onRoom = {
                            roomTab = "chat"
                            route = Room
                        }
                    )
                    Online -> {
                        val user = signedUser
                        if (user != null) {
                            OnlineRoomsHost(
                                uid = user.uid,
                                accountName = user.displayName ?: "Google 계정",
                                onClose = { route = Main },
                                onSignOut = {
                                    if (activity != null) {
                                        scope.launch {
                                            GoogleLogin(activity).signOut()
                                            route = Main
                                        }
                                    }
                                }
                            )
                        } else {
                            UnavailableScreen(
                                title = "로그인 필요",
                                description = "Google 계정으로 로그인 후 온라인 방을 이용할 수 있습니다.",
                                onBack = goBack
                            )
                        }
                    }
                    Create -> CreateRoomScreen(
                        onCreatePreview = { draft ->
                            val roomId = "local-${previewRooms.size + 1}"
                            previewRooms.add(
                                RoomPreview(
                                    id = roomId,
                                    name = draft.name,
                                    memberCount = 1,
                                    recentMessage = "이 기기 전용 미리보기 방",
                                    unreadCount = 0
                                )
                            )
                            selectedRoom = roomId
                            roomTab = "chat"
                            route = Room
                        },
                        onCancel = { route = Main }
                    )
                    Invite -> InviteScreen(
                        roomName = currentRoom.name,
                        isOwner = isRoomOwner,
  …1495 tokens truncated…on(tab.icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                tab.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RoomsScreen(
    rooms: List<RoomPreview>,
    openRoom: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenOnline: (() -> Unit)? = null,
    onInvitePreview: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(RoomPreviewMode.CONTENT) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("room-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PreviewNotice("아래 목록은 로컬 데모 방입니다. 로그인한 계정의 온라인 방은 별도로 관리합니다.")
            if (onOpenOnline != null) {
                PrimaryAction(
                    "내 온라인 방 관리 (Firebase)",
                    onClick = onOpenOnline,
                    modifier = Modifier.fillMaxWidth().testTag("rooms-open-online")
                )
            }
            SectionTitle("내 방 · 데모")
            Text("친구들과 계속 이어지는 모임", style = MaterialTheme.typography.bodyMedium)
            Text("목록 상태 체험", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    RoomPreviewMode.CONTENT to "목록",
                    RoomPreviewMode.EMPTY to "빈 화면",
                    RoomPreviewMode.LOADING to "로딩",
                    RoomPreviewMode.OFFLINE to "오프라인"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = mode == value,
                        onClick = { mode = value },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .testTag("rooms-state-${value.name}")
                    )
                }
            }
        }
        when (mode) {
            RoomPreviewMode.LOADING -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("rooms-loading")
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("방 목록 불러오는 중 (시연)", modifier = Modifier.padding(top = 12.dp))
                }
            }
            RoomPreviewMode.EMPTY -> item {
                CenteredInfo("아직 참여한 방이 없습니다", "새 방을 만들거나 받은 초대를 확인해 보세요.")
            }
            RoomPreviewMode.OFFLINE, RoomPreviewMode.CONTENT -> {
                if (mode == RoomPreviewMode.OFFLINE) {
                    item {
                        PreviewNotice("오프라인 화면 시연 중 · 실제 서버 연결 상태는 확인하지 않습니다.")
                    }
                }
                items(rooms, key = { it.id }) { room ->
                    RoomCard(room, onOpen = { openRoom(room.id) })
                }
            }
        }
        item {
            PrimaryAction("새 방 만들기", onClick = onCreate, modifier = Modifier.fillMaxWidth().testTag("rooms-create"))
        }
        item {
            TextButton(
                onClick = onInvitePreview,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("rooms-join-preview")
            ) { Text("초대받은 화면 보기") }
        }
    }
}

@Composable
private fun RoomChatScreen(
    messages: List<MessagePreview>,
    editingChat: Boolean,
    scrollableActions: (@Composable () -> Unit)?,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onGame: () -> Unit
) {
    val messagesState = rememberLazyListState()
    var previousMessageCount by remember { mutableStateOf(messages.size) }
    var wasEditingChat by remember { mutableStateOf(false) }
    LaunchedEffect(editingChat, messages.size) {
        if (editingChat || wasEditingChat || messages.size > previousMessageCount) {
            // Anchor the last message at the next measure, after the room header reflows.
            messagesState.requestScrollToItem(messages.size)
        }
        previousMessageCount = messages.size
        wasEditingChat = editingChat
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = messagesState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item(key = "game-banner") {
                Column(Modifier.padding(bottom = 8.dp)) {
                    scrollableActions?.invoke()
                    GamePreviewBanner(onGame)
                }
            }
            items(messages) { item ->
                MessageBubble(item)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f).testTag("chat-input")
                    .semantics { contentDescription = "이 기기의 데모 메시지" },
                placeholder = { Text("데모 메시지") },
                maxLines = 3,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = input.isNotBlank(),
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "데모 메시지 추가")
            }
        }
    }
}

@Composable
private fun GamePreviewBanner(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text("너 누구야?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "데모 게임",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "너 누구야? 게임 시안 보기" }
            ) { Text("시안 보기") }
        }
    }
}

@Composable
private fun GamesScreen(onGame: (GamePreview) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("game-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PreviewNotice()
            SectionTitle("어떤 게임을 할까요?")
        }
        items(DemoCatalog.games, key = { it.id }) { game ->
            GameCard(game, onOpen = { onGame(game) })
        }
    }
}

@Composable
private fun RankingsScreen(title: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ranking-screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PreviewNotice("시즌 점수는 예시이며 실제 경기 결과가 아닙니다.")
            SectionTitle(title)
            Text("2026년 11월 · 예시 시즌", style = MaterialTheme.typography.bodySmall)
        }
        items(DemoCatalog.ranking) { player ->
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${player.place}위", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(player.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${player.points}점", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onSettings: () -> Unit,
    nickname: String
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewNotice()
        SectionTitle("내 프로필")
        ContentCard {
            Text("${nickname} · 데모 미리보기", style = MaterialTheme.typography.titleMedium)
            Text("로그인 및 개인 통계는 연결 전입니다.", style = MaterialTheme.typography.bodyMedium)
        }
        ContentCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다크 모드", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = darkTheme, onCheckedChange = onThemeChange)
            }
        }
        PrimaryAction("설정 화면", onClick = onSettings, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SettingsScreen(darkTheme: Boolean, onThemeChange: (Boolean) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewNotice("계정·알림·개인정보 실제 변경은 아직 사용할 수 없습니다.")
        ContentCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다크 모드", modifier = Modifier.weight(1f))
                Switch(checked = darkTheme, onCheckedChange = onThemeChange)
            }
        }
        ContentCard {
            Text("계정 삭제·신고·차단", style = MaterialTheme.typography.titleMedium)
            Text("1-C에서 상태별 UI를 설계했습니다. 서버 연동 후 구현합니다.")
        }
        PrimaryAction("내 방으로 돌아가기", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UnavailableScreen(title: String, description: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PreviewNotice()
        CenteredInfo(title, description)
        PrimaryAction("돌아가기", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GamePreviewScreen(
    game: GamePreview,
    phase: String,
    onNext: (String) -> Unit,
    onRoom: () -> Unit
) {
    if (game.id != "identity") {
        UnavailableScreen(
            title = "${game.name} 디자인 준비",
            description = "이 게임의 온라인 판정·대기실·채팅은 후속 단계에서 구현합니다.",
            onBack = onRoom
        )
        return
    }
    when (phase) {
        "lobby" -> LobbyPreview(onNext = { onNext("chat") })
        "chat" -> AnonymousChatPreview(onNext = { onNext("guess") })
        "guess" -> GuessPreview(onNext = { onNext("result") })
        else -> ResultPreview(onRoom = onRoom)
    }
}

@Composable
private fun LobbyPreview(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PreviewNotice("대기실은 시연용입니다. 실제 참가·준비·시작 권한은 연결되지 않았습니다.")
            SectionTitle("게임 대기실")
            ContentCard {
                Text("너 누구야? · 준비 4/5명 (예시)", style = MaterialTheme.typography.titleMedium)
                listOf("민지", "지훈", "수빈", "도윤", "나").forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (name == "도윤") "준비 대기" else "준비 완료",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        PrimaryAction(
            "익명 채팅 시안 보기", onClick = onNext,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

@Composable
private fun AnonymousChatPreview(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("anonymous-chat-preview"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "round-preview") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewNotice("정적 익명 채팅 시안 · 타이머 02:35는 실제 카운트다운이 아닙니다.")
                    ContentCard {
                        Text("너 누구야? · ROUND 1 · 02:35 (예시)", style = MaterialTheme.typography.bodySmall)
                        Text("친구 중 무인도에서 가장 오래 살아남을 사람은?", style = MaterialTheme.typography.titleMedium)
                        Text("내 익명 정체: 부엉이", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(DemoCatalog.anonymousMessages) { message -> MessageBubble(message) }
        }
        PrimaryAction(
            "정체 추리 시안 보기",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}

@Composable
private fun GuessPreview(onNext: () -> Unit) {
    val aliases = listOf("고양이", "토끼", "강아지", "여우")
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PreviewNotice("정체 선택 화면 예시 · 실제 투표 제출이나 서버 검증은 수행하지 않습니다.")
        SectionTitle("정체 추리")
        Text("익명 참가자를 실제 친구와 연결해 보세요.", style = MaterialTheme.typography.bodyMedium)
        aliases.forEach { alias ->
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alias, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("선택 전 · 예시", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PrimaryAction("결과 디자인 보기", onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultPreview(onRoom: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewNotice("정적 결과 예시 · 실제 기록·점수·정체는 저장하지 않습니다.")
        SectionTitle("정체 공개!")
        ContentCard {
            Text("성공한 추리: 3 / 4", style = MaterialTheme.typography.titleMedium)
            Text("정체 적중: 3 × 100점 = 300점", style = MaterialTheme.typography.bodyMedium)
            Text("위장 보너스: +20점", style = MaterialTheme.typography.bodyMedium)
            Text("내 획득 점수: +320점 (예시)", style = MaterialTheme.typography.titleSmall)
        }
        PrimaryAction("방 채팅으로 돌아가기", onClick = onRoom, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "톡술래 다크", showBackground = true, widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkPreview() {
    TalkSullaeApp(initialDarkTheme = true)
}

@Preview(name = "톡술래 라이트", showBackground = true, widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun LightPreview() {
    TalkSullaeApp(initialDarkTheme = false)
}
