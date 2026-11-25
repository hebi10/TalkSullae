package com.hebi.talksullae.ui.rooms

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.ProfileAvatar
import com.hebi.talksullae.design.StatStrip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.firebase.FriendRecords

internal fun rateLabel(hits: Long, attempts: Long): String =
    if (attempts == 0L) "기록 없음" else "$hits/$attempts (" + ((hits * 100L) / attempts) + "%)"

@Composable
internal fun FriendRecordsDialog(name: String, value: FriendRecords?, loading: Boolean, error: String?,
    onRetry: () -> Unit, onDismiss: () -> Unit, onCompare: () -> Unit = {}, avatar: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ProfileAvatar(avatar, Modifier.size(36.dp))
            Text("$name · 방 전적",style=MaterialTheme.typography.titleLarge)
        } },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                .testTag("friend-records-dialog"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("현재 같은 방의 게임 기록만 표시합니다.", style = MaterialTheme.typography.bodySmall)
                if (loading) CircularProgressIndicator()
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("friend-records-retry")) { Text("다시 조회") }
                }
                value?.let { record ->
                    Text("친구의 방 누적 전적", style = MaterialTheme.typography.titleMedium)
                    StatStrip(listOf("${record.totalGames}" to "경기", "${record.totalWins}" to "승리",
                        (if (record.totalGames == 0L) "기록 없음" else "${(record.totalWins * 100L) / record.totalGames}%") to "승률"))
                    Text("최고 ${record.bestStreak}연승 · 누적 시즌점 ${record.totalSeasonPoints}점 · 업적 ${record.achievements.size}개")
                    val labels = listOf("identity" to "너 누구야?", "liar" to "라이어 채팅",
                        "forbidden" to "금지어 채팅", "balance" to "밸런스 예측")
                    Text("게임별 전적", style = MaterialTheme.typography.titleMedium)
                    labels.forEach { (id, label) ->
                        record.games[id]?.let {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(label, Modifier.weight(1f))
                                Text("${it.plays}경기 · ${it.wins}승",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (!record.comparisonLoaded) {
                        TextButton(onClick = onCompare, enabled = !loading, modifier = Modifier.testTag("friend-load-comparison")) {
                            Text("함께한 경기 상세 비교")
                        }
                    } else {
                    Text("함께한 경기", style = MaterialTheme.typography.titleMedium)
                    Text("내 최근 ${record.reviewedGames}경기 기준" +
                        if (record.capped) " (최대 30경기, 전체 누적 아님)" else "")
                    val pair = record.comparison
                    ContentCard {
                        Text("함께한 경기 ${pair.sharedGames}회", modifier = Modifier.testTag("friend-shared-games"),
                            style=MaterialTheme.typography.titleMedium)
                        StatStrip(listOf("${pair.myHigher}" to "내 점수 우위", "${pair.friendHigher}" to "친구 우위",
                            "${pair.ties}" to "동점"))
                    }
                    labels.forEach { (id, label) -> Text("$label · ${pair.games[id] ?: 0L}회") }
                    Text("정체 추리", style = MaterialTheme.typography.titleMedium)
                    Text("내가 친구 정체를 맞힌 비율 " + rateLabel(pair.myIdentityHits, pair.myIdentityAttempts))
                    if (record.identitySuppressed) Text("친구의 정체 추리 비율은 비교 기록 3회 이상일 때 표시합니다.")
                    else Text("친구가 내 정체를 맞힌 비율 " + rateLabel(pair.friendIdentityHits, pair.friendIdentityAttempts))
                    Text("밸런스 선택 일치율", style = MaterialTheme.typography.titleMedium)
                    Text(if (record.balanceSuppressed) "비교 기록 3회 이상 필요" else rateLabel(pair.balanceMatched, pair.balanceCompared))
                    Text("미제출 또는 보존되지 않은 비밀 기록은 비율 계산에서 제외합니다. 점수 비교는 같은 경기 내 원점수 비교입니다.",
                        style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
