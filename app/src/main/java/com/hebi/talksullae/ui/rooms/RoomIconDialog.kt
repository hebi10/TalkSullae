package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.design.ProfileAvatar
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.firebase.CloudRoom

@Composable
internal fun RoomIconDialog(room:CloudRoom,working:Boolean,error:String?,onDismiss:()->Unit,onSave:(String?,Long)->Unit){
    var selected by rememberSaveable(room.id){mutableStateOf(room.roomIcon)}
    val revision=rememberSaveable(room.id){room.roomRevision}
    val stale=revision!=room.roomRevision
    AlertDialog(onDismissRequest=onDismiss,title={Text("방 아이콘 변경")},shape=RoundedCornerShape(8.dp),text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("방 목록에 표시할 아이콘을 선택하세요.")
            Text("선택: " + (selected ?: "이름 첫 글자"),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.testTag("room-icon-selection"))
            FilterChip(selected=selected==null,onClick={selected=null},enabled=!working,
                label={Text("이름 첫 글자")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                shape=RoundedCornerShape(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()){
                val columns = if (maxWidth < 240.dp || LocalDensity.current.fontScale >= 1.3f) 1 else 2
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("부엉이","토끼","고양이","강아지","곰","여우","판다","펭귄")
                        .chunked(columns).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            group.forEach { icon ->
                                FilterChip(selected=selected==icon,onClick={selected=icon},enabled=!working,
                                    label={Text(icon)},leadingIcon={ProfileAvatar(icon,Modifier.size(22.dp))},
                                    shape=RoundedCornerShape(8.dp),
                                    modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("room-icon-"+icon))
                            }
                        }
                    }
                }
            }
            if(stale)Text("방 정보가 변경됐습니다. 다시 열어 주세요.",color=MaterialTheme.colorScheme.error)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={PrimaryAction(label=if(working)"저장 중…" else "저장",onClick={onSave(selected,revision)},enabled=!working&&!stale,modifier=Modifier.testTag("room-icon-save"))},
        dismissButton={TextButton(onClick=onDismiss,enabled=!working){Text("취소")}})
}
