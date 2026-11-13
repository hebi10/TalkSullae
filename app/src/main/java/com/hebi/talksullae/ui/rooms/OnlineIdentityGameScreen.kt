package com.hebi.talksullae.ui.rooms

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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("identity-game-host"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("너 누구야? · $roomName", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("방 관리") }
        }
        Text("4~10명의 친구와 익명으로 대화한 뒤 정체를 추리합니다.",
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

        if (!state.loading && snapshot == null) {
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
            Text("상태: ${phase?.name ?: "확인 중"} · 라운드 ${snapshot.session.round}",
                style = MaterialTheme.typography.titleMedium)
            remaining?.let {
                Text("남은 시간 약 ${it}초 · 서버 마감 기준",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot.fromCache) {
                Text("캐시 화면입니다. 서버 동기화를 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
            }

            if (phase == GamePhase.WAITING || phase == GamePhase.READY) {
                Text("참가 ${snapshot.participants.size}명 / 최대 10명 · 최소 4명")
                snapshot.participants.forEach { participant ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(names[participant.uid] ?: "친구", modifier = Modifier.weight(1f))
                        Text(if (participant.ready) "준비 완료" else "준비 중")
                    }
                }
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
                if (uid == ownerId && phase == GamePhase.READY) {
                    PrimaryAction("게임 시작", onClick = { vm.action("start") },
                        enabled = !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("identity-start"))
                }
            }

            if (player && phase == GamePhase.PLAYING) {
                Text(snapshot.publicState["prompt"] as? String ?: "주제 확인 중",
                    style = MaterialTheme.typography.titleMedium)
                Text("내 동물 별칭: ${ownAlias ?: "확인 중"}",
                    style = MaterialTheme.typography.titleMedium)
                IdentityMessages(
                    state.messages, ownAlias, state.canLoadOlder,
                    state.loadingOlder, vm::loadOlder
                )
                OutlinedTextField(
                    value = state.draft, onValueChange = vm::editMessage,
                    enabled = ownAlias != null && state.pendingMessage == null && !state.sending,
                    label = { Text("익명 메시지 · 500자 이내") },
                    supportingText = { Text("${state.draft.length}/500자") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("identity-chat-input")
                )
                if (state.pendingMessage != null && !state.sending) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = vm::retryMessage) { Text("메시지 재시도") }
                        TextButton(onClick = vm::abandonMessage) { Text("다시 작성") }
                    }
                } else {
                    PrimaryAction("익명 메시지 전송", onClick = vm::sendMessage,
                        enabled = ownAlias != null && !state.sending &&
                            state.pendingMessage == null && state.draft.trim().length in 1..500,
                        modifier = Modifier.fillMaxWidth().testTag("identity-chat-send"))
                }
            }

            if (player && phase == GamePhase.VOTING) {
                Text("동물 별칭을 친구의 실제 이름과 일대일로 연결해 주세요.",
                    style = MaterialTheme.typography.titleMedium)
                Text("내 별칭과 내 이름은 추리에서 제외됩니다.",
                    style = MaterialTheme.typography.bodySmall)
                if (ownAlias == null) Text("개인 별칭 확인 중…")
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
                Text("정체 공개·결과", style = MaterialTheme.typography.titleMedium)
                Text(snapshot.publicState["publishedResult"] as? String ?: "결과를 불러오는 중입니다.")
                if (phase == GamePhase.REVEAL) Text("곧 게임이 최종 종료됩니다.")
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

@Composable
private fun IdentityMessages(
    messages: List<IdentityChatMessage>,
    ownAlias: String?,
    canLoadOlder: Boolean,
    loadingOlder: Boolean,
    onLoadOlder: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()).testTag("identity-chat-messages"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (canLoadOlder) {
            TextButton(onClick = onLoadOlder, enabled = !loadingOlder) {
                Text(if (loadingOlder) "이전 대화 조회 중…" else "이전 대화 보기")
            }
        }
        messages.forEach { message ->
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text(if (message.alias == ownAlias) "나 · ${message.alias}" else message.alias,
                    style = MaterialTheme.typography.labelMedium)
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
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
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(alias, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.testTag("identity-guess-select-$alias")) {
            Text(selected?.let { names[it] } ?: "친구 선택")
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
