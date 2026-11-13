package com.hebi.talksullae.ui.safety

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.firebase.FirebaseBridge
import com.hebi.talksullae.firebase.GoogleLogin
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
internal fun AccountDeletionScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configured = remember(context) { FirebaseBridge.isReady(context) }
    val currentUser = if(configured) FirebaseAuth.getInstance().currentUser else null
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var requested by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .testTag("account-deletion-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("계정 및 데이터 삭제", style = MaterialTheme.typography.titleLarge)
        Text("삭제 요청 후 서버가 방 멤버 정보와 본인이 작성한 채팅을 정리하고 인증 계정을 삭제합니다. " +
            "공동 경기 결과와 관련 법령에 따른 신고 기록은 별도의 보관 기준이 적용될 수 있습니다.")
        Text("방장인 방은 먼저 다른 멤버에게 방장을 위임하거나 방을 종료해야 합니다. " +
            "진행 중인 게임을 마친 뒤 요청해 주세요.")
        Text("삭제 요청은 취소할 수 없으며, 완료까지 시간이 걸릴 수 있습니다.")
        if (busy) CircularProgressIndicator()
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!configured) Text("온라인 로그인 설정이 없는 시연 빌드에서는 계정 삭제 요청을 보낼 수 없습니다.")
        if (requested) {
            Text("삭제 요청이 접수됐습니다. 서버 처리 결과를 확인하는 동안 같은 계정으로 " +
                "데이터를 추가하지 마세요. 처리가 지연되면 개인정보처리방침의 지원 연락처로 문의해 주세요.")
        } else {
            OutlinedButton(
                onClick = {
                    if (busy || activity == null || !configured) return@OutlinedButton
                    busy = true
                    failure = null
                    scope.launch {
                        try {
                            val existing = FirebaseAuth.getInstance().currentUser?.uid
                                ?: error("Google 로그인 후 요청해 주세요.")
                            val refreshed = GoogleLogin(activity).signIn()
                            check(refreshed.uid == existing) {
                                "같은 Google 계정으로 다시 로그인해 주세요."
                            }
                            confirmVisible = true
                        } catch (error: Exception) {
                            failure = error.localizedMessage ?: "다시 로그인할 수 없습니다."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && activity != null && configured,
                modifier = Modifier.fillMaxWidth().testTag("account-deletion-reauth")
            ) { Text("같은 Google 계정으로 본인 확인") }
            PrimaryAction(
                "계정 삭제 요청",
                onClick = { confirmVisible = true },
                enabled = !busy && currentUser != null,
                modifier = Modifier.fillMaxWidth().testTag("account-deletion-open-confirm")
            )
        }
        TextButton(onClick = onBack, enabled = !busy) { Text("설정으로 돌아가기") }
    }
    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmVisible = false },
            title = { Text("계정을 삭제하시겠습니까?") },
            text = { Text("본인 확인 후 삭제 요청을 제출하면 복원할 수 없습니다. " +
                "최근 Google 로그인이 유효해야 합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmVisible = false
                        busy = true
                        failure = null
                        scope.launch {
                            try {
                                val response = FirebaseFunctions.getInstance("asia-northeast3")
                                    .getHttpsCallable("requestAccountDeletion")
                                    .call(mapOf("confirm" to "DELETE")).await()
                                    .data as? Map<*, *>
                                check(response?.get("status") == "queued") {
                                    "계정 삭제 요청의 접수를 확인할 수 없습니다."
                                }
                                requested = true
                                FirebaseAuth.getInstance().signOut()
                                onSignedOut()
                            } catch (error: Exception) {
                                failure = error.localizedMessage ?: "삭제 요청을 확인하지 못했습니다."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("account-deletion-confirm")
                ) { Text("삭제 요청 확정") }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) { Text("취소") }
            }
        )
    }
}
