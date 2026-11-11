package com.hebi.talksullae.ui.safety

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hebi.talksullae.data.SafetyReportForm
import com.hebi.talksullae.data.SafetyReportRequest
import com.hebi.talksullae.firebase.SafetyReportRepository
import com.hebi.talksullae.firebase.SafetyReportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// The report stays in memory across rotation, without SavedStateHandle or local storage.
internal class SafetyReportViewModel(
    private val repository: SafetyReportRepository = SafetyReportRepository()
) : ViewModel() {
    var form by mutableStateOf(SafetyReportForm())
        private set
    var result by mutableStateOf<SafetyReportResult?>(null)
        private set
    var sending by mutableStateOf(false)
        private set
    var signingIn by mutableStateOf(false)
        private set
    var signInFailed by mutableStateOf(false)
        private set

    private var accountUid: String? = null
    private var lastRequest: SafetyReportRequest? = null

    fun accountChanged(uid: String?) {
        if (accountUid != null && accountUid != uid) clearForm()
        accountUid = uid
    }

    fun updateForm(value: SafetyReportForm) {
        if (sending || signingIn || result is SafetyReportResult.Received) return
        form = value
        if (result == SafetyReportResult.Failure.InvalidInput) result = null
    }

    fun signIn(action: suspend () -> Unit) {
        if (signingIn || sending) return
        signingIn = true
        signInFailed = false
        viewModelScope.launch {
            try {
                action()
                if (result == SafetyReportResult.Failure.SignIn) result = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                signInFailed = true
            } finally {
                signingIn = false
            }
        }
    }

    fun submit(uid: String) {
        if (sending || signingIn || result is SafetyReportResult.Received) return
        val request = SafetyReportRequest.prepare(uid, form, lastRequest) ?: return
        lastRequest = request
        sending = true
        result = null
        viewModelScope.launch {
            try {
                val response = repository.submit(request)
                if (response is SafetyReportResult.Received) clearForm()
                result = response
            } finally {
                sending = false
            }
        }
    }

    fun close(): Boolean {
        if (sending || signingIn) return false
        clearForm()
        accountUid = null
        return true
    }

    private fun clearForm() {
        form = SafetyReportForm()
        lastRequest = null
        result = null
        signInFailed = false
    }
}
