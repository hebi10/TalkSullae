package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomUnreadTest {
    @Test fun unreadIsActiveAndRecentOnly() {
        val day = 24L * 60 * 60 * 1000
        val now = 1000L * day
        val active = CloudRoomIndex("room", "방", "active", unreadCount = 8,
            lastChatAtMillis = now - day)
        assertEquals(8L, active.visibleUnread(now))
        assertEquals(0L, active.copy(status = "removed").visibleUnread(now))
        assertEquals(0L, active.copy(lastChatAtMillis = now - 90L * day).visibleUnread(now))
        assertEquals(0L, active.copy(lastChatAtMillis = null).visibleUnread(now))
        assertEquals(0L, active.copy(unreadCount = -1).visibleUnread(now))
    }
}
