package com.hebi.talksullae.firebase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class InvitationCurrentTest {
    @Test fun onlyCurrentUnexpiredRoomCodeCanBeShared() {
        val id = "a".repeat(20)
        val token = "B".repeat(43)
        val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val room = CloudRoom(id, "방", "", "owner", 1, 0, true, activeInviteHash = hash)
        val invite = Invitation("$id.$token", 2_000L)
        assertTrue(invite.isCurrent(room, 1_000L))
        assertFalse(invite.isCurrent(room, 2_000L))
        assertFalse(invite.isCurrent(room.copy(activeInviteHash = "rotated"), 1_000L))
        assertFalse(invite.isCurrent(room.copy(id = "c".repeat(20)), 1_000L))
    }
}
