package com.hebi.talksullae.ui.rooms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hebi.talksullae.game.core.GamePhase

internal fun gamePhaseLabel(phase: GamePhase?): String = when (phase) {
    GamePhase.WAITING -> "참가 대기"
    GamePhase.READY -> "시작 준비 완료"
    GamePhase.ASSIGNING -> "역할 배정 중"
    GamePhase.PLAYING -> "게임 진행 중"
    GamePhase.VOTING -> "추리·투표 중"
    GamePhase.REVEAL -> "결과 공개"
    GamePhase.FINISHED -> "게임 종료"
    GamePhase.CANCELLED -> "게임 취소"
    null -> "확인 중"
}

internal fun lobbyReadinessLabel(count: Int, ready: Int, minimum: Int, phase: GamePhase?, fromCache: Boolean): String = when {
    fromCache -> "서버 상태를 확인하고 있습니다."
    count < minimum -> "시작하려면 ${minimum - count}명이 더 참가해야 합니다."
    ready < count -> "준비 완료 $ready/$count명 · 모든 참가자의 준비를 기다립니다."
    phase != GamePhase.READY -> "서버에서 시작 가능 여부를 확인 중입니다."
    else -> "모든 참가자가 준비했습니다. 방장이 시작할 수 있습니다."
}

internal fun gameDeadlineLabel(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    if (safe == 0L) return "시간 종료 · 서버 결과 확인 중"
    val minutes = safe / 60
    val rest = (safe % 60).toString().padStart(2, '0')
    return "남은 시간 $minutes:$rest · 서버 마감 기준"
}

@Composable
internal fun GameDeadlineStatus(seconds: Long) {
    Text(gameDeadlineLabel(seconds),
        style = MaterialTheme.typography.titleMedium,
        color = if (seconds in 0L..30L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}
