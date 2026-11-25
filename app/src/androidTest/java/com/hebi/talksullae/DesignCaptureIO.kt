package com.hebi.talksullae

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assume.assumeTrue

/** Only compiled into androidTest. Never uses production data or credentials. */
internal object DesignCaptureIO {
    private const val GUARD_VERSION = 1
    private const val MAX_SYSTEM_RECOVERIES = 3
    private val systemPackages = setOf("android", "com.android.systemui",
        "com.android.launcher3", "com.google.android.apps.nexuslauncher")
    private val systemUiTitles = setOf("System UI isn't responding", "시스템 UI가 응답하지 않습니다",
        "시스템 UI 응답 없음")
    private val launcherTitles = setOf("Quickstep isn't responding", "Pixel Launcher isn't responding",
        "Launcher isn't responding")
    private val waitLabels = setOf("Wait", "대기")
    private val closeLabels = setOf("Close app", "앱 닫기")

    fun requireMode() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureDesign") == "true")
        check(BuildConfig.DEBUG && !BuildConfig.FIREBASE_CONFIGURED) {
            "Design captures require an isolated debug build without Firebase configuration."
        }
    }

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        val result = mutableListOf<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            check(result.size < 5000) { "Accessibility hierarchy exceeded capture safety limit" }
            val node = pending.removeFirst()
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { pending.add(it) }
        }
        return result
    }

    private fun clickExactAction(button: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = button
        repeat(4) {
            val current = candidate ?: return false
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            candidate = current.parent
        }
        return false
    }

    /**
     * Inspect every accessible window, not just Compose semantics. Only a recognized
     * emulator System UI/launcher ANR may be acknowledged. App ANRs and unknown
     * foreground windows are never dismissed or accepted as screenshot evidence.
     * Returns true if a known system overlay was acknowledged and the frame must
     * be discarded/retried. A clean app foreground returns false.
     */
    private fun inspectForeground(automation: UiAutomation, appPackage: String, allowRecovery: Boolean): Boolean {
        val windows = automation.windows
        check(windows.isNotEmpty()) { "Cannot inspect interactive Android windows" }
        var appPresent = false
        var appForeground = false
        var keyboardForeground = false
        var foreignForeground = false
        val recoverable = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        for (window in windows) {
            val root = window.root
            if (root == null) {
                check(!(window.isActive || window.isFocused) ||
                    window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    "Active Android window has no accessible root"
                }
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                    (window.isActive || window.isFocused)) keyboardForeground = true
                continue
            }
            val pkg = root.packageName?.toString().orEmpty()
            val focused = window.isActive || window.isFocused
            if (pkg == appPackage) {
                appPresent = true
                if (focused) appForeground = true
                continue
            }
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                if (focused) keyboardForeground = true
                continue
            }
            val allNodes = nodes(root)
            val labels = allNodes.mapNotNull { it.text?.toString()?.trim() }
            val systemUi = labels.any { it in systemUiTitles }
            val launcher = labels.any { it in launcherTitles }
            val errorDialog = allNodes.any {
                it.viewIdResourceName?.startsWith("android:id/aerr_") == true
            } || labels.any {
                it.contains("isn't responding", ignoreCase = true) ||
                    it.contains("not responding", ignoreCase = true) ||
                    it.contains("keeps stopping", ignoreCase = true) ||
                    it.contains("has stopped", ignoreCase = true) ||
                    it.contains("응답하지 않") || it.contains("응답 없음") || it.contains("계속 중단")
            }
            if (errorDialog || systemUi || launcher) {
                check(pkg in systemPackages && (systemUi xor launcher)) {
                    "Non-recoverable application or unknown system error dialog"
                }
                val actionLabels = if (systemUi) waitLabels else closeLabels
                val actionId = if (systemUi) "android:id/aerr_wait" else "android:id/aerr_close"
                val button = allNodes.firstOrNull {
                    it.text?.toString()?.trim() in actionLabels &&
                        (it.viewIdResourceName == actionId || it.isClickable)
                } ?: allNodes.firstOrNull {
                    it.text?.toString()?.trim() in actionLabels
                }
                check(button != null) { "Recognized emulator dialog has no safe action" }
                recoverable.add(button to if (systemUi) "System UI / Wait" else "launcher / Close app")
            } else if (focused) {
                foreignForeground = true
            }
        }
        if (recoverable.isNotEmpty()) {
            check(allowRecovery && recoverable.size == 1) { "Repeated or ambiguous emulator ANR" }
            val (button, action) = recoverable.single()
            check(clickExactAction(button)) { "Could not acknowledge recognized emulator ANR" }
            android.util.Log.w("DesignCaptureGuard", "Acknowledged emulator-only dialog: $action")
            Thread.sleep(3000)
            return true
        }
        check(appPresent && (appForeground || keyboardForeground) && !foreignForeground) {
            "TalkSullae is not the unobscured active application"
        }
        return false
    }

    fun save(name: String) {
        require(name.matches(Regex("D[0-9]{2}_[a-z0-9_]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val appPackage = instrumentation.targetContext.packageName
        val originalFlags = automation.serviceInfo.flags
        val serviceInfo = automation.serviceInfo
        serviceInfo.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = serviceInfo
        try {
            var recoveries = 0
            repeat(MAX_SYSTEM_RECOVERIES + 1) {
                instrumentation.waitForIdleSync()
                Thread.sleep(350)
                if (inspectForeground(automation, appPackage, recoveries < MAX_SYSTEM_RECOVERIES)) {
                    recoveries++
                    return@repeat
                }
                val raw = requireNotNull(automation.takeScreenshot())
                val frame = if (raw.config == Bitmap.Config.HARDWARE) {
                    val copy = requireNotNull(raw.copy(Bitmap.Config.ARGB_8888, false))
                    raw.recycle()
                    copy
                } else raw
                try {
                    // Do not save a frame if a system dialog appeared during capture.
                    if (inspectForeground(automation, appPackage, recoveries < MAX_SYSTEM_RECOVERIES)) {
                        recoveries++
                        return@repeat
                    }
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
                    val guards = File(instrumentation.targetContext.filesDir,"design-capture-guards")
                    check(folder.isDirectory || folder.mkdirs())
                    check(guards.isDirectory || guards.mkdirs())
                    val output = File(folder,"$name.png")
                    val marker = File(guards,"$name.json")
                    check(!output.exists() && !marker.exists()) { "Duplicate capture: $name" }
                    output.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG,100,it)) }
                    marker.writeText(JSONObject().put("guardVersion", GUARD_VERSION)
                        .put("screen",name).put("package",appPackage)
                        .put("preForegroundClear",true).put("postForegroundClear",true)
                        .put("systemDialogRecoveries",recoveries).toString(), Charsets.UTF_8)
                    return
                } finally { frame.recycle() }
            }
            error("Repeated emulator ANR prevented a clean capture: $name")
        } finally {
            val restore = automation.serviceInfo
            restore.flags = originalFlags
            automation.serviceInfo = restore
        }
    }
}
