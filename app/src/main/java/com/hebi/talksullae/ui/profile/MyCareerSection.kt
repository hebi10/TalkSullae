package com.hebi.talksullae.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import com.hebi.talksullae.design.StatStrip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.firebase.CareerRepository
import com.hebi.talksullae.firebase.FirebaseCareerRepository
import com.hebi.talksullae.firebase.MyCareer
import com.hebi.talksullae.firebase.careerProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CareerUiState(
    val loading: Boolean = true,
    val value: MyCareer? = null,
    val error: String? = null
)

internal class MyCareerViewModel(private val repository: CareerRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CareerUiState())
    val state = mutable
    private var user: String? = null
    private var generation = 0L

    fun load(uid: String, force: Boolean = false) {
        if (!force && user == uid) return
        user = uid
        val token = ++generation
        mutable.value = CareerUiState(loading = true)
        viewModelScope.launch {
            try {
                val loaded = repository.getMyCareerPage(null)
                if (user == uid && token == generation) mutable.value = CareerUiState(loading = false, value = loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (user == uid && token == generation) mutable.value =
                    CareerUiState(loading = false, error = "나의 전적을 확인할 수 없습니다. 다시 시도해 주세요.")
            }
        }
    }

    fun loadMore(uid: String) {
        val previous = mutable.value.value ?: return
        val cursor = previous.nextCursor ?: return
        if (user != uid || mutable.value.loading) return
        val token = generation
        mutable.update { it.copy(loading = true,error = null) }
        viewModelScope.launch {
            try {
                val next = repository.getMyCareerPage(cursor)
                if (user == uid && token == generation) mutable.value =
                    CareerUiState(loading = false,value = previous.append(next))
            } catch(e:CancellationException) {
                throw e
            } catch(_:Exception) {
                if(user == uid && token == generation) mutable.update {
                    it.copy(loading=false,error="추가 전적을 확인할 수 없습니다. 다시 시도해 주세요.")
                }
            }
        }
    }

    fun clear() {
        generation++
        user = null
        mutable.value = CareerUiState()
    }
}

@Composable
internal fun MyCareerSection(uid: String, repository: CareerRepository? = null) {
    val repo = remember(repository) { repository ?: FirebaseCareerRepository() }
    val factory = remember(repo) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyCareerViewModel(repo) as T
        }
    }
    val vm: MyCareerViewModel = viewModel(key = "career-$uid", factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(uid) { vm.load(uid) }
    DisposableEffect(vm,uid) { onDispose { vm.clear() } }

    Column(modifier = Modifier.fillMaxWidth().testTag("my-career-summary"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.loading) CircularProgressIndicator()
        state.value?.let { career ->
            if(career.nextCursor == null) {
                val progress = careerProgress(career.totalGames)
                Text("Lv. " + progress.level + " · " + progress.title, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("career-level"))
                LinearProgressIndicator(progress = { (progress.xp % 100L) / 100f },
                    modifier=Modifier.fillMaxWidth().height(8.dp),
                    color=MaterialTheme.colorScheme.primary, trackColor=MaterialTheme.colorScheme.surfaceVariant)
                Text("경험치 " + progress.xp + " XP · 다음 레벨까지 " + (100L - progress.xp % 100L) + " XP",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("아직 모든 기록을 조회하지 않았습니다. 전체 전적과 레벨은 추가 조회 후 확인할 수 있습니다.",
                    style=MaterialTheme.typography.bodySmall)
            }
            StatStrip(listOf(career.totalGames.toString() to "누적 경기",
                career.totalWins.toString() to "승리", career.bestStreak.toString() to "최고 연승"))
            Text("게임별 승률",style=MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "identity" to "너 누구야?", "liar" to "라이어",
                    "forbidden" to "금지어", "balance" to "밸런스 예측"
                ).forEach { (id, label) ->
                    val game = career.games[id]
                    val plays = game?.plays ?: 0L
                    val wins = game?.wins ?: 0L
                    val rate = if (plays == 0L) "기록 없음" else "${((wins.toDouble() / plays) * 100).toInt().coerceIn(0,100)}% 승률"
                    Row(Modifier.fillMaxWidth().padding(vertical=4.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(label, Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        Text("${plays}경기 · ${wins}승 · $rate",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ContentCard {
                Text("획득한 시즌점 " + career.totalSeasonPoints + "점")
                if (career.achievements.isNotEmpty()) {
                    Text("획득 업적 " + career.achievements.size + "개", style=MaterialTheme.typography.titleMedium)
                    val labels = mapOf("first_game" to "첫 게임 완료", "games_10" to "10경기 참가",
                        "wins_5" to "5승 달성", "streak_3" to "3연승", "streak_5" to "5연승",
                        "liar_wins_5" to "라이어 5승", "identity_wins_10" to "정체 추리 10승")
                    career.achievements.mapNotNull { labels[it] }.forEach { Text(it) }
                }
            }
            Text("참여 중인 방 " + career.roomCount + "개 · 조회한 기록 방 " + career.recordedRoomCount + "개",
                style=MaterialTheme.typography.bodySmall)
            Text(if(career.retainedScope) "탈퇴·종료된 방을 포함하여 서버에 보존된 본인 전적만 집계합니다."
                else "현재 참여 중인 방의 전적만 집계합니다.", style=MaterialTheme.typography.bodySmall)
            if(career.nextCursor == null) Text("완료 경기 1회당 10 XP. 서버에 보존된 전적 기준이며 승패나 게임 권한에 영향을 주지 않습니다.",
                style=MaterialTheme.typography.bodySmall)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if(state.value?.nextCursor != null) {
            OutlinedButton(modifier=Modifier.fillMaxWidth().testTag("my-career-more"),
                onClick={vm.loadMore(uid)},enabled=!state.loading) { Text("이전 방 기록 더 불러오기") }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().testTag("my-career-refresh"),
            onClick = { vm.load(uid, force = true) },
            enabled = !state.loading
        ) { Text("전적 새로고침") }
    }
}
