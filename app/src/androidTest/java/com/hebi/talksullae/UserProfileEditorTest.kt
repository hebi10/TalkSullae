package com.hebi.talksullae

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.UserProfile
import com.hebi.talksullae.ui.entry.UserProfileEditorScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UserProfileEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newProfileRequiresValidNicknameAndSavesAvatar() {
        var savedName = ""
        var savedAvatar = ""
        compose.setContent {
            TalkSullaeTheme {
                UserProfileEditorScreen(
                    current = null, firstSetup = true, saving = false, error = null,
                    onSave = { nickname, avatar ->
                        savedName = nickname
                        savedAvatar = avatar
                    }
                )
            }
        }
        compose.onNodeWithTag("profile-save").assertIsNotEnabled()
        compose.onNodeWithTag("profile-nickname").performTextInput("  해비  ")
        compose.onNodeWithTag("profile-avatar-고양이").performClick()
        compose.onNodeWithTag("profile-save").assertIsEnabled().performClick()
        assertEquals("해비", savedName)
        assertEquals("고양이", savedAvatar)
    }

    @Test fun existingProfileLoadsForEditing() {
        compose.setContent {
            TalkSullaeTheme {
                UserProfileEditorScreen(
                    current = UserProfile("원래이름", "토끼"),
                    firstSetup = false, saving = false, error = null,
                    onSave = { _, _ -> }
                )
            }
        }
        compose.onNodeWithTag("profile-save").assertIsEnabled()
    }
}
