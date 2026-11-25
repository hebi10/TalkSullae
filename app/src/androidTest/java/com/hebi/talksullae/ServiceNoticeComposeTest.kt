package com.hebi.talksullae

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.ServiceNotice
import com.hebi.talksullae.ui.entry.ServiceNoticeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ServiceNoticeComposeTest {
    @get:Rule val compose=createComposeRule()
    private val notice=ServiceNotice("1","1","이용약관 확인용 문구","개인정보 확인용 문구")

    @Test fun consentRequiresTwoIndependentConfirmations() {
        var accepted=0
        compose.setContent {
            TalkSullaeTheme {
                ServiceNoticeScreen(notice,false,null,{accepted++},{},{},"test-user")
            }
        }
        compose.onNodeWithTag("service-notice-continue").assertIsNotEnabled()
        compose.onNodeWithTag("terms-accept").performScrollTo().performClick()
        compose.onNodeWithTag("service-notice-continue").assertIsNotEnabled()
        compose.onNodeWithTag("privacy-read").performScrollTo().performClick()
        compose.onNodeWithTag("service-notice-continue").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,accepted) }
    }

    @Test fun accountOrVersionChangeClearsUnsubmittedConfirmations() {
        val account=mutableStateOf("first")
        val policy=mutableStateOf(notice)
        val busy=mutableStateOf(false)
        compose.setContent {
            TalkSullaeTheme(darkTheme=true) {
                ServiceNoticeScreen(policy.value,busy.value,null,{},{},{},account.value)
            }
        }
        fun confirmBoth() {
            compose.onNodeWithTag("terms-accept").performScrollTo().performClick()
            compose.onNodeWithTag("privacy-read").performScrollTo().performClick()
            compose.onNodeWithTag("service-notice-continue").assertIsEnabled()
        }
        confirmBoth()
        compose.runOnIdle { account.value="second" }
        compose.onNodeWithTag("service-notice-continue").assertIsNotEnabled()
        confirmBoth()
        compose.runOnIdle { policy.value=notice.copy(termsVersion="2") }
        compose.onNodeWithTag("service-notice-continue").assertIsNotEnabled()
        confirmBoth()
        compose.runOnIdle { busy.value=true }
        compose.onNodeWithTag("service-notice-continue").assertIsNotEnabled()
    }
}
