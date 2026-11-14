package com.hebi.talksullae.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
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
        val result = FirebaseAuth.getInstance().signInWithCredential(requestGoogleCredential()).await()
        return requireNotNull(result.user) { "로그인한 계정을 확인할 수 없습니다." }
    }

    suspend fun reauthenticateCurrentUser(): FirebaseUser {
        check(FirebaseBridge.isReady(activity)) { "Firebase 로그인 설정을 먼저 확인해 주세요." }
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: error("삭제할 계정으로 로그인해 주세요.")
        user.reauthenticate(requestGoogleCredential()).await()
        check(auth.currentUser?.uid == user.uid) {
            "확인한 계정과 삭제 대상 계정이 다릅니다."
        }
        user.getIdToken(true).await()
        return user
    }

    private suspend fun requestGoogleCredential(): AuthCredential {
        val clientIdResource = activity.resources.getIdentifier(
            "default_web_client_id", "string", activity.packageName
        )
        check(clientIdResource != 0) { "Firebase Google 로그인 공급자가 설정되지 않았습니다." }
        val option = GetSignInWithGoogleOption.Builder(activity.getString(clientIdResource)).build()
        val result = try {
            CredentialManager.create(activity).getCredential(
                context = activity,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
        } catch (e: NoCredentialException) {
            throw IllegalStateException(
                "Google 로그인에 사용할 계정을 가져오지 못했습니다. Google Play 서비스를 업데이트하거나 기기에서 Google 계정에 다시 로그인한 뒤 시도해 주세요.",
                e
            )
        }
        val credential = result.credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "Google 계정 인증 정보가 올바르지 않습니다."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }

    suspend fun signOut() {
        if (FirebaseBridge.isReady(activity)) FirebaseAuth.getInstance().signOut()
        runCatching {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
