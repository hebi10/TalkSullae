package com.hebi.talksullae.data

import java.util.UUID

internal const val MIN_REPORT_DETAILS = 5
internal const val MAX_REPORT_DETAILS = 1000
internal const val MAX_REPORT_ROOM_REFERENCE = 100

internal enum class SafetyReportCategory(val key: String) {
    ChildSafety("child_safety"),
    Abuse("abuse"),
    Other("other")
}

internal data class SafetyReportForm(
    val category: SafetyReportCategory = SafetyReportCategory.ChildSafety,
    val details: String = "",
    val roomReference: String = ""
) {
    val detailsLength: Int get() = details.trimForReport().length
    val roomReferenceLength: Int get() = roomReference.trimForReport().length
    val valid: Boolean
        get() = detailsLength in MIN_REPORT_DETAILS..MAX_REPORT_DETAILS &&
            roomReferenceLength <= MAX_REPORT_ROOM_REFERENCE

    fun normalized(): SafetyReportForm = copy(
        details = details.trimForReport(),
        roomReference = roomReference.trimForReport()
    )
}

// Match the server's JavaScript trim before measuring UTF-16 code units.
private fun String.trimForReport(): String = trim {
    it in '\t'..'\r' || Character.isSpaceChar(it) || it == '\uFEFF'
}

internal class SafetyReportRequest private constructor(
    val requestId: String,
    val uid: String,
    private val form: SafetyReportForm
) {
    fun payload(): Map<String, String> = buildMap {
        put("requestId", requestId)
        put("category", form.category.key)
        put("details", form.details)
        if (form.roomReference.isNotEmpty()) put("roomReference", form.roomReference)
    }

    companion object {
        fun prepare(uid: String, form: SafetyReportForm, previous: SafetyReportRequest?): SafetyReportRequest? {
            if (uid.isBlank() || !form.valid) return null
            val normalized = form.normalized()
            return previous?.takeIf { it.uid == uid && it.form == normalized }
                ?: SafetyReportRequest(UUID.randomUUID().toString(), uid, normalized)
        }
    }
}
