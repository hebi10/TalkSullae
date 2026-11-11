package com.hebi.talksullae.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.hebi.talksullae.data.SafetyReportRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

internal fun FirebaseUser?.googleUid(): String? = this
    ?.takeIf { user -> user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } }
    ?.uid

internal sealed interface SafetyReportResult {
    data class Received(val reportId: String) : SafetyReportResult
    enum class Failure : SafetyReportResult { SignIn, RateLimited, InvalidInput, Unavailable, Backend }
}

internal class SafetyReportRepository(
    private val currentGoogleUid: () -> String? = { FirebaseAuth.getInstance().currentUser.googleUid() },
    private val call: suspend (Map<String, String>) -> Any? = { payload ->
        FirebaseFunctions.getInstance("asia-northeast3")
            .getHttpsCallable("submitSafetyReport")
            .withTimeout(30, TimeUnit.SECONDS).call(payload).await().data
    }
) {
    suspend fun submit(request: SafetyReportRequest): SafetyReportResult = try {
        if (currentGoogleUid() != request.uid) {
            SafetyReportResult.Failure.SignIn
        } else {
            val response = call(request.payload()) as? Map<*, *>
            val reportId = response?.get("reportId") as? String
            when {
                currentGoogleUid() != request.uid -> SafetyReportResult.Failure.SignIn
                reportId != null && ReceiptId.matches(reportId) -> SafetyReportResult.Received(reportId)
                else -> SafetyReportResult.Failure.Backend
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: FirebaseFunctionsException) {
        when (failure.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> SafetyReportResult.Failure.SignIn
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> SafetyReportResult.Failure.RateLimited
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> SafetyReportResult.Failure.InvalidInput
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.UNIMPLEMENTED,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> SafetyReportResult.Failure.Unavailable
            else -> SafetyReportResult.Failure.Backend
        }
    } catch (_: Exception) {
        SafetyReportResult.Failure.Backend
    }

    private companion object {
        val ReceiptId = Regex("[a-f0-9]{64}")
    }
}
