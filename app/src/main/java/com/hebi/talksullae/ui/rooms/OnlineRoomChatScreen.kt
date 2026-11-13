package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.firebase.FirestoreRoomChatRepository
import com.hebi.talksullae.firebase.RoomChatInputPolicy
import com.hebi.talksullae.firebase.RoomChatMessage
import com.hebi.talksullae.firebase.RoomChatRepository
import com.hebi.talksullae.firebase.RoomChatViewModel
import com.hebi.talksullae.firebase.UserSafetyRepository
import com.hebi.talksullae.firebase.FirestoreUserSafetyRepository
import com.hebi.talksullae.firebase.UserSafetyViewModel
import com.hebi.talksullae.firebase.UserSafetyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun OnlineRoomChatHost(
    uid: String,
    roomId: String,
    roomName: String,
    onBack: () -> Unit,
    repository: RoomChatRepository? = null,
    safetyRepository: UserSafetyRepository? = null
) {
    val chatRepository = remember(repository) { repository ?: FirestoreRoomChatRepository() }
    val factory = remember(chatRepository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RoomChatViewModel(chatRepository) as T
        }
    }
    val vm: RoomChatViewModel = viewModel(key = "room-chat-$uid-$roomId", factory = factory)
    DisposableEffect(vm, roomId) {
        vm.start(roomId)
        onDispose { vm.stop() }
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val safetyRepo = remember(repository,safetyRepository) {
        safetyRepository ?: if (repository == null) FirestoreUserSafetyRepository() else null
    }
    val safetyFactory = remember(safetyRepo) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                UserSafetyViewModel(requireNotNull(safetyRepo)) as T
        }
    }
    val safetyVm: UserSafetyViewModel? =
        if (safetyRepo != null) viewModel(key = "room-safety-$uid",factory = safetyFactory)
        else null
    DisposableEffect(safetyVm,uid) {
        safetyVm?.start(uid)
        onDispose { safetyVm?.stop() }
    }
    val safety = safetyVm?.state?.collectAsStateWithLifecycle()?.value ?: UserSafetyState()
    var reportTarget by remember { mutableStateOf<RoomChatMessage?>(null) }
    var reportCategory by remember { mutableStateOf("abuse") }
    var reportDetails by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val newestId = state.messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().testTag("online-chat-host")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(roomName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onBack) { Text("방 관리") }
        }
        HorizontalDivider()

        if (state.fromCache) {
            Text(
                "저장된 메시지를 표시 중입니다. 네트워크 상태에 따라 최신 내용이 아닐 수 있습니다.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.readError?.let { error ->
            Text(
                error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        state.historyError?.let { error ->
            Text(
                error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        if (state.initialLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) { CircularProgressIndicator() }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("online-chat-messages"),
            reverseLayout = true,
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages.asReversed(), key = { it.id }) { message ->
                if (message.senderUid in safety.blocked) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Text("차단한 사용자의 메시지입니다.")
                        TextButton(onClick = { safetyVm?.setBlocked(message.senderUid,false) },
                            enabled = !safety.working) { Text("차단 해제") }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RoomChatMessageRow(message, isOwn = message.senderUid == uid)
                        if (message.senderUid != uid && safetyVm != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        reportTarget = message
                                        reportCategory = "abuse"
                                        reportDetails = ""
                                    },
                                    modifier = Modifier.testTag("chat-report-" + message.id)
                                ) { Text("신고") }
                                TextButton(
                                    onClick = { safetyVm.setBlocked(message.senderUid,true) },
                                    enabled = !safety.working,
                                    modifier = Modifier.testTag("chat-block-" + message.id)
                                ) { Text("사용자 차단") }
                            }
                        }
                    }
                }
            }
            if (!state.initialLoading && state.messages.isEmpty() && state.readError == null) {
                item(key = "empty-chat") { Text("아직 메시지가 없습니다. 첫 메시지를 보내 보세요.") }
            }
            if (state.canLoadOlder) {
                item(key = "older-messages") {
                    TextButton(
                        modifier = Modifier.fillMaxWidth().testTag("online-chat-load-more"),
                        onClick = vm::loadOlder,
                        enabled = !state.loadingOlder
                    ) {
                        Text(if (state.loadingOlder) "이전 메시지 불러오는 중…" else "이전 메시지 보기")
                    }
                }
            }
        }

        HorizontalDivider()
        safety.error?.let { Text(it,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.error) }
        safety.info?.let { Text(it,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) }
        if (safety.pending != null && !safety.working) {
            TextButton(onClick = { safetyVm?.retryReport() },
                modifier = Modifier.testTag("chat-report-retry")) {
                Text("같은 신고 다시 시도")
            }
        }
        state.info?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.sendError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedTextField(
            value = state.draft,
            onValueChange = vm::updateDraft,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("online-chat-input"),
            label = { Text("메시지") },
            maxLines = 5,
            enabled = state.pending == null && !state.initialLoading && state.readError == null,
            supportingText = { Text("${RoomChatInputPolicy.normalize(state.draft).length}/1000자") },
            shape = RoundedCornerShape(8.dp)
        )
        if (state.pending != null && !state.sending) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = vm::retrySend, modifier = Modifier.weight(1f)) {
                    Text("같은 메시지 재시도")
                }
                TextButton(onClick = vm::abandonFailedSend, modifier = Modifier.weight(1f)) {
                    Text("입력 다시 작성")
                }
            }
        } else {
            PrimaryAction(
                label = if (state.sending) "메시지 전송 중…" else "전송",
                onClick = vm::send,
                enabled = !state.sending && !state.initialLoading &&
                    state.readError == null && state.pending == null &&
                    RoomChatInputPolicy.error(state.draft) == null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("online-chat-send")
            )
        }
    }

    reportTarget?.let { message ->
        AlertDialog(
            onDismissRequest = { if(!safety.working)reportTarget=null },
            title = { Text("채팅 메시지 신고") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("선택한 메시지와 신고 사유가 운영 담당자에게 전달됩니다.")
                    Row {
                        listOf("abuse" to "괴롭힘","child_safety" to "아동 안전",
                            "other" to "기타").forEach { (key,label) ->
                            TextButton(onClick = { reportCategory=key }) {
                                Text(label + if(reportCategory==key)" ✓" else "")
                            }
                        }
                    }
                    OutlinedTextField(
                        value=reportDetails,onValueChange={ reportDetails=it },
                        label={ Text("신고 사유 (5~1000자)") },maxLines=5,
                        modifier=Modifier.fillMaxWidth().testTag("chat-report-details")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        safetyVm?.report(roomId,message.id,reportCategory,reportDetails)
                        reportTarget=null
                    },
                    enabled = reportDetails.trim().length in 5..1000 &&
                        !safety.working,
                    modifier = Modifier.testTag("chat-report-submit")
                ) { Text("신고 제출") }
            },
            dismissButton = {
                TextButton(onClick={reportTarget=null}) { Text("취소") }
            }
        )
    }

}

@Composable
private fun RoomChatMessageRow(message: RoomChatMessage, isOwn: Boolean) {
    val displayTime = remember(message.createdAtMillis) {
        message.createdAtMillis?.let { millis ->
            SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(millis))
        } ?: "시간 확인 중"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${if (isOwn) "나" else message.senderName} · $displayTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
