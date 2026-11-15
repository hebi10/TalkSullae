package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.ui.rooms.LiveRoomSectionTabs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LiveRoomSectionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun roomTabsExposeServerChatGamesRecordsAndManagement() {
        var selected = "chat"
        compose.setContent {
            TalkSullaeTheme {
                LiveRoomSectionTabs(selected, onSelect = { selected = it })
            }
        }
        compose.onNodeWithTag("live-room-tab-games").performClick()
        assertEquals("games", selected)
        compose.onNodeWithTag("live-room-tab-records").performClick()
        assertEquals("records", selected)
        compose.onNodeWithTag("live-room-tab-members").performClick()
        assertEquals("members", selected)
    }
}
