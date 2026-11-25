package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.hebi.talksullae.design.ProfileAvatar
import com.hebi.talksullae.firebase.CloudRoom

@Composable
internal fun RoomIconDialog(room:CloudRoom,working:Boolean,error:String?,onDismiss:()->Unit,onSave:(String?,Long)->Unit){
    var selected by rememberSaveable(room.id){mutableStateOf(room.roomIcon)}
    val revision=rememberSaveable(room.id){room.roomRevision}
    val stale=revision!=room.roomRevision
    AlertDialog(onDismissRequest=onDismiss,title={Text("방 아이콘 변경")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("방 목록에 표시할 아이콘을 선택하세요.")
            OutlinedButton(onClick={selected=null},enabled=!working,modifier=Modifier.fillMaxWidth()){
                Text(if(selected==null)"선택 · 이름 첫 글자" else "이름 첫 글자")
            }
            listOf("부엉이","토끼","고양이","강아지","곰","여우","판다","펭귄").forEach{icon->
                OutlinedButton(onClick={selected=icon},enabled=!working,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("room-icon-"+icon)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        ProfileAvatar(icon,Modifier.size(24.dp))
                        Text(if(selected==icon)"선택 · $icon" else icon)
                    }
                }
            }
            if(stale)Text("방 정보가 변경됐습니다. 다시 열어 주세요.",color=MaterialTheme.colorScheme.error)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(onClick={onSave(selected,revision)},enabled=!working&&!stale){Text("저장")}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!working){Text("취소")}})
}
