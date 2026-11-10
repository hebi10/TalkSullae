package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.data.DemoRoomMembers
import com.hebi.talksullae.data.JoinPreviewState
import com.hebi.talksullae.data.MemberPreview
import com.hebi.talksullae.data.joinStateMessage
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.PreviewNotice
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.design.SectionTitle

@Composable
fun InviteScreen(
    roomName: String,
    isOwner: Boolean,
    onViewMembers: () -> Unit,
    onBack: () -> Unit,
    guestOnly: Boolean = false
) {
    var showGuestPreview by rememberSaveable(roomName) { mutableStateOf(guestOnly || !isOwner) }
    var requestOutcome by rememberSaveable(roomName) { mutableStateOf("pending") }
    var joinState by rememberSaveable(roomName) { mutableStateOf(JoinPreviewState.INVITED) }
    val showHost = isOwner && !showGuestPreview

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .testTag("invite-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PreviewNotice("초대·가입 승인 화면의 로컬 시연입니다. 카카오톡 메시지나 서버 요청이 발생하지 않습니다.")
        SectionTitle(if (showHost) "친구 초대" else "초대받은 사용자")
        if (isOwner) {
            OutlinedButton(
                onClick = { showGuestPreview = !showGuestPreview },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("invite-switch-role"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (showGuestPreview) "방장용 화면으로 돌아가기" else "초대받은 화면 미리보기")
            }
        }
        if (showHost) {
            ContentCard {
                Text(roomName, style = MaterialTheme.typography.titleMedium)
                Text("비공개 방 · 가입 요청은 방장이 관리", style = MaterialTheme.typography.bodySmall)
                Text("초대 링크 발급·복사·카카오톡 공유는 서버 연결 후 제공됩니다.")
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("카카오톡 초대 링크 생성 · 연결 전")
                }
            }
            ContentCard {
                Text("가입 요청 미리보기", style = MaterialTheme.typography.titleMedium)
                Text("예진 · 요청 상태: " + when (requestOutcome) {
                    "approved" -> "로컬 승인 시연"
                    "declined" -> "로컬 거절 시연"
                    else -> "승인 대기"
                }, style = MaterialTheme.typography.bodyMedium)
                if (requestOutcome == "pending") {
                    PrimaryAction(
                        label = "승인 시연",
                        modifier = Modifier.fillMaxWidth().testTag("invite-approve-preview"),
                        onClick = { requestOutcome = "approved" }
                    )
                    OutlinedButton(
                        onClick = { requestOutcome = "declined" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("거절 시연")
                    }
                } else {
                    Text("실제 상대방에게 아무 알림도 보내지 않았습니다.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { requestOutcome = "pending" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("다시 시연") }
                }
            }
            PrimaryAction(label = "멤버 관리로 이동", onClick = onViewMembers, modifier = Modifier.fillMaxWidth())
        } else {
            GuestInvitationStates(
                roomName = roomName,
                currentState = joinState,
                onStateChange = { joinState = it }
            )
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("방으로 돌아가기")
        }
    }
}

@Composable
private fun GuestInvitationStates(
    roomName: String,
    currentState: JoinPreviewState,
    onStateChange: (JoinPreviewState) -> Unit
) {
    val detail = joinStateMessage(currentState)
    ContentCard {
        Text(roomName, style = MaterialTheme.typography.titleMedium)
        Text("방장: 민지 · 초대/가입 UI 예시", style = MaterialTheme.typography.bodySmall)
        Text(detail.first, style = MaterialTheme.typography.titleMedium)
        Text(detail.second, style = MaterialTheme.typography.bodyMedium)
        when (currentState) {
            JoinPreviewState.INVITED -> {
                PrimaryAction(
                    label = "가입 요청 시연",
                    onClick = { onStateChange(JoinPreviewState.PENDING) },
                    modifier = Modifier.fillMaxWidth().testTag("guest-join-request")
                )
            }
            JoinPreviewState.PENDING -> {
                OutlinedButton(
                    onClick = { onStateChange(JoinPreviewState.INVITED) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("요청 취소 시연") }
            }
            else -> Text("다른 상태를 선택해 화면을 확인할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("예외 상태 미리보기", style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            JoinPreviewState.INVITED to "초대",
            JoinPreviewState.PENDING to "승인 대기",
            JoinPreviewState.APPROVED to "승인",
            JoinPreviewState.DECLINED to "거절",
            JoinPreviewState.REMOVED to "강퇴",
            JoinPreviewState.EXPIRED to "만료"
        ).forEach { (state, title) ->
            FilterChip(
                selected = currentState == state,
                onClick = { onStateChange(state) },
                label = { Text(title) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("join-state-${state.name}")
            )
        }
    }
    PreviewNotice("화면 상태 전환은 실제 링크·회원 자격·서버 정보와 관계없는 시각적 시연입니다.")
}

@Composable
fun MembersScreen(
    roomName: String,
    members: List<MemberPreview>,
    ownerId: String,
    onInvite: () -> Unit,
    onKickPreview: (String) -> Unit,
    onTransferPreview: (String) -> Unit
) {
    val isOwner = ownerId == DemoRoomMembers.MY_ID
    var activeMember by remember { mutableStateOf<MemberPreview?>(null) }
    var action by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .testTag("members-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PreviewNotice("방장·강퇴·위임은 이 기기에서만 보여 주는 데모입니다. 서버 멤버 권한은 변경되지 않습니다.")
        SectionTitle("멤버 관리")
        ContentCard {
            Text(roomName, style = MaterialTheme.typography.titleMedium)
            Text("멤버 ${members.size}명 · " + if (isOwner) "내가 방장" else "일반 멤버", style = MaterialTheme.typography.bodyMedium)
            if (isOwner) {
                PrimaryAction(
                    label = "친구 초대 화면",
                    onClick = onInvite,
                    modifier = Modifier.fillMaxWidth().testTag("members-invite")
                )
            } else {
                Text("초대·강퇴·방장 위임은 방장만 수행할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
        members.forEach { member ->
            ContentCard(modifier = Modifier.testTag("member-${member.id}")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (ownerId == member.id) "방장" else "멤버",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOwner && member.id != DemoRoomMembers.MY_ID) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        androidx.compose.foundation.layout.Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("member-actions-${member.id}")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "${member.name} 관리")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("강퇴 시연") },
                                    onClick = {
                                        menuExpanded = false
                                        activeMember = member
                                        action = "kick"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("방장 위임 시연") },
                                    onClick = {
                                        menuExpanded = false
                                        activeMember = member
                                        action = "transfer"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "다른 방의 정보나 실제 사용자 권한은 변경하지 않습니다.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    val selected = activeMember
    if (selected != null) {
        val isKick = action == "kick"
        AlertDialog(
            onDismissRequest = { activeMember = null; action = "" },
            title = { Text(if (isKick) "멤버 강퇴 시연" else "방장 위임 시연") },
            text = {
                Text(
                    if (isKick) "${selected.name}님을 이 로컬 목록에서 제외할까요? 실제 강퇴나 재가입 제한은 수행하지 않습니다."
                    else "${selected.name}님에게 이 로컬 방의 방장 표시를 넘길까요? 실제 서버 권한은 변경되지 않습니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isKick) onKickPreview(selected.id) else onTransferPreview(selected.id)
                        activeMember = null
                        action = ""
                    },
                    modifier = Modifier.testTag(if (isKick) "confirm-kick" else "confirm-transfer")
                ) {
                    Text(if (isKick) "로컬 강퇴 확인" else "로컬 위임 확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeMember = null; action = "" }) { Text("취소") }
            }
        )
    }
}
