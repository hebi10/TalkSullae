package com.hebi.talksullae.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

data class CloudRoomIndex(
    val id: String,
    val name: String,
    val status: String,
    val updatedAtMillis: Long = 0L,
    val unreadCount: Long = 0L,
    val lastChatAtMillis: Long? = null,
    val roomIcon: String? = null,
    val memberCount: Int? = null,
    val gameInProgress: Boolean? = null
) {
    fun visibleUnread(nowMillis: Long): Long {
        val last = lastChatAtMillis ?: return 0L
        if (status != "active" || last <= nowMillis - 90L * 24 * 60 * 60 * 1000) return 0L
        return unreadCount.coerceAtLeast(0L)
    }
}

data class CloudRoom(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val memberCount: Int,
    val pendingCount: Int,
    val requiresApproval: Boolean,
    val activeGameSessionId: String? = null,
    val activeInviteHash: String? = null,
    val activeInviteExpiresAtMillis: Long? = null,
    val roomRevision: Long = 0L,
    val roomIcon: String? = null
)

data class CloudMember(
    val uid: String,
    val name: String,
    val status: String,
    val avatar: String? = null
)

data class JoinResult(val roomId: String, val status: String)
data class Invitation(val code: String, val expiresAtMillis: Long) {
    fun isCurrent(room: CloudRoom, nowMillis: Long): Boolean {
        if (expiresAtMillis <= nowMillis || !code.startsWith(room.id + ".")) return false
        val token = code.substringAfter('.')
        val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return hash == room.activeInviteHash
    }
}
data class InvitationPreview(
    val roomId: String,
    val roomName: String,
    val description: String,
    val memberCount: Int,
    val requiresApproval: Boolean,
    val expiresAtMillis: Long
)

internal interface OnlineRoomsRepository {
    fun watchMyRooms(uid: String, onResult: (List<CloudRoomIndex>) -> Unit, onError: (String) -> Unit): ListenerRegistration
    fun watchRoom(roomId: String, onResult: (CloudRoom?) -> Unit, onError: (String) -> Unit): ListenerRegistration
    fun watchMembers(roomId: String, onResult: (List<CloudMember>) -> Unit, onError: (String) -> Unit): ListenerRegistration
    suspend fun createRoom(name: String, description: String, approval: Boolean): String
    suspend fun updateRoom(roomId: String, name: String, description: String, approval: Boolean, expectedRevision: Long): Unit = error("방 정보 수정 기능을 사용할 수 없습니다.")
    suspend fun updateRoomIcon(roomId: String, icon: String?, expectedRevision: Long): Unit = error("방 아이콘 수정 기능을 사용할 수 없습니다.")
    suspend fun archiveRoom(roomId: String): Unit = error("방 종료 기능을 사용할 수 없습니다.")
    suspend fun revokeInvitation(roomId: String, expectedHash: String): Unit = error("초대 취소 기능을 사용할 수 없습니다.")
    suspend fun createInvitation(roomId: String): Invitation
    suspend fun previewInvitation(code: String): InvitationPreview
    suspend fun requestJoin(code: String): JoinResult
    suspend fun changeMember(roomId: String, targetUid: String, action: String)
    suspend fun leaveRoom(roomId: String)
}

