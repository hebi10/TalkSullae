package com.hebi.talksullae.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.design.ProfileAvatar
import com.hebi.talksullae.firebase.NicknamePolicy
import com.hebi.talksullae.firebase.UserProfile

@Composable
fun UserProfileEditorScreen(
    current: UserProfile?,
    firstSetup: Boolean,
    saving: Boolean,
    error: String?,
    onSave: (String, String) -> Unit
) {
    var nickname by rememberSaveable(current?.nickname) {
        mutableStateOf(current?.nickname.orEmpty())
    }
    var avatar by rememberSaveable(current?.avatar) {
        mutableStateOf(current?.avatar ?: NicknamePolicy.avatars.first())
    }
    val valid = NicknamePolicy.isValid(nickname)
    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (firstSetup) "사용할 닉네임을 정해 주세요" else "내 프로필 편집",
                style = MaterialTheme.typography.headlineSmall
            )
            Text("친구에게 보일 이름입니다. Google 계정 이름은 공개하지 않습니다.")
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("닉네임") },
                singleLine = true,
                enabled = !saving,
                isError = nickname.isNotEmpty() && !valid,
                supportingText = { Text("2~16자 · 다른 사람과 같은 닉네임도 사용할 수 있습니다.") },
                modifier = Modifier.fillMaxWidth().testTag("profile-nickname")
            )
            Text("기본 아바타", style = MaterialTheme.typography.titleMedium)
            Text("선택한 아바타: $avatar", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profile-avatar-selection"))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) 1 else 2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NicknamePolicy.avatars.chunked(columns).forEach { options ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEach { option ->
                                FilterChip(
                                    selected = avatar == option,
                                    enabled = !saving,
                                    onClick = { avatar = option },
                                    label = {
                                        Text(option, fontWeight = if (avatar == option) FontWeight.SemiBold else FontWeight.Normal)
                                    },
                                    leadingIcon = { ProfileAvatar(option, Modifier.size(24.dp)) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        .testTag("profile-avatar-$option")
                                )
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryAction(
                label = if (saving) "저장 중…" else "프로필 저장",
                enabled = valid && !saving,
                onClick = { onSave(NicknamePolicy.normalize(nickname), avatar) },
                modifier = Modifier.fillMaxWidth().testTag("profile-save")
            )
        }
    }
}
