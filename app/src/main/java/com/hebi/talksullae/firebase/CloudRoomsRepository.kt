package com.hebi.talksullae.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class CloudRoomIndex(
    val id: String,
    val name: String,
    val status: String
)

data class CloudRoom(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val memberCount: Int,
    val pendingCount: Int,
    val requiresApproval: Boolean
)

data class CloudMember(
    val uid: String,
    val name: String,
    val status: String
)

data class JoinResult(val roomId: String, val status: String)
data class Invitation(val code: String, val expiresAtMillis: Long)

class CloudRoomsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val callables: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) {
    fun watchMyRooms(uid: String, onResult: (List<CloudRoomIndex>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("userRooms").document(uid).collection("rooms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError("방 목록을 가져올 수 없습니다.")
                } else if (snapshot != null) {
                    onResult(snapshot.documents.map {
                        CloudRoomIndex(
                            id = it.id,
                            name = it.getString("roomName").orEmpty(),
                            status = it.getString("status").orEmpty()
                        )
                    }.sortedBy { it.name })
                }
            }

    fun watchRoom(roomId: String, onResult: (CloudRoom?) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("rooms").document(roomId).addSnapshotListener { doc, error ->
            if (error != null) onError("방 정보를 확인할 수 없습니다.")
            else if (doc == null || !doc.exists()) onResult(null)
            else onResult(
                CloudRoom(
                    id = doc.id,
                    name = doc.getString("name").orEmpty(),
                    description = doc.getString("description").orEmpty(),
                    ownerId = doc.getString("ownerId").orEmpty(),
                    memberCount = doc.getLong("memberCount")?.toInt() ?: 0,
                    pendingCount = doc.getLong("pendingCount")?.toInt() ?: 0,
                    requiresApproval = doc.getBoolean("requiresApproval") ?: true
                )
            )
        }

    fun watchMembers(roomId: String, onResult: (List<CloudMember>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("rooms").document(roomId).collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError("멤버 정보를 불러올 수 없습니다.")
                else if (snapshot != null) onResult(snapshot.documents.map {
                    CloudMember(
                        uid = it.id,
                        name = it.getString("displayName").orEmpty(),
                        status = it.getString("status").orEmpty()
                    )
                }.filter { it.status == "active" || it.status == "pending" })
            }

    private suspend fun invoke(action: String, args: Map<String, Any>): Map<*, *> {
        val data = callables.getHttpsCallable(action).call(args).await().data
        return data as? Map<*, *> ?: error("서버 응답 형식이 올바르지 않습니다.")
    }

    suspend fun createRoom(name: String, description: String, approval: Boolean): String =
        invoke("createRoom", mapOf("name" to name, "description" to description, "requiresApproval" to approval))
            .get("roomId") as? String ?: error("생성된 방을 확인할 수 없습니다.")

    suspend fun createInvitation(roomId: String): Invitation {
        val result = invoke("createInvitation", mapOf("roomId" to roomId))
        return Invitation(
            code = result["code"] as? String ?: error("초대 코드를 확인할 수 없습니다."),
            expiresAtMillis = (result["expiresAt"] as? Number)?.toLong() ?: 0L
        )
    }

    suspend fun requestJoin(code: String): JoinResult {
        val result = invoke("requestJoin", mapOf("code" to code))
        return JoinResult(
            roomId = result["roomId"] as? String ?: error("방을 확인할 수 없습니다."),
            status = result["status"] as? String ?: error("가입 상태를 확인할 수 없습니다.")
        )
    }

    suspend fun changeMember(roomId: String, targetUid: String, action: String) {
        invoke("changeMember", mapOf("roomId" to roomId, "targetUid" to targetUid, "action" to action))
    }

    suspend fun leaveRoom(roomId: String) {
        invoke("leaveRoom", mapOf("roomId" to roomId))
    }
}
