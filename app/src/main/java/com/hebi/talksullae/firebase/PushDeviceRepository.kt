package com.hebi.talksullae.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal object PushDevicePrefs {
    private const val STORE = "talksullae_local_push"
    private const val INSTALLATION_ID = "installation-id"

    fun isEnabled(context: Context, uid: String): Boolean =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getBoolean("enabled-$uid", false)

    fun setEnabled(context: Context, uid: String, enabled: Boolean) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled-$uid", enabled).apply()
    }

    @Synchronized fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val existing = prefs.getString(INSTALLATION_ID, null)
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(INSTALLATION_ID, id).commit()
        return id
    }

    @Synchronized fun revision(context: Context, uid: String): Long =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getLong("revision-$uid", 0L)

    @Synchronized fun nextRevision(context: Context, uid: String): Long {
        val next = revision(context, uid) + 1L
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putLong("revision-$uid", next).commit()
        return next
    }
}

internal class PushDeviceRepository(private val context: Context) {
    private val functions get() = FirebaseFunctions.getInstance("asia-northeast3")
    private val messaging get() = FirebaseMessaging.getInstance()

    suspend fun setEnabled(enabled: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Google 로그인 상태를 확인해 주세요.")
        val deviceId = PushDevicePrefs.deviceId(context)
        val revision = PushDevicePrefs.nextRevision(context, uid)
        val token = messaging.token.await()
        val data = mapOf(
            "token" to token, "deviceId" to deviceId,
            "revision" to revision, "enabled" to enabled
        )
        val response = functions.getHttpsCallable("registerPushToken")
            .call(data).await().data as? Map<*, *>
            ?: error("알림 설정 결과를 확인할 수 없습니다.")
        check(response["enabled"] == enabled && response["applied"] == true) {
            "알림 설정이 서버에 저장되지 않았습니다."
        }
        if (FirebaseAuth.getInstance().currentUser?.uid == uid &&
            PushDevicePrefs.revision(context, uid) == revision) {
            PushDevicePrefs.setEnabled(context, uid, enabled)
        }
    }

    suspend fun registerRotatedToken(token: String, uid: String) {
        if (!PushDevicePrefs.isEnabled(context, uid)) return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val revision = PushDevicePrefs.revision(context, uid)
        if (revision == 0L) return
        val response = functions.getHttpsCallable("registerPushToken").call(
            mapOf(
                "token" to token, "deviceId" to PushDevicePrefs.deviceId(context),
                "revision" to revision, "enabled" to true
            )
        ).await().data as? Map<*, *> ?: return
        if (response["applied"] != true) return
        // Token refresh does not change the user's local opt-in preference.
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
                withTimeoutOrNull(1000L) { messaging.deleteToken().await() }
            }
        } catch (_: Exception) {
            withTimeoutOrNull(1000L) { messaging.deleteToken().await() }
        } finally {
            PushDevicePrefs.setEnabled(context, uid, false)
        }
    }
}
