package com.hebi.talksullae.data

import com.hebi.talksullae.firebase.SafetyReportRepository
import com.hebi.talksullae.firebase.SafetyReportResult
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyReportTest {
    private val form = SafetyReportForm(details = "기능 확인용 신고입니다.")
    private val receipt = "a".repeat(64)

    @Test
    fun payloadContainsOnlyTrimmedFieldsAndAUuidV4() {
        val request = requireNotNull(SafetyReportRequest.prepare(
            "reporter", form.copy(details = "  테스트 신고 내용\n", roomReference = "\t  데모 방  "), null
        ))
        val payload = request.payload()
        assertEquals(
            mapOf("requestId" to request.requestId, "category" to "child_safety",
                "details" to "테스트 신고 내용", "roomReference" to "데모 방"),
            payload
        )
        val id = UUID.fromString(request.requestId)
        assertEquals(4, id.version())
        assertEquals(2, id.variant())
        val withoutRoom = requireNotNull(SafetyReportRequest.prepare("reporter", form.copy(roomReference = "  "), null))
        assertEquals(setOf("requestId", "category", "details"), withoutRoom.payload().keys)
    }

    @Test
    fun lengthsAreValidatedAfterTrimmingInUtf16() {
        assertNull(SafetyReportRequest.prepare("reporter", form.copy(details = "\uFEFF😀😀  "), null))
        assertNotNull(SafetyReportRequest.prepare("reporter", form.copy(details = "  😀😀a  "), null))
        assertNotNull(SafetyReportRequest.prepare("reporter", form.copy(details = "😀".repeat(500)), null))
        assertNull(SafetyReportRequest.prepare("reporter", form.copy(details = "😀".repeat(500) + "a"), null))
        assertNotNull(SafetyReportRequest.prepare("reporter", form.copy(roomReference = "😀".repeat(50)), null))
        assertNull(SafetyReportRequest.prepare("reporter", form.copy(roomReference = "😀".repeat(51)), null))
    }

    @Test
    fun retryReusesOnlyTheSameNormalizedSubmissionAndAccount() {
        val first = requireNotNull(SafetyReportRequest.prepare("reporter", form, null))
        val retry = requireNotNull(SafetyReportRequest.prepare("reporter", form.copy(details = " ${form.details} "), first))
        assertEquals(first.requestId, retry.requestId)
        for (changedForm in listOf(
            form.copy(category = SafetyReportCategory.Abuse),
            form.copy(details = "내용을 수정한 신고입니다."),
            form.copy(roomReference = "다른 방")
        )) {
            assertNotEquals(first.requestId, SafetyReportRequest.prepare("reporter", changedForm, first)?.requestId)
        }
        assertNotEquals(first.requestId, SafetyReportRequest.prepare("different", form, first)?.requestId)
    }

    @Test
    fun onlyAValidServerReceiptMeansSuccess() = runBlocking {
        val request = requireNotNull(SafetyReportRequest.prepare("reporter", form, null))
        val delivered = SafetyReportRepository({ "reporter" }) { payload ->
            assertEquals(request.payload(), payload)
            mapOf("reportId" to receipt)
        }
        assertEquals(SafetyReportResult.Received(receipt), delivered.submit(request))
        for (response in listOf(null, mapOf("reportId" to ""), mapOf("reportId" to "확인"), mapOf("ok" to true))) {
            val malformed = SafetyReportRepository({ "reporter" }) { response }
            assertEquals(SafetyReportResult.Failure.Backend, malformed.submit(request))
        }
    }

    @Test
    fun accountChangesNeverProduceAReceipt() = runBlocking {
        val request = requireNotNull(SafetyReportRequest.prepare("reporter", form, null))
        val wrongAccount = SafetyReportRepository({ "different" }) { error("The callable must not run") }
        assertEquals(SafetyReportResult.Failure.SignIn, wrongAccount.submit(request))

        var activeUid: String? = "reporter"
        val changedDuringCall = SafetyReportRepository({ activeUid }) {
            activeUid = null
            mapOf("reportId" to receipt)
        }
        assertTrue(changedDuringCall.submit(request) is SafetyReportResult.Failure)
    }
}
