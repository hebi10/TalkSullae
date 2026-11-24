package com.hebi.talksullae.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import java.text.Normalizer
import kotlinx.coroutines.tasks.await

data class UserProfile(val nickname: String, val avatar: String)

object NicknamePolicy {
    val avatars = listOf("부엉이", "토끼", "고양이", "강아지", "곰", "여우", "판다", "펭귄")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC).trim()

    fun isValid(value: String): Boolean {
        val nickname = normalize(value)
        val size = nickname.codePointCount(0, nickname.length)
        return size in 2..16 && nickname.none {
            it.code < 32 || it.code in 127..159
        }
    }
}

internal class UserProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) {
    suspend fun load(uid: String): UserProfile? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null
        val nickname = snapshot.getString("nickname") ?: return null
        val avatar = snapshot.getString("avatar") ?: return null
        return if (NicknamePolicy.isValid(nickname) && avatar in NicknamePolicy.avatars) {
            UserProfile(nickname, avatar)
        } else null
    }

    suspend fun save(nickname: String, avatar: String): UserProfile {
        require(NicknamePolicy.isValid(nickname) && avatar in NicknamePolicy.avatars) {
            "닉네임과 아바타를 확인해 주세요."
        }
        val result = functions.getHttpsCallable("saveMyProfile").call(
            mapOf("nickname" to NicknamePolicy.normalize(nickname), "avatar" to avatar)
        ).await().data as? Map<*, *> ?: error("프로필 저장 결과를 확인할 수 없습니다.")
        val savedName = result["nickname"] as? String ?: error("저장된 닉네임을 확인할 수 없습니다.")
        val savedAvatar = result["avatar"] as? String ?: error("저장된 아바타를 확인할 수 없습니다.")
        return UserProfile(savedName, savedAvatar)
    }
}
