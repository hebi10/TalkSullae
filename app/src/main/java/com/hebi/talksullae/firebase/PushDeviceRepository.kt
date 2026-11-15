package com.hebi.talksullae.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal object PushDevicePrefs {
    private const val STORE = "talksullae_local_push"
    fun isEnabled(context: Context, uid: String): Boolean =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getBoolean("enabled-$uid", false)

    fun setEnabled(context: Context, uid: String, enabled: Boolean) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled-$uid", enabled).apply()
    }
}

internal class PushDeviceRepository(private val context: Context) {
    private val functions get() = FirebaseFunctions.getInstance("asia-northeast3")
    private val messaging get() = FirebaseMessaging.getInstance()

    suspend fun setEnabled(enabled: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Google 로그인 상태를 확인해 주세요.")
        val token = messaging.token.await()
        val response = functions.getHttpsCallable("registerPushToken")
            .call(mapOf("token" to token, "enabled" to enabled)).await().data as? Map<*, *>
            ?: error("알림 설정 결과를 확인할 수 없습니다.")
        check(response["enabled"] == enabled) { "알림 설정이 서버에 저장되지 않았습니다." }
        PushDevicePrefs.setEnabled(context, uid, enabled)
    }

    suspend fun registerRotatedToken(token: String, uid: String) {
        if (!PushDevicePrefs.isEnabled(context, uid)) return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        functions.getHttpsCallable("registerPushToken")
            .call(mapOf("token" to token, "enabled" to true)).await()
    }

    suspend fun disableOnSignOut() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!PushDevicePrefs.isEnabled(context, uid)) return
        try {
            val acknowledged = withTimeoutOrNull(2000L) {
                setEnabled(false)
                true
            } == true
            if (!acknowledged) {
                // A deleted installation token cannot receive the old queued push.
                withTimeoutOrNull(1000L) { messaging.deleteToken().await() }
            }
        } catch (_: Exception) {
            withTimeoutOrNull(1000L) { messaging.deleteToken().await() }
        } finally {
            PushDevicePrefs.setEnabled(context, uid, false)
        }
    }
}
