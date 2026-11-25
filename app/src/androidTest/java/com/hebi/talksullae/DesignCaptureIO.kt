package com.hebi.talksullae

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue

/** Only compiled into androidTest. Never uses production data or credentials. */
internal object DesignCaptureIO {
    fun requireMode() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureDesign") == "true")
        check(BuildConfig.DEBUG && !BuildConfig.FIREBASE_CONFIGURED) {
            "Design captures require an isolated debug build without Firebase configuration."
        }
    }

    fun save(name: String) {
        require(name.matches(Regex("D[0-9]{2}_[a-z0-9_]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(350)
        val raw = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val frame = if (raw.config == Bitmap.Config.HARDWARE) {
            val copy = requireNotNull(raw.copy(Bitmap.Config.ARGB_8888, false))
            raw.recycle()
            copy
        } else raw
        try {
            // Reject an empty / single-color frame, not a replacement for a semantic anchor.
            var dark = false
            var light = false
            for (y in 100 until frame.height - 100 step 12) {
                for (x in 24 until frame.width - 24 step 12) {
                    val color = frame.getPixel(x,y)
                    dark = dark || Color.red(color) < 65
                    light = light || Color.red(color) > 175
                    if (dark && light) break
                }
                if (dark && light) break
            }
            check(dark && light) { "No rendered contrast for $name" }
            val folder = File(instrumentation.targetContext.filesDir,"design-captures")
            check(folder.isDirectory || folder.mkdirs())
            val output = File(folder,"$name.png")
            check(!output.exists()) { "Duplicate capture: $name" }
            output.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG,100,it)) }
        } finally { frame.recycle() }
    }
}
