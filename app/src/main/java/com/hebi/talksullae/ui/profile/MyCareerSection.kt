package com.hebi.talksullae.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    fun load(uid: String, force: Boolean = false) {
        if (!force && user == uid) return
        user = uid
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.getMyCareer()
                if (user == uid) mutable.value = CareerUiState(loading = false, value = loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (user == uid) mutable.update {
                    it.copy(loading = false, error = "나의 전적을 확인할 수 없습니다. 다시 시도해 주세요.")
                }
            }
        }
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

    ContentCard(modifier = Modifier.testTag("my-career-summary")) {
        Text("나의 전체 전적", style = MaterialTheme.typography.titleMedium)
        if (state.loading) CircularProgressIndicator()
        state.value?.let { career ->
            Text("참여 중인 방 " + career.roomCount + "개 · 누적 경기 " + career.totalGames + "회")
            Text("승리 " + career.totalWins + "회 · 최고 연승 " + career.bestStreak + "회")
            Text("획득한 시즌점 " + career.totalSeasonPoints + "점")
            if (career.achievements.isNotEmpty()) {
                Text("업적 " + career.achievements.size + "개")
                val labels = mapOf("first_game" to "첫 게임 완료", "games_10" to "10경기 참가",
                    "wins_5" to "5승 달성", "streak_3" to "3연승", "streak_5" to "5연승",
                    "liar_wins_5" to "라이어 5승", "identity_wins_10" to "정체 추리 10승")
                career.achievements.mapNotNull { labels[it] }.forEach { Text(it) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "identity" to "너 누구야?", "liar" to "라이어",
                    "forbidden" to "금지어", "balance" to "밸런스 예측"
                ).forEach { (id, label) ->
                    val game = career.games[id]
                    val plays = game?.plays ?: 0L
                    val wins = game?.wins ?: 0L
                    val rate = if (plays == 0L) "기록 없음" else "${((wins.toDouble() / plays) * 100).toInt().coerceIn(0,100)}% 승률"
                    Text("$label · ${plays}경기 · ${wins}승 · $rate")
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().testTag("my-career-refresh"),
            onClick = { vm.load(uid, force = true) },
            enabled = !state.loading
        ) { Text("전적 새로고침") }
    }
}
