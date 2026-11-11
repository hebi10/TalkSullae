package com.hebi.talksullae

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.hebi.talksullae.design.AppTopBar
import com.google.firebase.firestore.ListenerRegistration
import com.hebi.talksullae.design.TalkSullaeTheme
import com.hebi.talksullae.firebase.CloudMember
import com.hebi.talksullae.firebase.CloudRoom
import com.hebi.talksullae.firebase.CloudRoomIndex
import com.hebi.talksullae.firebase.Invitation
import com.hebi.talksullae.firebase.JoinResult
import com.hebi.talksullae.firebase.OnlineRoomsRepository
import com.hebi.talksullae.ui.entry.OnboardingScreen
import com.hebi.talksullae.ui.rooms.OnlineRoomsHost
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StoreScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun requireCaptureMode() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("capturePlayStore") == "true")
    }

    @Test
    fun login() {
        compose.setContent {
            StoreScreen("닉네임 · 아바타") {
                OnboardingScreen(onContinueDemo = {}, onGoogleSignIn = {})
            }
        }
        compose.onNodeWithTag("onboarding-google-signin").performScrollTo().assertIsDisplayed().assertIsEnabled()
        save("01-login")
    }

    @Test
    fun onlineRooms() {
        val repository = SampleRoomsRepository()
        compose.setContent {
            StoreScreen("온라인 방", "초대 코드 · 멤버 관리") {
                OnlineRoomsHost("sample-owner", "샘플 계정", {}, {}, repository)
            }
        }
        compose.onNodeWithText("내 온라인 방").assertIsDisplayed()
        save("02-rooms")

        compose.onNodeWithTag("online-create-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("online-room-name").performScrollTo().assertIsDisplayed()
        save("03-create")
        compose.onNodeWithTag("online-create-toggle").performScrollTo().performClick()

        compose.onNodeWithText("열기").performScrollTo().performClick()
        compose.onNodeWithText("승인").performScrollTo().assertIsDisplayed()
        save("04-approval")

        compose.onNodeWithText("새 초대 코드 발급 (이전 코드 폐기)").performScrollTo().performClick()
        compose.onNodeWithText("코드 복사").performScrollTo().assertIsDisplayed()
        save("05-invite")

        compose.onNodeWithText("방장 위임").performScrollTo().assertIsDisplayed()
        save("06-members")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = File(instrumentation.targetContext.filesDir, "play-store-captures")
        output.mkdirs()
        try {
            check(screenshot.width == 1080 && screenshot.height == 1920)
            File(output, "$name.jpg").outputStream().use { stream ->
                check(screenshot.compress(Bitmap.CompressFormat.JPEG, 98, stream))
            }
        } finally {
            screenshot.recycle()
        }
    }
}

@Composable
private fun StoreScreen(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    TalkSullaeTheme(darkTheme = true) {
        Scaffold(topBar = { AppTopBar(title, subtitle, onBack = {}) }) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
        }
    }
}

private class SampleRoomsRepository : OnlineRoomsRepository {
    private val sample = CloudRoom("sample-room", "금요일 모임", "친구들과 함께하는 주말 모임", "sample-owner", 2, 1, true)

    override fun watchMyRooms(uid: String, onResult: (List<CloudRoomIndex>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        onResult(listOf(CloudRoomIndex(sample.id, sample.name, "active"), CloudRoomIndex("sample-pending", "동네 친구들", "pending")))
        return ListenerRegistration {}
    }

    override fun watchRoom(roomId: String, onResult: (CloudRoom?) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        onResult(sample)
        return ListenerRegistration {}
    }

    override fun watchMembers(roomId: String, onResult: (List<CloudMember>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        onResult(listOf(
            CloudMember("sample-owner", "샘플 계정", "active"),
            CloudMember("sample-pending", "하늘", "pending"),
            CloudMember("sample-member", "지우", "active")
        ))
        return ListenerRegistration {}
    }

    override suspend fun createInvitation(roomId: String) = Invitation("sample-room.예시코드", 0)
    override suspend fun createRoom(name: String, description: String, approval: Boolean): String = error("Screenshot fixture")
    override suspend fun requestJoin(code: String): JoinResult = error("Screenshot fixture")
    override suspend fun changeMember(roomId: String, targetUid: String, action: String) = error("Screenshot fixture")
    override suspend fun leaveRoom(roomId: String) = error("Screenshot fixture")
}
