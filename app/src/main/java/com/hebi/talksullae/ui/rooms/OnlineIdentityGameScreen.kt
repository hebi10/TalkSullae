package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.ProfileAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.game.core.GamePhase
import com.hebi.talksullae.game.online.FirestoreOnlineGameSessionRepository
import com.hebi.talksullae.game.online.IdentityChatMessage
import com.hebi.talksullae.game.online.IdentityGameViewModel
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
internal fun OnlineIdentityGameHost(
    uid: String,
    roomId: String,
    roomName: String,
    ownerId: String,
    members: List<CloudMember>,
    onBack: () -> Unit,
    repository: OnlineGameSessionRepository? = null
) {
    val repo = remember(repository) { repository ?: FirestoreOnlineGameSessionRepository() }
    val factory = remember(repo) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                IdentityGameViewModel(repo) as T
        }
    }
    val vm: IdentityGameViewModel = viewModel(key = "identity-$uid-$roomId", factory = factory)
    DisposableEffect(vm, uid, roomId) {
        vm.start(roomId, uid)
        onDispose { vm.stop() }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.session
    val phase = snapshot?.session?.phase
    val player = snapshot?.participants?.any { it.uid == uid } ?: false
    val ownAlias = state.ownAlias
    val aliases = (snapshot?.publicState?.get("options") as? List<*>)
        ?.mapNotNull { it as? String }.orEmpty()
    val names = members.associate { it.uid to it.name }
    val selections = remember(snapshot?.session?.sessionId, phase) {
        mutableStateMapOf<String, String>()
    }
    val guessAliases = aliases.filter { it != ownAlias }
    val targets = snapshot?.participants?.map { it.uid }?.filter { it != uid }.orEmpty()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val deadline = snapshot?.session?.deadlineEpochMillis
    LaunchedEffect(deadline) {
        while (deadline != null) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = deadline?.let { max(0L, (it - nowMillis + 999L) / 1000L) }
    val canVote = ownAlias != null && !state.hasSubmitted &&
        guessAliases.size == targets.size && guessAliases.isNotEmpty() &&
        selections.keys.containsAll(guessAliases) &&
        selections.values.toSet().size == targets.size

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val fixedChat = player && phase == GamePhase.PLAYING &&
        LocalConfiguration.current.screenHeightDp >= 650 && density.fontScale <= 1.3f && !imeVisible &&
        state.error == null && state.personalError == null && state.info == null &&
        state.pendingAction == null && state.pendingMessage == null && snapshot?.fromCache == false
    val pageScroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    val pageHeight = maxHeight
    Column(
        modifier = Modifier.fillMaxSize().then(if (fixedChat) Modifier else
            Modifier.verticalScroll(pageScroll).heightIn(min = pageHeight))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("identity-game-host"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "방으로 돌아가기")
            }
            Column(Modifier.weight(1f)) {
                Text(if (phase == GamePhase.REVEAL || phase == GamePhase.FINISHED)
                    "정체 공개·결과" else "너 누구야?", style = MaterialTheme.typography.titleLarge)
                Text(roomName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (phase == null) Text("4~10명의 친구와 익명으로 대화한 뒤 정체를 추리합니다.",
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.busy) Text("게임 서버 요청 중…")
        if (state.pendingAction != null && !state.busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::retryAction) { Text("같은 요청 재시도") }
                TextButton(onClick = vm::discardFailedAction) { Text("서버 상태 다시 확인") }
            }
        }

        if (state.otherGameActive) {
            Text("이 방에서 다른 게임이 진행 중입니다. 종료한 뒤 너 누구야?를 시작할 수 있습니다.")
            OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) {
                Text("진행 중인 게임 다시 확인")
            }
        } else if (!state.loading && snapshot == null) {
            Text("현재 진행 중인 게임이 없습니다.")
            if (uid == ownerId) {
                PrimaryAction("새 너 누구야? 게임 만들기",
                    onClick = vm::create,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("identity-game-create"))
            } else {
                Text("방장이 게임을 생성하면 참여할 수 있습니다.")
            }
            OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) {
                Text("게임 다시 확인")
            }
        }

        if (snapshot != null) {
            if (player && ownAlias == null &&
                (phase == GamePhase.PLAYING || phase == GamePhase.VOTING)) {
                state.personalError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::retryPersonal,
                        enabled = !state.personalLoading,
                        modifier = Modifier.testTag("identity-personal-retry")) {
                        Text("내 별칭 다시 불러오기")
                    }
                }
            }
            if (phase != GamePhase.WAITING && phase != GamePhase.READY &&
                phase != GamePhase.REVEAL && phase != GamePhase.FINISHED) {
                Surface(Modifier.fillMaxWidth(), color=MaterialTheme.colorScheme.surfaceVariant,
                    shape=RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(gamePhaseLabel(phase) + " · 라운드 " + snapshot.session.round,
                            style = MaterialTheme.typography.titleSmall)
                        remaining?.let {
                            Text(gameDeadlineLabel(it), style=MaterialTheme.typography.bodyMedium,
                                color=if(it in 0L..30L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else if (phase == GamePhase.WAITING || phase == GamePhase.READY) {
                remaining?.let { Text(gameDeadlineLabel(it), style=MaterialTheme.typography.bodySmall) }
            }
            if (snapshot.fromCache) {
                Text("캐시 화면입니다. 서버 동기화를 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
            }

            if (phase == GamePhase.WAITING || phase == GamePhase.READY) {
                Text("게임 대기실", style = MaterialTheme.typography.titleLarge)
                Text("참가 ${snapshot.participants.size}/10명 · 최소 4명")
                Text(lobbyReadinessLabel(snapshot.participants.size, snapshot.participants.count { it.ready }, 4, phase, snapshot.fromCache),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ContentCard {
                    Text("참가자 · 준비 ${snapshot.participants.count { it.ready }}/${snapshot.participants.size}",
                        style = MaterialTheme.typography.titleMedium)
                    snapshot.participants.forEach { participant ->
                        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatar(members.firstOrNull { it.uid == participant.uid }?.avatar, Modifier.size(36.dp))
                            Text((names[participant.uid] ?: "친구") + if (participant.uid == uid) " (나)" else "",
                                modifier = Modifier.weight(1f))
                            Text(if (participant.ready) "✓ 준비 완료" else "준비 중",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!player) {
                    PrimaryAction("게임 참가", onClick = { vm.action("join") },
                        enabled = !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("identity-join"))
                } else {
                    val ready = snapshot.participants.first { it.uid == uid }.ready
                    PrimaryAction(if (ready) "준비 취소" else "준비 완료",
                        onClick = { vm.action("set_ready", mapOf("ready" to !ready)) },
                        enabled = !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("identity-ready"))
                }
                if (uid == ownerId) {
                    PrimaryAction("게임 시작", onClick = { vm.action("start") },
                        enabled = phase == GamePhase.READY && !snapshot.fromCache && !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("identity-start"))
                }
            }

            if (player && phase == GamePhase.PLAYING) {
                ContentCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(ownAlias, Modifier.size(36.dp))
                        Text("내 동물 별칭: ${ownAlias ?: "확인 중"}",
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
                ContentCard {
                    Text(snapshot.publicState["prompt"] as? String ?: "주제 확인 중",
                        style = MaterialTheme.typography.titleMedium)
                }
                IdentityMessages(
                    state.messages, ownAlias, state.canLoadOlder,
                    state.loadingOlder, vm::loadOlder,
                    modifier = if (fixedChat) Modifier.weight(1f) else Modifier.heightIn(max = 360.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.draft, onValueChange = vm::editMessage,
                        enabled = ownAlias != null && state.pendingMessage == null && !state.sending,
                        placeholder = { Text("익명 메시지를 입력하세요.") },
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "익명 메시지 · 500자 이내" }
                            .testTag("identity-chat-input")
                    )
                    if (state.pendingMessage == null || state.sending) {
                        FilledIconButton(onClick = vm::sendMessage,
                            enabled = ownAlias != null && !state.sending &&
                                state.pendingMessage == null && state.draft.trim().length in 1..500,
                            modifier = Modifier.size(48.dp).testTag("identity-chat-send"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) { Icon(Icons.Default.Send, contentDescription = "익명 메시지 전송") }
                    }
                }
                if (state.draft.isNotEmpty()) Text("${state.draft.length}/500자",
                    style = MaterialTheme.typography.bodySmall)
                if (state.pendingMessage != null && !state.sending) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = vm::retryMessage) { Text("메시지 재시도") }
                        TextButton(onClick = vm::abandonMessage) { Text("다시 작성") }
                    }
                }
            }

            if (player && phase == GamePhase.VOTING) {
                Text("동물 별칭을 친구의 실제 이름과 일대일로 연결해 주세요.",
                    style = MaterialTheme.typography.titleMedium)
                Text("내 별칭과 내 이름은 추리에서 제외됩니다.",
                    style = MaterialTheme.typography.bodySmall)
                if (ownAlias == null) Text("개인 별칭 확인 중…")
                else Text("선택 ${selections.size}/${guessAliases.size} · 남은 선택 ${(guessAliases.size - selections.size).coerceAtLeast(0)}",
                    style = MaterialTheme.typography.titleSmall)
                guessAliases.forEach { alias ->
                    IdentityGuessRow(
                        alias = alias, candidates = targets, names = names,
                        selected = selections[alias],
                        enabled = ownAlias != null && !state.hasSubmitted && !state.busy &&
                            state.pendingAction == null,
                        onChoose = { candidate ->
                            val previousAlias = selections.entries
                                .firstOrNull { it.key != alias && it.value == candidate }?.key
                            val previousChoice = selections[alias]
                            if (previousAlias != null) {
                                if (previousChoice == null) selections.remove(previousAlias)
                                else selections[previousAlias] = previousChoice
                            }
                            selections[alias] = candidate
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.hasSubmitted) {
                    Text("추리가 제출됐습니다. 다른 참가자의 제출을 기다리는 중입니다.")
                } else {
                    PrimaryAction("정체 추리 제출",
                        onClick = { vm.action("submit", mapOf("choice" to selections.toMap())) },
                        enabled = canVote && !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("identity-guess-send"))
                }
            }

            if (phase == GamePhase.REVEAL || phase == GamePhase.FINISHED) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (phase == GamePhase.FINISHED && !snapshot.fromCache &&
                        snapshot.publicState["publishedResult"] is String) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (phase == GamePhase.FINISHED) "결과 공개 및 게임 종료" else "정체 공개",
                        style = MaterialTheme.typography.titleMedium)
                }
                ContentCard {
                    val result = snapshot.publicState["publishedResult"] as? String
                    if (result == null) {
                        Text("결과를 불러오는 중입니다.")
                    } else {
                        result.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            val alias = line.substringBefore(" = ").substringBefore(":").trim()
                            val recognizedAlias = alias in setOf("고양이", "토끼", "부엉이", "여우", "판다",
                                "펭귄", "강아지", "곰")
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (recognizedAlias) ProfileAvatar(alias, Modifier.size(32.dp))
                                // Display the original server-published line without calculating or inferring scores.
                                Text(line, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (phase == GamePhase.REVEAL) Text("곧 게임이 최종 종료됩니다.")
                }
            }
            if (phase == GamePhase.FINISHED) {
                Spacer(Modifier.weight(1f))
                PrimaryAction("방으로 돌아가기", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
            if (phase == GamePhase.CANCELLED) Text("게임이 취소됐습니다.")

            if (uid == ownerId && phase != GamePhase.FINISHED &&
                phase != GamePhase.CANCELLED) {
                OutlinedButton(onClick = { vm.action("cancel") },
                    enabled = !state.busy && state.pendingAction == null,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("게임 취소")
                }
            }
        }
    }
    }
}

@Composable
private fun IdentityMessages(
    messages: List<IdentityChatMessage>,
    ownAlias: String?,
    canLoadOlder: Boolean,
    loadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState()).testTag("identity-chat-messages"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (canLoadOlder) {
            TextButton(onClick = onLoadOlder, enabled = !loadingOlder) {
                Text(if (loadingOlder) "이전 대화 조회 중…" else "이전 대화 보기")
            }
        }
        messages.forEach { message ->
            val own = message.alias == ownAlias
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top) {
                if (!own) ProfileAvatar(message.alias, Modifier.padding(end = 8.dp).size(32.dp))
                Column(Modifier.fillMaxWidth(0.84f), horizontalAlignment = if (own) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (own) "나 · ${message.alias}" else message.alias, style = MaterialTheme.typography.labelMedium)
                    Surface(color = if (own) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (own) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(8.dp)) {
                        Text(message.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    message.createdAtMillis?.let {
                        Text(remember(it) { SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(it)) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (messages.isEmpty()) Text("아직 익명 대화가 없습니다.")
    }
}

@Composable
private fun IdentityGuessRow(
    alias: String, candidates: List<String>, names: Map<String, String>,
    selected: String?, enabled: Boolean,
    onChoose: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(alias, Modifier.size(36.dp))
        Text(alias, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("identity-guess-select-$alias")) {
            Text(selected?.let { names[it] } ?: "친구 선택", Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        if (selected != null) {
            Icon(Icons.Default.Check, contentDescription = "선택 완료", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(names[candidate] ?: "친구") },
                    onClick = { onChoose(candidate); expanded = false },
                    modifier = Modifier.testTag("identity-guess-option-$alias-$candidate"),
                    enabled = enabled
                )
            }
        }
    }
}
