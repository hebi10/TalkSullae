package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MainNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun openDemo() {
        composeRule.onNodeWithTag("welcome-start").assertExists().performClick()
        composeRule.onNodeWithTag("onboarding-nickname").assertExists()
        composeRule.onNodeWithTag("onboarding-continue").performClick()
        composeRule.onNodeWithTag("room-list").assertExists()
    }

    @Test
    fun onboardingNavigatesToRoomsAndGames() {
        openDemo()
        composeRule.onNodeWithTag("room-friday").performClick()
        composeRule.onNodeWithTag("room-screen").assertExists()
        composeRule.onNodeWithTag("room-tab-chat").assertExists()
        composeRule.onNodeWithTag("room-tab-games").performClick()
        composeRule.onNodeWithTag("game-list").assertExists()
    }

    @Test
    fun recordsBottomTabOpensPreview() {
        openDemo()
        composeRule.onNodeWithTag("main-tab-records").performClick()
        composeRule.onNodeWithTag("ranking-screen").assertExists()
    }

    @Test
    fun roomCreationRequiresValidNameAndShowsLocalRoom() {
        openDemo()
        composeRule.onNodeWithTag("rooms-create").performClick()
        composeRule.onNodeWithTag("create-room-submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("create-room-name").performTextInput("주말 톡술래")
        composeRule.onNodeWithTag("create-room-submit").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("room-screen").assertExists()
        composeRule.onNodeWithTag("room-members-open").assertExists()
    }

    @Test
    fun invitePreviewCanShowApprovalPending() {
        openDemo()
        composeRule.onNodeWithTag("room-friday").performClick()
        composeRule.onNodeWithTag("room-invite-open").performClick()
        composeRule.onNodeWithTag("invite-screen").assertExists()
        composeRule.onNodeWithTag("invite-switch-role").performClick()
        composeRule.onNodeWithTag("guest-join-request").performClick()
        composeRule.onNodeWithTag("join-state-PENDING").assertExists()
        composeRule.onNodeWithText("가입 승인을 기다리는 중").assertExists()
    }

    @Test
    fun roomOwnerCanConfirmLocalKick() {
        openDemo()
        composeRule.onNodeWithTag("room-friday").performClick()
        composeRule.onNodeWithTag("room-members-open").performClick()
        composeRule.onNodeWithTag("members-screen").assertExists()
        composeRule.onNodeWithTag("member-actions-minji").performClick()
        composeRule.onNodeWithText("강퇴 시연").performClick()
        composeRule.onNodeWithTag("confirm-kick").performClick()
        composeRule.onNodeWithTag("member-minji").assertDoesNotExist()
    }

    @Test
    fun ordinaryMemberDoesNotSeeOwnerInviteControls() {
        openDemo()
        composeRule.onNodeWithTag("room-friends").performClick()
        composeRule.onNodeWithTag("room-invite-open").assertDoesNotExist()
        composeRule.onNodeWithTag("room-members-open").performClick()
        composeRule.onNodeWithTag("members-screen").assertExists()
        composeRule.onNodeWithTag("members-invite").assertDoesNotExist()
        composeRule.onNodeWithTag("member-actions-minji").assertDoesNotExist()
    }

    @Test
    fun roomListCanPreviewEmptyState() {
        openDemo()
        composeRule.onNodeWithTag("rooms-state-EMPTY").performClick()
        composeRule.onNodeWithText("아직 참여한 방이 없습니다").assertExists()
        composeRule.onNodeWithTag("rooms-create").assertExists()
    }
}
