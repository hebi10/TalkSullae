package com.hebi.talksullae.firebase

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hebi.talksullae.MainActivity
import com.hebi.talksullae.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TalkSullaeMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channelId = "talksullae-room"

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!PushDevicePrefs.isEnabled(this, uid)) return
        scope.launch {
            try {
                PushDeviceRepository(applicationContext).registerRotatedToken(token, uid)
            } catch (_: Exception) {
                // Retry the registration when the user next opens notification settings.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = message.data
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (payload["recipientUid"] != uid || !PushDevicePrefs.isEnabled(this, uid)) return
        if (payload["kind"] !in listOf("message", "game", "approved")) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            channelId, "친구방 알림", NotificationManager.IMPORTANCE_DEFAULT
        ))
        val body = when (payload["kind"]) {
            "message" -> "참여 중인 방에 새 메시지가 있습니다."
            "game" -> "친구방에서 게임이 시작되었습니다."
            else -> "친구방 가입 요청이 승인되었습니다."
        }
        val target = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 0, target,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("톡술래")
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify((message.messageId ?: body).hashCode(), notification)
    }
}
