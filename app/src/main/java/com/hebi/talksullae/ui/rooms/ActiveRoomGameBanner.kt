package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.game.online.FirestoreOnlineGameSessionRepository
import com.hebi.talksullae.game.online.OnlineGameSessionRepository
import com.hebi.talksullae.game.online.OnlineGameSnapshot

internal object ActiveRoomGameLabels {
    private val games = mapOf(
        "identity" to "너 누구야?",
        "liar" to "라이어 채팅",
        "forbidden" to "금지어 채팅",
        "balance" to "밸런스 예측"
    )
    fun label(gameId: String): String? = games[gameId]

    fun available(snapshot: OnlineGameSnapshot?): Boolean =
        snapshot != null && !snapshot.session.phase.isTerminal &&
            games.containsKey(snapshot.session.gameId)
}

/** Reads public session metadata only. Hidden roles and votes are never rendered. */
@Composable
internal fun ActiveRoomGameBanner(
    roomId: String,
    sessionId: String,
    onResume: (String) -> Unit,
    repository: OnlineGameSessionRepository? = null
) {
    val repo = remember(repository) { repository ?: FirestoreOnlineGameSessionRepository() }
    var snapshot by remember(roomId, sessionId) { mutableStateOf<OnlineGameSnapshot?>(null) }
    var error by remember(roomId, sessionId) { mutableStateOf<String?>(null) }

    DisposableEffect(roomId, sessionId, repo) {
        val listener = repo.watchSession(roomId, sessionId,
            onResult = { snapshot = it; error = null },
            onError = { error = it; snapshot = null }
        )
        onDispose { listener.remove() }
    }

    if (ActiveRoomGameLabels.available(snapshot)) {
        val session = requireNotNull(snapshot).session
        val label = ActiveRoomGameLabels.label(session.gameId) ?: return
        ContentCard(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("active-game-banner")) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(session.phase.wireValue, style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot?.fromCache == true) {
                Text("저장된 게임 상태입니다. 서버 연결 후 최신 단계가 표시됩니다.")
            }
            TextButton(
                onClick = { onResume(session.gameId) },
                modifier = Modifier.testTag("active-game-resume")
            ) { Text("진행 중 게임 이어하기") }
        }
    } else if (error != null) {
        Text(
            "진행 중 게임 상태를 확인할 수 없습니다. 게임 탭에서 다시 확인해 주세요.",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error
        )
    }
}
