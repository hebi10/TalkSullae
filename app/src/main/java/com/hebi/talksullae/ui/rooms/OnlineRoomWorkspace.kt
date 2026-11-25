package com.hebi.talksullae.ui.rooms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.ProfileAvatar
import com.hebi.talksullae.firebase.CloudRoom

internal data class OnlineGameInfo(
    val id: String, val name: String, val players: String, val duration: String, val rules: String
)

internal val onlineGameCatalog = listOf(
    OnlineGameInfo("identity","너 누구야?","4~10명","약 5분","익명으로 대화하고 친구의 정체를 추리하세요."),
    OnlineGameInfo("liar","라이어 채팅","4~10명","약 4~6분","주제를 모르는 라이어를 대화와 투표로 찾아내세요."),
    OnlineGameInfo("forbidden","금지어 채팅","3~10명","약 3분","친구의 금지어를 유도하고 끝까지 살아남으세요."),
    OnlineGameInfo("balance","밸런스 예측","2~10명","실시간 약 3분 · 비동기 단계별 최대 24시간","선택지를 고르고 친구의 선택을 예측하세요.")
)

internal fun roomInitial(name: String): String = name.trim().take(1).ifEmpty { "방" }

@Composable
internal fun RoomListAvatar(name: String, icon: String? = null) {
    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (icon == null) Text(roomInitial(name), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium)
        else ProfileAvatar(icon, Modifier.size(32.dp))
    }
}

@Composable
internal fun OnlineRoomWorkspace(
    room: CloudRoom, section: String, onSection: (String) -> Unit,
    onRoomList: () -> Unit, content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("live-room-shell")) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize().testTag("live-room-expanded")) {
                Column(Modifier.width(184.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRoomList, modifier = Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text("방 목록")
                    }
                    RoomListAvatar(room.name,room.roomIcon)
                    Text(room.name, style = MaterialTheme.typography.titleLarge)
                    Text("참가 ${room.memberCount}/20명", style = MaterialTheme.typography.bodyMedium)
                    if (room.description.isNotBlank()) Text(room.description, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    listOf("chat" to "채팅","games" to "게임","records" to "기록","members" to "멤버 관리").forEach { (id,label) ->
                        OutlinedButton(onClick={onSection(id)}, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                            .testTag("live-room-tab-$id")) {
                            Text(if(section==id) "현재 · $label" else label)
                        }
                    }
                    if (room.activeGameSessionId != null) Text("진행 중인 게임 있음", style=MaterialTheme.typography.labelMedium)
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize().testTag("live-room-compact")) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick = onRoomList, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "방 목록")
                    }
                    RoomListAvatar(room.name,room.roomIcon)
                    Column(Modifier.weight(1f)) {
                        Text(room.name, style=MaterialTheme.typography.titleMedium)
                        Text("참가 ${room.memberCount}/20명", style=MaterialTheme.typography.bodySmall)
                    }
                }
                Box(Modifier.padding(horizontal=16.dp)) { LiveRoomSectionTabs(section,onSection) }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
internal fun OnlineGameCatalog(room: CloudRoom, onResume:(String)->Unit, onOpen:(String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag("live-game-list"), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("게임 선택", style=MaterialTheme.typography.titleLarge)
        Text("함께 플레이할 게임을 선택하세요.", style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        room.activeGameSessionId?.let { ActiveRoomGameBanner(room.id,it,onResume) }
        onlineGameCatalog.forEach { game ->
            ContentCard(Modifier.testTag("live-game-card-${game.id}")) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.Top) {
                    Icon(when(game.id) {
                        "identity" -> Icons.Default.Search
                        "liar" -> Icons.Default.VisibilityOff
                        "forbidden" -> Icons.Default.Block
                        else -> Icons.Default.Balance
                    }, contentDescription=null, modifier=Modifier.size(32.dp))
                    Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(game.name,style=MaterialTheme.typography.titleMedium)
                        Text(game.rules,style=MaterialTheme.typography.bodyMedium)
                        Text("${game.players} · ${game.duration}",style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                com.hebi.talksullae.design.PrimaryAction("게임 열기", onClick={onOpen(game.id)},
                    modifier=Modifier.fillMaxWidth().testTag("live-game-${game.id}"))
            }
        }
    }
}
