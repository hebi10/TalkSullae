package com.hebi.talksullae

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        compose.onNodeWithTag("profile-avatar-고양이").performScrollTo().performClick()
        compose.onNodeWithTag("profile-avatar-selection").assertTextEquals("선택한 아바타: 고양이")
        compose.onNodeWithTag("profile-save").performScrollTo().assertIsEnabled().performClick()
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

    @Test fun allEightAvatarsAndSaveRemainReachableAtLargeFontScale() {
        var savedAvatar = ""
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                TalkSullaeTheme(darkTheme = false) {
                    UserProfileEditorScreen(current = UserProfile("프로필", "부엉이"),
                        firstSetup = false, saving = false, error = null,
                        onSave = { _, avatar -> savedAvatar = avatar })
                }
            }
        }
        listOf("부엉이","토끼","고양이","강아지","곰","여우","판다","펭귄").forEach { avatar ->
            compose.onNodeWithTag("profile-avatar-$avatar").assertExists()
        }
        compose.onNodeWithTag("profile-avatar-펭귄").performScrollTo().performClick()
        compose.onNodeWithTag("profile-save").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("펭귄", savedAvatar) }
    }
    @Test fun savingDisablesProfileInputs() {
        compose.setContent {
            TalkSullaeTheme {
                UserProfileEditorScreen(UserProfile("민지", "고양이"), false, true, null, { _, _ -> })
            }
        }
        compose.onNodeWithTag("profile-nickname").assertIsNotEnabled()
        compose.onNodeWithTag("profile-avatar-고양이").assertIsNotEnabled()
        compose.onNodeWithTag("profile-save").assertIsNotEnabled()
    }
}