internal class CloudRoomsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val callables: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3"),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : OnlineRoomsRepository {
    override fun watchMyRooms(uid: String, onResult: (List<CloudRoomIndex>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val gate = ServerSnapshotGate(reportInitialCache = true)
        return db.collection("userRooms").document(uid).collection("rooms")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onError("방 목록을 가져올 수 없습니다.")
                } else if (snapshot != null) {
                    when (gate.evaluate(snapshot.metadata.isFromCache, auth.currentUser?.uid == uid)) {
                        SnapshotAccess.WAIT -> return@addSnapshotListener
                        SnapshotAccess.HIDE -> {
                            onError("서버에서 방 접근 권한을 확인할 수 없습니다. 연결을 확인해 주세요.")
                            return@addSnapshotListener
                        }
                        SnapshotAccess.ACCEPT -> Unit
                    }
                    onResult(snapshot.documents.map {
                        CloudRoomIndex(
                            id = it.id,
                            name = it.getString("roomName").orEmpty(),
                            status = it.getString("status").orEmpty(),
                            updatedAtMillis = it.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
                            unreadCount = it.getLong("unreadCount")?.coerceAtLeast(0L) ?: 0L,
                            lastChatAtMillis = it.getTimestamp("lastChatAt")?.toDate()?.time,
                            roomIcon = it.getString("roomIcon"),
                            memberCount = it.getLong("memberCount")?.takeIf { n -> n in 0L..20L }?.toInt(),
                            gameInProgress = it.getBoolean("gameInProgress")
                        )
                    }.sortedByDescending { maxOf(it.updatedAtMillis, it.lastChatAtMillis ?: 0L) })
                }
            }
    }

    override fun watchRoom(roomId: String, onResult: (CloudRoom?) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = auth.currentUser?.uid
        val gate = ServerSnapshotGate()
        return db.collection("rooms").document(roomId).addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (error != null) onError("방 정보를 확인할 수 없습니다.")
            else if (doc != null) {
                when (gate.evaluate(doc.metadata.isFromCache, uid != null && uid == auth.currentUser?.uid)) {
                    SnapshotAccess.WAIT -> return@addSnapshotListener
                    SnapshotAccess.HIDE -> {
                        onError("방 접근 권한을 서버에서 확인하지 못했습니다.")
                        return@addSnapshotListener
                    }
                    SnapshotAccess.ACCEPT -> Unit
                }
                if (!doc.exists() || doc.getString("status") != "active") {
                    onError("현재 접근할 수 없는 방입니다.")
                    return@addSnapshotListener
                }
                onResult(
                CloudRoom(
                    id = doc.id,
                    name = doc.getString("name").orEmpty(),
                    description = doc.getString("description").orEmpty(),
                    ownerId = doc.getString("ownerId").orEmpty(),
                    memberCount = doc.getLong("memberCount")?.toInt() ?: 0,
                    pendingCount = doc.getLong("pendingCount")?.toInt() ?: 0,
                    requiresApproval = doc.getBoolean("requiresApproval") ?: true,
                    activeGameSessionId = doc.getString("activeGameSessionId"),
                    activeInviteHash = doc.getString("activeInviteHash"),
                    activeInviteExpiresAtMillis = doc.getTimestamp("activeInviteExpiresAt")?.toDate()?.time,
                    roomRevision = doc.getLong("roomRevision") ?: 0L,
                    roomIcon = doc.getString("roomIcon")
                )
                )
            }
        }
    }

    override fun watchMembers(roomId: String, onResult: (List<CloudMember>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = auth.currentUser?.uid
        val gate = ServerSnapshotGate()
        return db.collection("rooms").document(roomId).collection("members")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) onError("멤버 정보를 불러올 수 없습니다.")
                else if (snapshot != null) {
                    when (gate.evaluate(snapshot.metadata.isFromCache, uid != null && uid == auth.currentUser?.uid)) {
                        SnapshotAccess.WAIT -> return@addSnapshotListener
                        SnapshotAccess.HIDE -> {
                            onError("멤버 권한을 서버에서 확인하지 못했습니다.")
                            return@addSnapshotListener
                        }
                        SnapshotAccess.ACCEPT -> Unit
                    }
                    val own = snapshot.documents.firstOrNull { it.id == uid }
                    if (own?.getString("status") != "active") {
                        onError("방 접근 권한이 변경되었습니다.")
                        return@addSnapshotListener
                    }
                    onResult(snapshot.documents.map {
                    CloudMember(
                        uid = it.id,
                        name = it.getString("displayName").orEmpty(),
                        status = it.getString("status").orEmpty(),
                        avatar = it.getString("avatar")
                    )
                    }.filter { it.status == "active" || it.status == "pending" })
                }
            }
    }

    private suspend fun invoke(action: String, args: Map<String, Any>): Map<*, *> {
        val data = callables.getHttpsCallable(action).call(args).await().data
        return data as? Map<*, *> ?: error("서버 응답 형식이 올바르지 않습니다.")
    }

    override suspend fun createRoom(name: String, description: String, approval: Boolean): String =
        invoke("createRoom", mapOf("name" to name, "description" to description, "requiresApproval" to approval))
            .get("roomId") as? String ?: error("생성된 방을 확인할 수 없습니다.")

    override suspend fun updateRoom(roomId: String, name: String, description: String, approval: Boolean, expectedRevision: Long) {
        val result = invoke("updateRoom", mapOf("roomId" to roomId, "name" to name,
            "description" to description, "requiresApproval" to approval, "expectedRevision" to expectedRevision))
        check(result["revision"] is Number) { "방 정보 수정 결과를 확인할 수 없습니다." }
    }

    override suspend fun updateRoomIcon(roomId: String, icon: String?, expectedRevision: Long) {
        val result = callables.getHttpsCallable("updateRoomIcon")
            .call(mapOf("roomId" to roomId, "roomIcon" to icon, "expectedRevision" to expectedRevision)).await().data as? Map<*, *>
        check(result?.get("revision") is Number) { "방 아이콘 수정 결과를 확인할 수 없습니다." }
    }

    override suspend fun archiveRoom(roomId: String) {
        check(invoke("archiveRoom", mapOf("roomId" to roomId, "confirm" to "ARCHIVE"))["status"] == "archived") {
            "방 종료 결과를 확인할 수 없습니다."
        }
    }

    override suspend fun revokeInvitation(roomId: String, expectedHash: String) {
        check(invoke("revokeInvitation", mapOf("roomId" to roomId, "expectedHash" to expectedHash))["revoked"] == true) {
            "초대 취소 결과를 확인하지 못했습니다."
        }
    }

    override suspend fun createInvitation(roomId: String): Invitation {
        val result = invoke("createInvitation", mapOf("roomId" to roomId))
        return Invitation(
            code = result["code"] as? String ?: error("초대 코드를 확인할 수 없습니다."),
            expiresAtMillis = (result["expiresAt"] as? Number)?.toLong() ?: 0L
        )
    }

    override suspend fun previewInvitation(code: String): InvitationPreview {
        val result = invoke("previewInvitation", mapOf("code" to code))
        return InvitationPreview(
            roomId = result["roomId"] as? String ?: error("초대 방을 확인할 수 없습니다."),
            roomName = result["roomName"] as? String ?: error("초대 방 이름을 확인할 수 없습니다."),
            description = result["description"] as? String ?: "",
            memberCount = (result["memberCount"] as? Number)?.toInt() ?: 0,
            requiresApproval = result["requiresApproval"] == true,
            expiresAtMillis = (result["expiresAtMillis"] as? Number)?.toLong() ?: 0L
        )
    }

    override suspend fun requestJoin(code: String): JoinResult {
        val result = invoke("requestJoin", mapOf("code" to code))
        return JoinResult(
            roomId = result["roomId"] as? String ?: error("방을 확인할 수 없습니다."),
            status = result["status"] as? String ?: error("가입 상태를 확인할 수 없습니다.")
        )
    }

    override suspend fun changeMember(roomId: String, targetUid: String, action: String) {
        invoke("changeMember", mapOf("roomId" to roomId, "targetUid" to targetUid, "action" to action))
    }

    override suspend fun leaveRoom(roomId: String) {
        invoke("leaveRoom", mapOf("roomId" to roomId))
    }
}
