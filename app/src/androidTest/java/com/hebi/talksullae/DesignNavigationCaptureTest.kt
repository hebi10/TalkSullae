package com.hebi.talksullae

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Actual MainActivity navigation, demo account only. */
class DesignNavigationCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun setup(){DesignCaptureIO.requireMode()}
    @Test fun settingsSafetyAndDeletion(){
        compose.onNodeWithTag("welcome-start").performClick()
        compose.onNodeWithTag("onboarding-continue").performClick()
        compose.onNodeWithTag("main-tab-profile").performClick()
        compose.onNodeWithTag("profile-settings-open").performScrollTo().performClick()
        compose.onNodeWithText("안전 및 지원").assertExists()
        compose.onNodeWithTag("settings-theme-switch").assertExists()
        compose.onNodeWithTag("privacy-policy-open").performScrollTo().assertIsDisplayed()
        compose.waitForIdle();DesignCaptureIO.save("D32_settings_demo")
        compose.onNodeWithTag("safety-report-open").performScrollTo().performClick()
        compose.onNodeWithTag("safety-report-screen").assertIsDisplayed()
        compose.onNodeWithText("신고 전 확인").assertExists()
        compose.onNodeWithTag("safety-report-send").assertIsNotEnabled()
        compose.waitForIdle();DesignCaptureIO.save("D33_report_demo")
        // Return through the existing system back handler, not a test-only account.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        // The request-open tag launches the external policy URL; use the in-app route.
        compose.onNodeWithTag("account-deletion-in-app").performScrollTo().performClick()
        compose.onNodeWithTag("account-deletion-screen").assertIsDisplayed()
        compose.onNodeWithTag("account-deletion-reauth").assertIsNotEnabled()
        compose.onNodeWithTag("account-deletion-open-confirm").assertIsNotEnabled()
        compose.onNodeWithText("삭제 범위").assertExists()
        compose.onNodeWithText("요청 전 확인").assertExists()
        compose.onNodeWithTag("account-deletion-open-confirm").performScrollTo().assertIsDisplayed()
        compose.waitForIdle();DesignCaptureIO.save("D34_delete_demo")
    }
}
