package com.hebi.talksullae.ui.rooms

import com.hebi.talksullae.game.core.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStatusUiTest {
    @Test fun lobbyReadinessDistinguishesMinimumReadyAndCache() {
        assertTrue(lobbyReadinessLabel(2,2,4,GamePhase.WAITING,false).contains("2명이"))
        assertTrue(lobbyReadinessLabel(4,3,4,GamePhase.WAITING,false).contains("3/4명"))
        assertTrue(lobbyReadinessLabel(4,4,4,GamePhase.READY,false).contains("방장이"))
        assertTrue(lobbyReadinessLabel(4,4,4,GamePhase.READY,true).contains("서버 상태"))
    }
    @Test fun countdownAndKoreanPhaseAreReadable() {
        assertEquals("참가 대기",gamePhaseLabel(GamePhase.WAITING))
        assertTrue(gameDeadlineLabel(65).contains("1:05"))
        assertTrue(gameDeadlineLabel(0).contains("서버 결과"))
    }
    @Test fun equalSeasonPointsShareCompetitionRank() {
        val rows = listOf(SeasonRank("b",100,3,2),SeasonRank("a",100,1,1),SeasonRank("c",80,2,1))
        assertEquals(listOf(1,1,3),competitionRanks(rows).map{it.first})
    }
}
