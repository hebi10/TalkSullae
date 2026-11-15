package com.hebi.talksullae.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkCodecTest {
    private val code = "a".repeat(20) + "." + "B".repeat(43)

    @Test fun roundTripsSecureInviteCode() {
        val link = InviteLinkCodec.encode(code)
        assertEquals("talksullae://invite?code=$code", link)
        assertEquals(code, InviteLinkCodec.read(link))
        assertEquals(code, InviteLinkCodec.normalizeInput(link!!))
        assertEquals(code, InviteLinkCodec.normalizeInput(code))
    }

    @Test fun rejectsUnsupportedDomainsAndTamperedPayload() {
        assertNull(InviteLinkCodec.read("https://example.com/invite?code=$code"))
        assertNull(InviteLinkCodec.read("talksullae://host?code=$code"))
        assertNull(InviteLinkCodec.read("talksullae://invite?code=$code&next=1"))
        assertNull(InviteLinkCodec.read("talksullae://invite/path?code=$code"))
        assertNull(InviteLinkCodec.read("talksullae://invite?code=invalid"))
        assertNull(InviteLinkCodec.read("talksullae://invite?code=$code#fragment"))
        assertNull(InviteLinkCodec.read("talksullae://guest@invite?code=$code"))
        assertNull(InviteLinkCodec.read("talksullae://invite?code=$code%0A"))
        assertNull(InviteLinkCodec.encode("short"))
        assertNull(InviteLinkCodec.normalizeInput("http://not-an-invite"))
    }
}
