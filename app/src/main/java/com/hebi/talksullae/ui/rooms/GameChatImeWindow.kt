package com.hebi.talksullae.ui.rooms

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.hostActivity()
    else -> null
}

/**
 * These two scrolling chat hosts already consume the real IME insets outside
 * their scroll content. On API 30+ let Compose own that viewport instead of
 * allowing the window's focus adjustment to pan the entire edge-to-edge page.
 * Keep the legacy resize policy and restore the previous adjustment on exit.
 */
@Composable
internal fun GameChatImeWindow() {
    val window = LocalView.current.context.hostActivity()?.window
    DisposableEffect(window) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onDispose {}
        } else {
            val mask = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
            val previous = window.attributes.softInputMode and mask
            val manual = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (previous != manual) {
                window.setSoftInputMode((window.attributes.softInputMode and mask.inv()) or manual)
            }
            onDispose {
                val current = window.attributes.softInputMode
                if (previous != manual && (current and mask) == manual) {
                    window.setSoftInputMode((current and mask.inv()) or previous)
                }
            }
        }
    }
}
