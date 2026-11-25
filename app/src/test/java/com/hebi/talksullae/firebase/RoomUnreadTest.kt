package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun sharedActivitySubtractsOwnSendsAndKeepsLegacyCarry() {
        val activity = SharedUnreadActivity(2,10,3,1000)
        assertEquals(5L, activity.count(2,6,1,3))
        assertEquals(0L, activity.count(2,10,3,0))
        assertEquals(7L, activity.count(1,30,20,100))
        assertNull(activity.count(3,0,0,0))
        assertNull(activity.count(2,11,3,0))
        assertNull(activity.count(2,6,4,0))
    }
}
