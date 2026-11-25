package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.firebase.CloudRoom
import com.hebi.talksullae.ui.rooms.OnlineRoomWorkspace
import com.hebi.talksullae.ui.rooms.OnlineGameCatalog
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

    @Test fun compactWorkspaceHasRoomNavigationAndCatalog() {
        var selected = ""
        val room=CloudRoom("a".repeat(20),"친구 방","","owner",4,0,true)
        compose.setContent {
            TalkSullaeTheme {
                Box(Modifier.width(360.dp)) {
                    OnlineRoomWorkspace(room,"games",{selected=it},{}) {
                        OnlineGameCatalog(room,{}, {selected=it})
                    }
                }
            }
        }
        compose.onNodeWithTag("live-room-compact").assertExists()
        compose.onNodeWithTag("live-game-card-identity").assertExists()
        compose.onNodeWithTag("live-room-tab-members").performClick()
        assertEquals("members",selected)
    }
}
