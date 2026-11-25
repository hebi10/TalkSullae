package com.hebi.talksullae.firebase

data class RoomNotificationTarget(val recipientUid: String, val roomId: String, val kind: String) {
    val section: String get() = if (kind == "game") "games" else "chat"

    companion object {
        const val ACTION = "com.hebi.talksullae.OPEN_ROOM_NOTIFICATION"
        const val EXTRA_UID = "notificationRecipient"
        const val EXTRA_ROOM = "notificationRoom"
        const val EXTRA_KIND = "notificationKind"

        fun parse(uid: String?, roomId: String?, kind: String?): RoomNotificationTarget? {
            if (uid.isNullOrBlank() || uid.length > 128 || uid.any { it == '/' || it.isISOControl() } ||
                roomId == null || !roomId.matches(Regex("^[A-Za-z0-9]{20}$")) ||
                kind !in setOf("message", "game", "approved")) return null
            return RoomNotificationTarget(uid, roomId, requireNotNull(kind))
        }
    }
}
