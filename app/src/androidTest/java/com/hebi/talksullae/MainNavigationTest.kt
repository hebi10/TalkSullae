package com.hebi.talksullae

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun roomNavigationShowsRoomTabs() {
        composeRule.onNodeWithTag("room-list").assertExists()
        composeRule.onNodeWithTag("room-friday").performClick()
        composeRule.onNodeWithTag("room-screen").assertExists()
        composeRule.onNodeWithTag("room-tab-chat").assertExists()
        composeRule.onNodeWithTag("room-tab-games").performClick()
        composeRule.onNodeWithTag("game-list").assertExists()
    }

    @Test
    fun recordsBottomTabOpensPreview() {
        composeRule.onNodeWithTag("main-tab-records").performClick()
        composeRule.onNodeWithTag("ranking-screen").assertExists()
    }
}
