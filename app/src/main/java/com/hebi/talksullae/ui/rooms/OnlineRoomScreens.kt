package com.hebi.talksullae.ui.rooms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebi.talksullae.data.RoomInputRules
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.PreviewNotice
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.design.SectionTitle
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.firebase.CloudRoomsRepository
import com.hebi.talksullae.firebase.OnlineRoomsRepository
import com.hebi.talksullae.firebase.OnlineRoomsViewModel

@Composable
internal fun OnlineRoomsHost(
    uid: String,
    accountName: String,
    onClose: () -> Unit,
    onSignOut: () -> Unit,
    repository: OnlineRoomsRepository? = null
) {
    val repo = remember(repository) { repository ?: CloudRoomsRepository() }
    val factory = remember(uid, repo) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnlineRoomsViewModel(repo, uid) as T
            }
        }
    }
    val vm: OnlineRoomsViewModel = viewModel(key = "online-$uid", factory = factory)
    DisposableEffect(vm) {
        vm.start()
        onDispose { vm.stop() }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var createVisible by rememberSaveable { mutableStateOf(false) }
    var roomName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var approval by rememberSaveable { mutableStateOf(true) }
    var joinCode by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<Pair<String, String>?>(null) }
    val room = state.selectedRoom
    val selected = state.entries.firstOrNull { it.id == state.selectedId }
    val isOwner = room?.ownerId == uid
    var chatVisible by rememberSaveable { mutableStateOf(false) }
    var identityVisible by rememberSaveable { mutableStateOf(false) }
    var liarVisible by rememberSaveable { mutableStateOf(false) }
    var forbiddenVisible by rememberSaveable { mutableStateOf(false) }
    var balanceVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.selectedId, selected?.status) {
        if (state.selectedId == null || selected?.status != "active") {
            chatVisible = false
            identityVisible = false
            liarVisible = false
            forbiddenVisible = false
            balanceVisible = false
        }
    }

    if (balanceVisible && room != null && selected?.status == "active") {
        OnlineBalanceGameHost(
            uid = uid,roomId = room.id,roomName = room.name,ownerId = room.ownerId,
            members = state.members.filter { it.status == "active" },
            onBack = { balanceVisible = false }
        )
        return
    }

    if (forbiddenVisible && room != null && selected?.status == "active") {
        OnlineForbiddenGameHost(
            uid = uid,roomId = room.id,roomName = room.name,ownerId = room.ownerId,
            members = state.members.filter { it.status == "active" },
            onBack = { forbiddenVisible = false }
        )
        return
    }

    if (liarVisible && room != null && selected?.status == "active") {
        OnlineLiarGameHost(
            uid = uid,
            roomId = room.id,
            roomName = room.name,
            ownerId = room.ownerId,
            members = state.members.filter { it.status == "active" },
            onBack = { liarVisible = false }
        )
        return
    }

    if (identityVisible && room != null && selected?.status == "active") {
        OnlineIdentityGameHost(
            uid = uid,
            roomId = room.id,
            roomName = room.name,
            ownerId = room.ownerId,
            members = state.members.filter { it.status == "active" },
            onBack = { identityVisible = false }
        )
        return
    }

    if (chatVisible && room != null && selected?.status == "active") {
        OnlineRoomChatHost(
            uid = uid,
            roomId = room.id,
            roomName = room.name,
            onBack = { chatVisible = false }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("online-rooms-host"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PreviewNotice("초대 코드로 친구를 모으고 가입 요청과 멤버를 관리할 수 있습니다.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "온라인 방 관리",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = { if (state.selectedId != null) vm.closeRoom() else onClose() }) {
                Text(if (state.selectedId == null) "닫기" else "방 목록")
            }
        }
        Text(accountName, style = MaterialTheme.typography.bodySmall)
        if (state.working) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("서버 처리 중…")
            }
        }
        state.error?.let {
            ContentCard {
                Text("오류: $it", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::clearNotice) { Text("닫기") }
            }
        }
        state.info?.let {
            ContentCard {
                Text(it)
                TextButton(onClick = vm::clearNotice) { Text("확인") }
            }
        }
        if (state.selectedId == null) {
            SectionTitle("내 온라인 방")
            if (state.entries.isEmpty()) {
                Text("가입한 온라인 방이 없습니다. 새 방을 만들거나 초대 코드로 가입해 주세요.")
            }
            state.entries.forEach { entry ->
                ContentCard(modifier = Modifier.testTag("online-room-${entry.id}")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            Text(membershipLabel(entry.status), style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.status == "active") {
                            TextButton(onClick = { vm.openRoom(entry.id) }) { Text("열기") }
                        }
                    }
                }
            }
            HorizontalDivider()
            OutlinedButton(
                onClick = { createVisible = !createVisible },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("online-create-toggle"),
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (createVisible) "방 생성 닫기" else "새 온라인 방 만들기") }
            if (createVisible) {
                val valid = RoomInputRules.valid(roomName, description)
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    modifier = Modifier.fillMaxWidth().testTag("online-room-name"),
                    label = { Text("방 이름") },
                    isError = roomName.isNotEmpty() && RoomInputRules.nameError(roomName) != null,
                    supportingText = { Text(RoomInputRules.nameError(roomName) ?: "${roomName.length}/24자") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("설명 (선택)") },
                    supportingText = { Text(RoomInputRules.descriptionError(description) ?: "${description.length}/120자") },
                    isError = RoomInputRules.descriptionError(description) != null,
                    maxLines = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("방장 가입 승인", modifier = Modifier.weight(1f))
                    Switch(checked = approval, onCheckedChange = { approval = it })
                }
                PrimaryAction(
                    "실제 방 생성",
                    modifier = Modifier.fillMaxWidth().testTag("online-create-submit"),
                    enabled = valid && !state.working,
                    onClick = {
                        vm.createRoom(roomName, description, approval)
                        createVisible = false
                        roomName = ""
                        description = ""
                    }
                )
            }
            HorizontalDivider()
            SectionTitle("초대 코드로 가입")
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it },
                modifier = Modifier.fillMaxWidth().testTag("online-join-code"),
                label = { Text("방 ID.초대 코드") },
                supportingText = { Text("방장이 전달한 코드 전체를 붙여넣어 주세요.") },
                minLines = 2,
                maxLines = 4
            )
            PrimaryAction(
                "가입 신청",
                onClick = { vm.join(joinCode) },
                modifier = Modifier.fillMaxWidth().testTag("online-join-submit"),
                enabled = joinCode.trim().isNotBlank() && !state.working
            )
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Google 계정 로그아웃") }
        } else if (selected?.status != "active") {
            SectionTitle(selected?.name ?: "방 가입 상태")
            Text("현재 상태: ${membershipLabel(selected?.status.orEmpty())}")
            Text("방장 승인 이전에는 멤버 목록이나 방 내용에 접근할 수 없습니다.")
        } else if (room == null) {
            CircularProgressIndicator()
            Text("방 정보 확인 중…")
        } else {
            SectionTitle(room.name)
            Text(room.description.ifBlank { "설명이 없습니다." })
            Text("참가 ${room.memberCount}명 · 승인 대기 ${room.pendingCount}명")
            Text(if (isOwner) "내 역할: 방장" else "내 역할: 멤버")
            PrimaryAction(
                "일반 채팅 열기",
                onClick = { forbiddenVisible = false; liarVisible = false; identityVisible = false; chatVisible = true },
                modifier = Modifier.fillMaxWidth().testTag("online-chat-open")
            )
            PrimaryAction(
                "너 누구야? 온라인 게임",
                onClick = { forbiddenVisible = false; liarVisible = false; chatVisible = false; identityVisible = true },
                modifier = Modifier.fillMaxWidth().testTag("identity-game-open")
            )
            PrimaryAction(
                "라이어 채팅 온라인 게임",
                onClick = { balanceVisible = false; forbiddenVisible = false; chatVisible = false; identityVisible = false; liarVisible = true },
                modifier = Modifier.fillMaxWidth().testTag("liar-game-open")
            )
            PrimaryAction(
                "금지어 채팅 온라인 게임",
                onClick = {chatVisible = false; identityVisible = false;
                    liarVisible = false; forbiddenVisible = true},
                modifier = Modifier.fillMaxWidth().testTag("forbidden-game-open")
            )
            PrimaryAction(
                "밸런스 예측 온라인 게임",
                onClick = {
                    chatVisible = false; identityVisible = false
                    liarVisible = false; forbiddenVisible = false; balanceVisible = true
                },
                modifier = Modifier.fillMaxWidth().testTag("balance-game-open")
            )

            if (isOwner) {
                PrimaryAction(
                    "새 초대 코드 발급 (이전 코드 폐기)",
                    onClick = { vm.generateInvitation(room.id) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.working
                )
                state.invite?.let { invite ->
                    ContentCard {
                        Text("24시간 유효한 초대 코드입니다. 다른 사람에게 공개하지 마세요.")
                        SelectionContainer {
                            Text(invite.code, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("톡술래 초대 코드", invite.code))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("코드 복사") }
                    }
                }
            }
            SectionTitle("멤버 · 서버 기록")
            state.members.forEach { member ->
                ContentCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                member.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (member.uid == room.ownerId) "방장"
                                else membershipLabel(member.status),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (isOwner && member.uid != uid) {
                            val actions = if (member.status == "pending") {
                                listOf("approve" to "승인", "reject" to "거절")
                            } else if (member.status == "active") {
                                listOf("remove" to "강퇴", "transfer" to "방장 위임")
                            } else emptyList()
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                actions.forEach { (action, title) ->
                                    TextButton(
                                        onClick = { confirmation = member.uid to action },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) { Text(title) }
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { confirmation = uid to "leave" },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("이 방 나가기") }
        }
    }

    confirmation?.let { (targetUid, action) ->
        val member = state.members.firstOrNull { it.uid == targetUid }
        val title = when (action) {
            "approve" -> "가입 승인"
            "reject" -> "가입 거절"
            "remove" -> "강퇴"
            "transfer" -> "방장 위임"
            else -> "방 나가기"
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title) },
            text = {
                Text(
                    if (action == "leave") "서버에서 방 탈퇴를 진행하시겠습니까? 방장은 멤버가 있을 때 먼저 위임해야 합니다."
                    else "${member?.name ?: "멤버"}님에게 '${title}'을 적용하시겠습니까? 서버에서 권한을 다시 검증합니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == "leave") vm.leave(room?.id ?: "")
                    else vm.manage(room?.id ?: "", targetUid, action)
                    confirmation = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("취소") } }
        )
    }
}

private fun membershipLabel(status: String) = when (status) {
    "active" -> "가입 완료"
    "pending" -> "방장 승인 대기"
    "rejected" -> "가입 거절"
    "removed" -> "강퇴됨"
    "left" -> "탈퇴"
    else -> "정보 확인 중"
}
