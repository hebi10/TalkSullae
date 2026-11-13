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
import com.hebi.talksullae.game.online.LiarChatMessage
import com.hebi.talksullae.game.online.LiarGameViewModel
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
internal fun OnlineLiarGameHost(
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
                LiarGameViewModel(repo) as T
        }
    }
    val vm: LiarGameViewModel = viewModel(key = "liar-$uid-$roomId", factory = factory)
    DisposableEffect(vm, uid, roomId) {
        vm.start(roomId, uid)
        onDispose { vm.stop() }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snap = state.session
    val phase = snap?.session?.phase
    val participant = snap?.participants?.any { it.uid == uid } ?: false
    val names = members.associate { it.uid to it.name }
    val notice = snap?.publicState?.get("notice") as? String
    val options = (snap?.publicState?.get("options") as? List<*>)
        ?.mapNotNull { it as? String }.orEmpty()
    var selectedChoice by remember(snap?.session?.sessionId, snap?.session?.round, notice) {
        mutableStateOf("")
    }
    val deadline = snap?.session?.deadlineEpochMillis
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        while (deadline != null) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = deadline?.let { max(0L,(it - nowMillis + 999L)/1000L) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("liar-game-host"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("라이어 채팅 · $roomName", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("방 관리") }
        }
        Text("4~10명이 주제에 대해 힌트를 나누고 라이어를 찾아냅니다.",
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.pendingAction != null && !state.busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::retryAction) { Text("같은 요청 재시도") }
                TextButton(onClick = vm::discardFailedAction) { Text("서버 상태 재확인") }
            }
        }
        if (state.otherGameActive) {
            Text("이 방에서 다른 게임이 진행 중입니다. 종료 후 라이어 채팅을 시작할 수 있습니다.")
            OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) {
                Text("진행 중인 게임 다시 확인")
            }
        } else if (!state.loading && snap == null) {
            Text("현재 진행 중인 라이어 게임이 없습니다.")
            if (uid == ownerId) {
                PrimaryAction("새 라이어 게임 만들기", onClick = vm::create,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("liar-create"))
            } else {
                Text("방장이 게임을 만들면 참여할 수 있습니다.")
            }
            OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) {
                Text("게임 다시 확인")
            }
        }

        if (snap != null) {
            Text("상태: " + (phase?.name ?: "확인 중") + " · 라운드 " + snap.session.round,
                style = MaterialTheme.typography.titleMedium)
            remaining?.let { Text("남은 시간 약 " + it + "초 · 서버 기준",
                style = MaterialTheme.typography.bodySmall) }
            if (snap.fromCache) {
                Text("캐시 상태입니다. 서버 동기화를 확인해 주세요.")
            }
            if (phase == GamePhase.WAITING || phase == GamePhase.READY) {
                Text("참가 " + snap.participants.size + "명 / 4~10명")
                snap.participants.forEach { player ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(names[player.uid] ?: "친구", modifier = Modifier.weight(1f))
                        Text(if (player.ready) "준비 완료" else "준비 중")
                    }
                }
                if (!participant) {
                    PrimaryAction("게임 참가", onClick = { vm.action("join") },
                        enabled = !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("liar-join"))
                } else {
                    val ready = snap.participants.first { it.uid == uid }.ready
                    PrimaryAction(if (ready) "준비 취소" else "준비 완료",
                        onClick = { vm.action("set_ready",mapOf("ready" to !ready)) },
                        enabled = !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("liar-ready"))
                }
                if (uid == ownerId && phase == GamePhase.READY) {
                    PrimaryAction("라이어 게임 시작", onClick = { vm.action("start") },
                        enabled = !state.busy && state.pendingAction == null,
                        modifier = Modifier.fillMaxWidth().testTag("liar-start"))
                }
            }

            if (participant && (phase == GamePhase.PLAYING || phase == GamePhase.VOTING)) {
                if (state.role == "liar") {
                    Text("내 역할: 라이어 · 주제는 공개되지 않습니다.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("liar-secret-role"))
                } else if (state.role == "citizen") {
                    Text("내 역할: 일반 참가자 · 비밀 주제: " + (state.topic ?: "확인 중"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("liar-secret-role"))
                } else {
                    Text("개인 역할을 확인하는 중입니다.")
                }
                state.personalError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::retryPersonal, enabled = !state.personalLoading,
                        modifier = Modifier.testTag("liar-role-retry")) {
                        Text("내 역할 다시 불러오기")
                    }
                }
            }

            if (participant && phase == GamePhase.PLAYING) {
                Text(snap.publicState["prompt"] as? String ?: "채팅 준비 중",
                    style = MaterialTheme.typography.bodyMedium)
                LiarMessages(state.messages,names,state.canLoadOlder,state.loadingOlder,vm::loadOlder)
                OutlinedTextField(
                    value = state.draft, onValueChange = vm::editMessage,
                    enabled = state.role != null && state.pendingMessage == null && !state.sending,
                    label = { Text("채팅 메시지 · 최대 500자") },
                    supportingText = { Text(state.draft.length.toString() + "/500자") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("liar-chat-input")
                )
                if (state.pendingMessage != null && !state.sending) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = vm::retryMessage) { Text("메시지 재시도") }
                        TextButton(onClick = vm::abandonMessage) { Text("다시 작성") }
                    }
                } else {
                    PrimaryAction("채팅 전송", onClick = vm::sendMessage,
                        enabled = state.role != null && !state.sending &&
                            state.pendingMessage == null && state.draft.trim().length in 1..500,
                        modifier = Modifier.fillMaxWidth().testTag("liar-chat-send"))
                }
            }

            if (participant && phase == GamePhase.VOTING) {
                Text(snap.publicState["prompt"] as? String ?: "투표 준비 중",
                    style = MaterialTheme.typography.titleMedium)
                when (notice) {
                    "vote","revote" -> {
                        if (notice == "revote") Text("동점 후보에게만 투표할 수 있습니다.")
                        if (state.hasSubmitted) {
                            Text("투표를 제출했습니다. 다른 참가자 또는 서버 마감을 기다립니다.")
                        } else {
                            options.filter { it != uid }.forEach { candidate ->
                                OutlinedButton(
                                    onClick = { selectedChoice = candidate },
                                    enabled = state.role != null && !state.busy &&
                                        state.pendingAction == null,
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("liar-vote-" + candidate)
                                ) {
                                    Text((names[candidate] ?: "친구") +
                                        if (selectedChoice == candidate) " · 선택됨" else "")
                                }
                            }
                            PrimaryAction("라이어 지목 제출",
                                onClick = { vm.action("submit",mapOf("choice" to selectedChoice)) },
                                enabled = selectedChoice.isNotEmpty() && state.role != null &&
                                    !state.busy && state.pendingAction == null,
                                modifier = Modifier.fillMaxWidth().testTag("liar-vote-send"))
                        }
                    }
                    "guess" -> {
                        if (state.role != "liar") {
                            Text("라이어가 마지막으로 주제를 맞힐 차례입니다.")
                        } else if (state.hasSubmitted) {
                            Text("주제 답변을 제출했습니다. 결과 공개를 기다립니다.")
                        } else {
                            Text("비밀 주제를 추리해 하나를 선택해 주세요.")
                            options.forEach { topic ->
                                OutlinedButton(
                                    onClick = { selectedChoice = topic },
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("liar-topic-option-" + topic)
                                ) {
                                    Text(topic + if (selectedChoice == topic) " · 선택됨" else "")
                                }
                            }
                            PrimaryAction("주제 답안 제출",
                                onClick = { vm.action("submit",mapOf("choice" to selectedChoice)) },
                                enabled = selectedChoice.isNotEmpty() && !state.busy &&
                                    state.pendingAction == null,
                                modifier = Modifier.fillMaxWidth().testTag("liar-topic-send"))
                        }
                    }
                    else -> Text("게임 상태를 확인하고 있습니다.")
                }
                Text("제출 결과와 단계 전환은 서버에서 확정합니다.",
                    style = MaterialTheme.typography.bodySmall)
            }

            if (phase == GamePhase.REVEAL || phase == GamePhase.FINISHED) {
                Text("라이어 공개·최종 결과", style = MaterialTheme.typography.titleMedium)
                Text(snap.publicState["publishedResult"] as? String ?: "결과를 불러오는 중입니다.",
                    modifier = Modifier.testTag("liar-result"))
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
private fun LiarMessages(
    messages: List<LiarChatMessage>,
    names: Map<String,String>,
    canLoadOlder: Boolean,
    loadingOlder: Boolean,
    onLoadOlder: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
        .verticalScroll(rememberScrollState()).testTag("liar-chat-messages"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canLoadOlder) {
            TextButton(onClick = onLoadOlder, enabled = !loadingOlder) {
                Text(if (loadingOlder) "이전 대화 조회 중…" else "이전 대화 보기")
            }
        }
        messages.forEach { message ->
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text(names[message.senderUid] ?: "친구",
                    style = MaterialTheme.typography.labelMedium)
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (messages.isEmpty()) Text("아직 게임 대화가 없습니다.")
    }
}
