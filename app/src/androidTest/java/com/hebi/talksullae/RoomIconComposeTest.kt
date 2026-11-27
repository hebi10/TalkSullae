package com.hebi.talksullae

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.CloudRoom
import com.hebi.talksullae.ui.rooms.RoomIconDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoomIconComposeTest {
    @get:Rule val compose=createComposeRule()
    private val room=CloudRoom("c".repeat(20),"친구 방","","owner",4,0,true,roomRevision=7)

    @Test fun selectedIconSavesWithOriginalServerRevision() {
        var result: Pair<String?,Long>?=null
        compose.setContent {
            TalkSullaeTheme {
                RoomIconDialog(room,false,null,{}, {icon,revision->result=icon to revision})
            }
        }
        compose.onNodeWithTag("room-icon-펭귄").performScrollTo().performClick()
        compose.onNodeWithTag("room-icon-selection").assertTextEquals("선택: 펭귄")
        compose.onNodeWithTag("room-icon-save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("펭귄" to 7L,result) }
    }
    @Test fun staleRoomRevisionDisablesIconSave() {
        val current=mutableStateOf(room)
        compose.setContent {
            TalkSullaeTheme(darkTheme=true) {
                RoomIconDialog(current.value,false,null,{},{_,_->})
            }
        }
        compose.onNodeWithTag("room-icon-save").assertIsEnabled()
        compose.runOnIdle { current.value=room.copy(roomRevision=8) }
        compose.onNodeWithTag("room-icon-save").assertIsNotEnabled()
    }
}
