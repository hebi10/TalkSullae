package com.hebi.talksullae.ui.entry

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Device-installed deep link. An HTTPS Android App Link requires a verified
 * owned domain and assetlinks.json; this custom scheme does not claim that.
 */
object InviteLinkCodec {
    private val codePattern = Regex("^[A-Za-z0-9]{20}\\.[A-Za-z0-9_-]{43}$")

    fun validCode(code: String): Boolean = codePattern.matches(code)

    fun encode(code: String): String? =
        if (validCode(code)) "talksullae://invite?code=$code" else null

    fun read(url: String?): String? {
        if (url.isNullOrBlank() || url.length > 512) return null
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        if (parsed.scheme != "talksullae" || parsed.host != "invite" ||
            parsed.port != -1 || parsed.rawUserInfo != null ||
            parsed.rawFragment != null || (parsed.rawPath != "" && parsed.rawPath != "/")) {
            return null
        }
        val query = parsed.rawQuery ?: return null
        if (!query.startsWith("code=") || query.contains("&")) return null
        val code = runCatching {
            URLDecoder.decode(query.removePrefix("code="), StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        return code.takeIf(::validCode)
    }

    fun normalizeInput(value: String): String? {
        val input = value.trim()
        return if (validCode(input)) input else read(input)
    }
}
