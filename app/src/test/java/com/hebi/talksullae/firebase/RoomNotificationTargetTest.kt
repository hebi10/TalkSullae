package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomNotificationTargetTest {
    @Test fun supportedNotificationRoutes() {
        val room = "a".repeat(20)
        assertEquals("chat", RoomNotificationTarget.parse("account",room,"message")?.section)
        assertEquals("games", RoomNotificationTarget.parse("account",room,"game")?.section)
        assertEquals("chat", RoomNotificationTarget.parse("account",room,"approved")?.section)
        assertNull(RoomNotificationTarget.parse("account","../room","message"))
        assertNull(RoomNotificationTarget.parse("account",room,"unknown"))
        assertNull(RoomNotificationTarget.parse(null,room,"message"))
        assertNull(RoomNotificationTarget.parse("a/b",room,"message"))
    }
}
