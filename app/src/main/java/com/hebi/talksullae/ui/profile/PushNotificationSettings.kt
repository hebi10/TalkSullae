package com.hebi.talksullae.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.firebase.PushDevicePrefs
import com.hebi.talksullae.firebase.PushDeviceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PushNotificationSettings(uid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { PushDeviceRepository(context.applicationContext) }
    var enabled by remember(uid) { mutableStateOf(PushDevicePrefs.isEnabled(context, uid)) }
    var busy by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }

    val change: (Boolean) -> Unit = { next ->
        if (!busy) {
            scope.launch {
                busy = true
                error = null
                try {
                    check(FirebaseAuth.getInstance().currentUser?.uid == uid) {
                        "계정이 변경되었습니다."
                    }
                    repository.setEnabled(next)
                    if (FirebaseAuth.getInstance().currentUser?.uid == uid) enabled = next
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = "알림 설정을 서버에 저장하지 못했습니다. 다시 시도해 주세요."
                } finally {
                    busy = false
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) change(true)
        else error = "기기의 알림 권한을 허용해야 알림을 받을 수 있습니다."
    }
    val onToggle: (Boolean) -> Unit = { next ->
        if (next && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            change(next)
        }
    }
    LaunchedEffect(uid) {
        if (enabled) {
            try {
                repository.reconcileCurrentToken(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "알림 등록을 갱신하지 못했습니다. 다음 앱 실행 때 다시 확인합니다."
            }
        }
    }

    ContentCard(modifier = Modifier.testTag("push-notification-settings")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("친구방 알림", style = MaterialTheme.typography.titleMedium)
                Text("새 채팅·게임 시작·가입 승인 알림을 이 기기에서 받습니다.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = !busy,
                modifier = Modifier.testTag("push-notification-toggle")
            )
        }
        Text("알림에는 메시지 본문과 게임의 익명 정체·비밀 역할이 표시되지 않습니다.",
            style = MaterialTheme.typography.bodySmall)
        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
