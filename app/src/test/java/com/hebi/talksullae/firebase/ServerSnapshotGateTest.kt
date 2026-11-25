package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSnapshotGateTest {
    @Test fun cachedInitialAndDisconnectedSnapshotsAreNotAuthorized() {
        val gate=ServerSnapshotGate()
        assertEquals(SnapshotAccess.WAIT,gate.evaluate(true))
        assertEquals(SnapshotAccess.ACCEPT,gate.evaluate(false))
        assertEquals(SnapshotAccess.HIDE,gate.evaluate(true))
        assertEquals(SnapshotAccess.WAIT,gate.evaluate(true))
        assertEquals(SnapshotAccess.ACCEPT,gate.evaluate(false))
        assertEquals(SnapshotAccess.HIDE,gate.evaluate(false,false))
    }
    @Test fun initialListCacheAndExpiredMessagesAreHidden() {
        assertEquals(SnapshotAccess.HIDE,ServerSnapshotGate(true).evaluate(true))
        assertFalse(unexpiredMessage(1000L,1000L))
        assertFalse(unexpiredMessage(999L,1000L))
        assertTrue(unexpiredMessage(1001L,1000L))
        assertTrue(unexpiredMessage(null,1000L))
    }
}
