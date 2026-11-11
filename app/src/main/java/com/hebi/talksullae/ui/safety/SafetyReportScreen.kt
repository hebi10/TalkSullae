package com.hebi.talksullae.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hebi.talksullae.data.MAX_REPORT_DETAILS
import com.hebi.talksullae.data.MAX_REPORT_ROOM_REFERENCE
import com.hebi.talksullae.data.MIN_REPORT_DETAILS
import com.hebi.talksullae.data.SafetyReportCategory
import com.hebi.talksullae.design.ContentCard
import com.hebi.talksullae.design.PreviewNotice
import com.hebi.talksullae.design.PrimaryAction
import com.hebi.talksullae.firebase.SafetyReportResult

private const val ChildSafetyStandards =
    "https://gist.github.com/hebi10/ca30acf14d216d9aa89aa0b4c6d615fa/de45c2c01e1c49ff92821129ffe752e9af8b207d"

@Composable
internal fun SafetyReportScreen(
    model: SafetyReportViewModel,
    googleUid: String?,
    onSignIn: (() -> Unit)?,
    onBack: () -> Unit
) {
    val focus = LocalFocusManager.current
    val result = model.result
    val needsLogin = googleUid == null || result == SafetyReportResult.Failure.SignIn
    val editable = !model.sending && !model.signingIn

    Box(
        Modifier.fillMaxSize().imePadding().testTag("safety-report-screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (result is SafetyReportResult.Received) {
                ContentCard(Modifier.testTag("safety-report-receipt")
                    .semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text("신고가 접수되었습니다", style = MaterialTheme.typography.titleMedium)
                    Text("접수 번호", style = MaterialTheme.typography.bodySmall)
                    SelectionContainer {
                        Text(result.reportId, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
                PrimaryAction("설정으로 돌아가기", onClick = onBack, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "톡술래 이용 중 발견한 문제를 운영자에게 알릴 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("신고 사유", style = MaterialTheme.typography.titleMedium)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (category in SafetyReportCategory.entries) {
                            val selected = model.form.category == category
                            FilterChip(
                                selected = selected,
                                onClick = { model.updateForm(model.form.copy(category = category)) },
                                enabled = editable,
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier.testTag("safety-report-category-${category.key}"),
                                label = {
                                    Text(category.label(), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            )
                        }
                    }
                }

                val detailsLength = model.form.detailsLength
                val shortDetails = detailsLength in 1 until MIN_REPORT_DETAILS
                OutlinedTextField(
                    value = model.form.details,
                    onValueChange = { model.updateForm(model.form.copy(details = it)) },
                    modifier = Modifier.fillMaxWidth().testTag("safety-report-details"),
                    label = { Text("신고 내용") },
                    enabled = editable,
                    minLines = 4,
                    maxLines = 7,
                    shape = RoundedCornerShape(8.dp),
                    isError = shortDetails || detailsLength > MAX_REPORT_DETAILS,
                    supportingText = {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (shortDetails) Text("신고 내용을 ${MIN_REPORT_DETAILS}자 이상 입력해 주세요.")
                            Text("$detailsLength/$MAX_REPORT_DETAILS", modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                )
                val roomLength = model.form.roomReferenceLength
                OutlinedTextField(
                    value = model.form.roomReference,
                    onValueChange = { model.updateForm(model.form.copy(roomReference = it)) },
                    modifier = Modifier.fillMaxWidth().testTag("safety-report-room"),
                    label = { Text("방 이름 (선택)") },
                    enabled = editable,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    isError = roomLength > MAX_REPORT_ROOM_REFERENCE,
                    supportingText = if (roomLength > MAX_REPORT_ROOM_REFERENCE) ({
                        Text("방 이름은 ${MAX_REPORT_ROOM_REFERENCE}자까지 입력할 수 있습니다.")
                    }) else null
                )
                PreviewNotice("의심되는 이미지·영상이나 방 초대 코드는 첨부하거나 전달하지 마세요.")

                if (needsLogin) {
                    if (onSignIn == null) {
                        PreviewNotice("이 미리보기 빌드에서는 신고를 보낼 수 없습니다.")
                    } else {
                        Text("신고를 보내려면 Google 계정으로 로그인해 주세요.",
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(
                            onClick = { focus.clearFocus(); onSignIn() },
                            enabled = editable,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("safety-report-sign-in")
                        ) {
                            Text(if (model.signingIn) "로그인 중…" else "Google 계정으로 로그인")
                        }
                    }
                }
                if (model.signInFailed) ReportMessage("로그인할 수 없습니다. 다시 시도해 주세요.")
                if (result is SafetyReportResult.Failure && result != SafetyReportResult.Failure.SignIn) {
                    ReportMessage(result.message())
                }
                PrimaryAction(
                    label = when {
                        model.sending -> "보내는 중…"
                        result is SafetyReportResult.Failure && !needsLogin -> "다시 시도"
                        else -> "신고 보내기"
                    },
                    onClick = {
                        focus.clearFocus()
                        if (googleUid != null) model.submit(googleUid)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("safety-report-send"),
                    enabled = editable && !needsLogin && model.form.valid
                )
            }
            ChildSafetyPolicyLink(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ChildSafetyPolicyLink(modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    TextButton(
        onClick = {
            failed = try {
                uriHandler.openUri(ChildSafetyStandards)
                false
            } catch (_: IllegalArgumentException) {
                true
            }
        },
        modifier = modifier.heightIn(min = 48.dp).testTag("safety-report-policy")
    ) {
        Text("아동 안전 표준 보기", textDecoration = TextDecoration.Underline)
    }
    if (failed) ReportMessage("아동 안전 표준 링크를 열 수 없습니다.")
}

@Composable
private fun ReportMessage(message: String) {
    Text(
        message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

private fun SafetyReportCategory.label(): String = when (this) {
    SafetyReportCategory.ChildSafety -> "아동 안전"
    SafetyReportCategory.Abuse -> "괴롭힘·혐오"
    SafetyReportCategory.Other -> "기타"
}

private fun SafetyReportResult.Failure.message(): String = when (this) {
    SafetyReportResult.Failure.SignIn -> "Google 계정으로 다시 로그인해 주세요."
    SafetyReportResult.Failure.RateLimited -> "신고는 1분에 한 번 보낼 수 있습니다. 잠시 후 다시 시도해 주세요."
    SafetyReportResult.Failure.InvalidInput -> "신고 내용과 방 이름의 길이를 확인해 주세요."
    SafetyReportResult.Failure.Unavailable -> "지금 신고 접수 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."
    SafetyReportResult.Failure.Backend -> "신고 접수를 확인하지 못했습니다. 다시 시도해 주세요."
}
