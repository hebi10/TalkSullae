package com.hebi.talksullae

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.CloudRoom
import com.hebi.talksullae.ui.rooms.OnlineRoomEditDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TS08RoomEditComposeTest {
    @get:Rule val compose = createComposeRule()
    private val initial = CloudRoom("a".repeat(20), "기존 방", "설명", "owner", 2, 0, true, roomRevision = 2L)

    @Test fun editSendsValidatedValuesWithOriginalServerRevision() {
        var result: List<Any>? = null
        compose.setContent {
            TalkSullaeTheme {
                OnlineRoomEditDialog(initial, false, null, onDismiss = {},
                    onSave = { n, d, a, rev -> result = listOf(n,d,a,rev) })
            }
        }
        val field=compose.onNodeWithTag("room-edit-name").performScrollTo()
        field.performTextClearance()
        compose.onNodeWithTag("room-edit-save").assertIsNotEnabled()
        field.performTextInput("수정 방")
        compose.onNodeWithTag("room-edit-save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("수정 방","설명",true,2L), result) }
    }

    @Test fun editCannotOverwriteNewerRevisionFromOtherDevice() {
        var room by mutableStateOf(initial)
        var calls=0
        compose.setContent {
            TalkSullaeTheme(darkTheme = true) {
                OnlineRoomEditDialog(room,false,null,onDismiss={},onSave={_,_,_,_->calls++})
            }
        }
        compose.onNodeWithTag("room-edit-save").assertIsEnabled()
        compose.runOnIdle { room = room.copy(name = "다른 기기 수정", roomRevision = 3L) }
        compose.onNodeWithTag("room-edit-save").assertIsNotEnabled()
        compose.onNodeWithText("다른 기기에서 방 정보가 변경됐습니다. 닫고 다시 열어 주세요.").assertExists()
        assertEquals(0,calls)
    }
}
