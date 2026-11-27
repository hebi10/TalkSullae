package com.hebi.talksullae.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.firebase.ServiceNotice

@Composable
internal fun ServiceNoticeScreen(
    notice: ServiceNotice, busy: Boolean, error: String?,
    onAccept: () -> Unit, onDecline: () -> Unit, onReload: () -> Unit,
    accountKey: String
) {
    var termsAccepted by remember(accountKey,notice.termsVersion, notice.privacyVersion) { mutableStateOf(false) }
    var privacyRead by remember(accountKey,notice.termsVersion, notice.privacyVersion) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("서비스 이용 전 확인", style = MaterialTheme.typography.headlineSmall)
            Text("아래 문서를 확인해 주세요. 알림 수신 여부는 설정에서 별도로 선택할 수 있습니다.")
            ContentCard {
                Text("이용약관 · " + notice.termsVersion, style = MaterialTheme.typography.titleMedium)
                Text(notice.termsText, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = termsAccepted, enabled = !busy, role = Role.Checkbox,
                        onValueChange = { termsAccepted = it }
                    ).testTag("terms-accept"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = termsAccepted, onCheckedChange = null, enabled = !busy)
                    Text("이용약관에 동의합니다.", modifier = Modifier.weight(1f))
                }
            }
            ContentCard {
                Text("개인정보처리 안내 · " + notice.privacyVersion, style = MaterialTheme.typography.titleMedium)
                Text(notice.privacyText, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = privacyRead, enabled = !busy, role = Role.Checkbox,
                        onValueChange = { privacyRead = it }
                    ).testTag("privacy-read"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = privacyRead, onCheckedChange = null, enabled = !busy)
                    Text("개인정보처리 안내를 확인했습니다.", modifier = Modifier.weight(1f))
                }
            }
            Text(
                "확인한 문서 ${listOf(termsAccepted, privacyRead).count { it }}/2",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("notice-confirmation-count")
            )
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            if (error != null) OutlinedButton(onClick=onReload, enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text("정책 다시 불러오기")
            }
            PrimaryAction(if(busy)"확인 중…" else "확인하고 계속",
                onClick=onAccept,enabled=termsAccepted&&privacyRead&&!busy,
                modifier=Modifier.fillMaxWidth().testTag("service-notice-continue"))
            OutlinedButton(onClick=onDecline,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("동의하지 않고 로그아웃")
            }
        }
    }
}
