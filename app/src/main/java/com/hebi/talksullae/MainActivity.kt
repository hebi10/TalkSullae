package com.hebi.talksullae

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hebi.talksullae.ui.entry.InviteLinkCodec
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import com.hebi.talksullae.ui.TalkSullaeApp

class MainActivity : ComponentActivity() {
    private var pendingInviteCode by mutableStateOf<String?>(null)

    private fun readInvite(intent: Intent?) {
        pendingInviteCode = InviteLinkCodec.read(intent?.dataString)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readInvite(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readInvite(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            TalkSullaeApp(
                incomingInviteCode = pendingInviteCode,
                onInviteCodeHandled = {
                    pendingInviteCode = null
                    intent?.let { it.data = null }
                },
                onDarkThemeChanged = { isDark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            })
        }
    }
}
