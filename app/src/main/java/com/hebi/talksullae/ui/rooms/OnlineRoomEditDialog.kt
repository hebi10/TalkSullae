package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.data.RoomInputRules
import com.hebi.talksullae.firebase.CloudRoom

@Composable
internal fun OnlineRoomEditDialog(room: CloudRoom, working: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (String, String, Boolean, Long) -> Unit) {
    var name by rememberSaveable(room.id) { mutableStateOf(room.name) }
    var description by rememberSaveable(room.id) { mutableStateOf(room.description) }
    var approval by rememberSaveable(room.id) { mutableStateOf(room.requiresApproval) }
    val revision = rememberSaveable(room.id) { room.roomRevision }
    val stale = revision != room.roomRevision
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("방 정보 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("방 이름") },
                    enabled = !working, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("room-edit-name"),
                    isError = RoomInputRules.nameError(name) != null,
                    supportingText = { Text(RoomInputRules.nameError(name) ?: "${name.length}/24자") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("설명 (선택)") },
                    enabled = !working, maxLines = 4, modifier = Modifier.fillMaxWidth().testTag("room-edit-description"),
                    isError = RoomInputRules.descriptionError(description) != null,
                    supportingText = { Text(RoomInputRules.descriptionError(description) ?: "${description.length}/120자") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("방장 가입 승인", modifier = Modifier.weight(1f))
                    Switch(checked = approval, onCheckedChange = { approval = it }, enabled = !working)
                }
                Text("가입 승인 변경은 이후의 신청에 적용됩니다. 기존 대기 요청은 별도로 처리해야 합니다.",
                    style = MaterialTheme.typography.bodySmall)
                if (stale) Text("다른 기기에서 방 정보가 변경됐습니다. 닫고 다시 열어 주세요.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, description, approval, revision) },
            enabled = !working && !stale && RoomInputRules.valid(name, description),
            modifier = Modifier.testTag("room-edit-save")) { Text(if (working) "저장 중…" else "저장") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("취소") } })
}
