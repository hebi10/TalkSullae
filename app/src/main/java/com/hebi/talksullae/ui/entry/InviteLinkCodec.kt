package com.hebi.talksullae.ui.entry

import com.hebi.talksullae.BuildConfig
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * HTTPS invite codes travel in the fragment (never in the hosting HTTP request).
 * Sharing HTTPS remains disabled until the approved site and Play signing
 * association are verified. Existing scheme links remain supported.
 */
object InviteLinkCodec {
    private val codePattern = Regex("^[A-Za-z0-9]{20}\\.[A-Za-z0-9_-]{43}$")

    fun validCode(code: String): Boolean = codePattern.matches(code)

    private const val WEB_HOST = "talksullae.web.app"
    private const val WEB_PATH = "/talksullae/invite"

    fun encode(code: String, webEnabled: Boolean = BuildConfig.INVITE_WEB_ENABLED): String? {
        if (!validCode(code)) return null
        return if (webEnabled) "https://$WEB_HOST$WEB_PATH#code=$code" else "talksullae://invite?code=$code"
    }

    fun read(url: String?): String? {
        if (url.isNullOrBlank() || url.length > 512) return null
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        if (parsed.port != -1 || parsed.rawUserInfo != null) return null
        val payload = when {
            parsed.scheme == "talksullae" && parsed.host == "invite" &&
                parsed.rawFragment == null && (parsed.rawPath == "" || parsed.rawPath == "/") -> parsed.rawQuery
            parsed.scheme == "https" && parsed.host == WEB_HOST &&
                parsed.rawPath == WEB_PATH && parsed.rawQuery == null -> parsed.rawFragment
            else -> null
        } ?: return null
        if (!payload.startsWith("code=") || payload.contains("&")) return null
        val code = runCatching {
            URLDecoder.decode(payload.removePrefix("code="), StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        return code.takeIf(::validCode)
    }

    fun normalizeInput(value: String): String? {
        val input = value.trim()
        return if (validCode(input)) input else read(input)
    }
}
