package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.ui.rooms.RecentGameRecord
import com.hebi.talksullae.ui.rooms.RoomRecordEvent
import com.hebi.talksullae.ui.rooms.RoomRecordsContent
import com.hebi.talksullae.ui.rooms.RoomRecordsState
import com.hebi.talksullae.ui.rooms.SeasonRank
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoomRecordsComposeTest {
    @get:Rule val compose=createComposeRule()
    private fun sample()=RoomRecordsState(
        seasonId="2026-11",closed=false,
        ranking=listOf(
            SeasonRank("owner",225,3,2),
            SeasonRank("friend",120,2,1)
        ),
        totalGames=3,totalWins=2,bestStreak=2,
        achievements=setOf("first_game","wins_5"),
        recentGames=listOf(RecentGameRecord("liar",110,true,"2026-11")),
        events=listOf(RoomRecordEvent("liar","2026-11",4)),
        loading=false
    )
    @Test fun leaderboardAndPersonalAchievementsRenderWithoutPrivateOtherStats(){
        compose.setContent {
            TalkSullaeTheme(darkTheme=true){
                RoomRecordsContent("친구방",sample(),
                    mapOf("owner" to "민지","friend" to "지훈"),
                    onPrevious={},onNext={},canPrevious=true,
                    canNext=false,onBack={}
                )
            }
        }
        compose.onNodeWithTag("room-records-screen").assertExists()
        compose.onNodeWithText("경기 3회 · 승리 2회 · 최고 연승 2회").assertExists()
        compose.onNodeWithText("첫 게임 완료").assertExists()
        compose.onNodeWithText("5승 달성").assertExists()
        compose.onNodeWithText("1위 · 민지").assertExists()
        compose.onNodeWithText("2위 · 지훈").assertExists()
    }
    @Test fun previousSeasonControlInvokesOnlyNavigationCallback(){
        var previous=0
        compose.setContent {
            TalkSullaeTheme(darkTheme=true){
                RoomRecordsContent("친구방",sample().copy(closed=true),
                    emptyMap(),onPrevious={previous++},onNext={},
                    canPrevious=true,canNext=false,onBack={}
                )
            }
        }
        compose.onNodeWithTag("records-previous-season").performClick()
        compose.runOnIdle{assertEquals(1,previous)}
    }
}
