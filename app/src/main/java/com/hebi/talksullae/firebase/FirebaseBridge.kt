package com.hebi.talksullae.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.hebi.talksullae.BuildConfig
import kotlinx.coroutines.tasks.await

object FirebaseBridge {
    fun isReady(context: Context): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.initializeApp(context) != null
}

class GoogleLogin(private val activity: Activity) {
    suspend fun signIn(): FirebaseUser {
        check(FirebaseBridge.isReady(activity)) { "Firebase 설정을 먼저 연결해 주세요." }
        val clientIdResource = activity.resources.getIdentifier(
            "default_web_client_id", "string", activity.packageName
        )
        check(clientIdResource != 0) { "Firebase Google 로그인 공급자가 설정되지 않았습니다." }
        val clientId = activity.getString(clientIdResource)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val credential = result.credential
        check(credential is CustomCredential) {
            "Google 계정 인증 정보가 올바르지 않습니다."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        return requireNotNull(FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await().user) {
            "로그인한 계정을 확인할 수 없습니다."
        }
    }

    suspend fun signOut() {
        if (FirebaseBridge.isReady(activity)) FirebaseAuth.getInstance().signOut()
        runCatching {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
