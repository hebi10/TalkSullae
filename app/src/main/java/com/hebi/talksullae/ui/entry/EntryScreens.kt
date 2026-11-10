package com.hebi.talksullae.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.data.RoomDraft
import com.hebi.talksullae.data.RoomInputRules
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.PreviewNotice
import com.hebi.talksullae.design.PrimaryAction

@Composable
fun WelcomeScreen(onContinue: () -> Unit, onInvitePreview: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.height(72.dp).fillMaxWidth())
        Text("톡술래", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("채팅으로 즐기는 친구들의 추리 게임", style = MaterialTheme.typography.bodyLarge)
        ContentCard {
            Text("모두 같은 방에서", style = MaterialTheme.typography.titleMedium)
            Text("친구들과 채팅하고, 익명으로 대화하며, 누가 누구인지 맞혀보세요.")
            Text("게임 기록은 이후 온라인 서버 연결 단계에서 지원할 예정입니다.", style = MaterialTheme.typography.bodySmall)
        }
        PreviewNotice("현재는 UI 체험 버전입니다. 로그인·카카오톡 링크·온라인 방은 연결되지 않았습니다.")
        PrimaryAction(
            label = "시작하기",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().testTag("welcome-start")
        )
        OutlinedButton(
            onClick = onInvitePreview,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("welcome-invite-demo"),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("초대받은 화면 미리보기")
        }
    }
}

@Composable
fun OnboardingScreen(
    onContinueDemo: (String) -> Unit,
    onGoogleSignIn: (() -> Unit)? = null,
    signInBusy: Boolean = false,
    authError: String? = null
) {
    var nickname by rememberSaveable { mutableStateOf("게스트") }
    var avatar by rememberSaveable { mutableStateOf("부엉이") }
    val validNickname = nickname.trim().length in 2..16 && !nickname.contains("\n")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("처음 오셨나요?", style = MaterialTheme.typography.headlineMedium)
        Text("닉네임과 아바타를 선택해 UI를 체험해 보세요.", style = MaterialTheme.typography.bodyMedium)
        PreviewNotice("계정이나 프로필은 온라인에 등록되지 않습니다. 이 화면에서 실제 약관 동의를 받지 않습니다.")
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            modifier = Modifier.fillMaxWidth().testTag("onboarding-nickname"),
            label = { Text("데모 닉네임") },
            supportingText = { Text("2~16자, 이 기기 화면에서만 사용") },
            isError = !validNickname,
            singleLine = true
        )
        Text("기본 아바타", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("부엉이", "토끼", "고양이").forEach { choice ->
                FilterChip(
                    selected = avatar == choice,
                    onClick = { avatar = choice },
                    label = { Text(choice) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("avatar-${choice}")
                )
            }
        }
        ContentCard {
            Text("온라인 로그인", style = MaterialTheme.typography.titleSmall)
            Text(if (onGoogleSignIn == null) "Firebase 설정이 없어 온라인 로그인을 사용할 수 없습니다."
                 else "Google 계정으로 로그인해 온라인 방을 이용할 수 있습니다.")
            OutlinedButton(
                onClick = { onGoogleSignIn?.invoke() },
                enabled = onGoogleSignIn != null && !signInBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("onboarding-google-signin")
            ) {
                Text(if (signInBusy) "Google 로그인 진행 중…" else "Google 계정으로 로그인")
            }
            if (authError != null) {
                Text(authError, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryAction(
            label = "데모로 시작",
            onClick = { onContinueDemo(nickname.trim()) },
            modifier = Modifier.fillMaxWidth().testTag("onboarding-continue"),
            enabled = validNickname
        )
    }
}

@Composable
fun CreateRoomScreen(onCreatePreview: (RoomDraft) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var requiresApproval by rememberSaveable { mutableStateOf(true) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val nameError = RoomInputRules.nameError(name)
    val descriptionError = RoomInputRules.descriptionError(description)
    val valid = nameError == null && descriptionError == null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("새 방", style = MaterialTheme.typography.headlineMedium)
        PreviewNotice("만든 방은 이 실행 중에만 보이는 로컬 데모입니다. 서버 저장이나 카카오톡 초대가 진행되지 않습니다.")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; touched = true },
            modifier = Modifier.fillMaxWidth().testTag("create-room-name"),
            label = { Text("방 이름 *") },
            supportingText = {
                Text(if (touched && nameError != null) nameError else "${name.length}/24자")
            },
            isError = touched && nameError != null,
            singleLine = true
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth().testTag("create-room-description"),
            label = { Text("설명 (선택)") },
            supportingText = { Text(descriptionError ?: "${description.length}/120자") },
            isError = descriptionError != null,
            minLines = 2,
            maxLines = 4
        )
        ContentCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("가입 시 방장 승인", style = MaterialTheme.typography.titleSmall)
                    Text("초대받은 사용자의 가입 요청을 방장이 승인", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = requiresApproval,
                    onCheckedChange = { requiresApproval = it },
                    modifier = Modifier.testTag("create-room-approval")
                )
            }
        }
        Text("방은 비공개로 설정됩니다. 실제 참여 권한은 서버 연동 후 검증합니다.", style = MaterialTheme.typography.bodySmall)
        PrimaryAction(
            label = "방 미리보기 생성",
            modifier = Modifier.fillMaxWidth().testTag("create-room-submit"),
            enabled = valid,
            onClick = {
                if (RoomInputRules.valid(name, description)) {
                    onCreatePreview(RoomDraft(name.trim(), description.trim(), requiresApproval))
                }
            }
        )
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("취소")
        }
    }
}
