package com.hebi.talksullae.firebase

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class ServiceNotice(
    val termsVersion: String,
    val privacyVersion: String,
    val termsText: String,
    val privacyText: String
)

internal object ServiceNoticeDecoder {
    fun decode(data: Map<*, *>): ServiceNotice? {
        val enabled = data["enabled"] as? Boolean ?: error("정책 활성 상태를 확인할 수 없습니다.")
        val required = data["required"] as? Boolean ?: error("정책 확인 상태를 확인할 수 없습니다.")
        if (!enabled && required) error("정책 확인 상태가 올바르지 않습니다.")
        if (!required) return null
        fun text(key: String, max: Int): String =
            (data[key] as? String)?.takeIf { it.isNotBlank() && it.length <= max }
                ?: error("정책 문서 정보를 확인할 수 없습니다.")
        return ServiceNotice(text("termsVersion", 80), text("privacyVersion", 80),
            text("termsText", 30000), text("privacyText", 30000))
    }
}

internal class ServiceNoticeRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) {
    suspend fun load(): ServiceNotice? {
        val data = functions.getHttpsCallable("getServiceNoticeStatus").call(emptyMap<String, Any>())
            .await().data as? Map<*, *> ?: error("정책 확인 상태를 불러올 수 없습니다.")
        return ServiceNoticeDecoder.decode(data)
    }

    suspend fun accept(uid: String, notice: ServiceNotice) {
        val data = functions.getHttpsCallable("acceptServiceNotices").call(mapOf(
            "expectedUid" to uid, "termsVersion" to notice.termsVersion,
            "privacyVersion" to notice.privacyVersion, "termsAccepted" to true,
            "privacyNoticeRead" to true
        )).await().data as? Map<*, *>
        check(data?.get("accepted") == true) { "서버의 정책 확인 기록을 검증하지 못했습니다." }
    }
}
