package com.hebi.talksullae.firebase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class StoredRoomDraft(val draft: String, val pending: PendingRoomMessage?)

internal interface RoomChatDraftStore {
    fun load(roomId: String): StoredRoomDraft?
    fun save(roomId: String, value: StoredRoomDraft, durable: Boolean = false): Boolean
    fun complete(roomId: String, requestId: String)
}

internal class EncryptedRoomDraftStore(context: Context, private val uid: String) : RoomChatDraftStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun currentUser() = FirebaseAuth.getInstance().currentUser?.uid == uid
    private fun slot(roomId: String) = MessageDigest.getInstance("SHA-256")
        .digest("$uid:$roomId".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    override fun load(roomId: String): StoredRoomDraft? = synchronized(lock) {
        if (!currentUser()) return@synchronized null
        runCatching {
            val slot = slot(roomId)
            val stored = prefs.getString(slot, null) ?: return@runCatching null
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(slot.toByteArray(Charsets.UTF_8))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            val request = json.optString("requestId")
            val text = json.optString("pendingText")
            val pending = if (request.isNotEmpty() && text.isNotEmpty()) PendingRoomMessage(request, text) else null
            StoredRoomDraft(json.optString("draft"), pending)
        }.getOrNull()
    }

    override fun save(roomId: String, value: StoredRoomDraft, durable: Boolean): Boolean = synchronized(lock) {
        if (!currentUser()) return@synchronized false
        runCatching {
            val slot = slot(roomId)
            val editor = prefs.edit()
            if (value.draft.isEmpty() && value.pending == null) {
                editor.remove(slot)
            } else {
                val json = JSONObject().put("draft", value.draft)
                    .put("requestId", value.pending?.requestId.orEmpty())
                    .put("pendingText", value.pending?.text.orEmpty())
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                require(cipher.iv.size == 12)
                cipher.updateAAD(slot.toByteArray(Charsets.UTF_8))
                val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
                editor.putString(slot, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            if (durable) editor.commit() else { editor.apply(); true }
        }.getOrDefault(false)
    }

    override fun complete(roomId: String, requestId: String) = synchronized(lock) {
        val value = load(roomId)
        if (value?.pending?.requestId == requestId) save(roomId, value.copy(pending = null), durable = true)
        Unit
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    companion object {
        private const val PREFS = "room_chat_outbox_v1"
        private const val KEY = "talksullae_room_chat_outbox_v1"
        private val lock = Any()

        fun clearAll(context: Context) = synchronized(lock) {
            runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit() }
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(KEY) } }
            Unit
        }
    }
}
