package com.hebi.talksullae

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.ui.rooms.RoomRecordsContent
import com.hebi.talksullae.ui.rooms.RoomRecordsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Live07LargeTextRecordsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun seasonNavigationRemainsAvailableAt200PercentFontScale() {
        var previous = 0
        var next = 0
        rule.setContent {
            val originalDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(originalDensity.density, 2f)
            ) {
                TalkSullaeTheme(darkTheme = true) {
                    RoomRecordsContent(
                        roomName = "모임 이름이 매우 길어서 두 줄이 되어야 하는 기록방",
                        state = RoomRecordsState(
                            seasonId = "2026-11", closed = false, loading = false
                        ),
                        names = emptyMap(),
                        onPrevious = { previous++ },
                        onNext = { next++ },
                        canPrevious = true,
                        canNext = true,
                        onBack = {}
                    )
                }
            }
        }
        rule.onNodeWithTag("records-season-heading").assertExists()
        rule.onNodeWithTag("records-previous-season").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("records-next-season").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, previous)
            assertEquals(1, next)
        }
    }
}
