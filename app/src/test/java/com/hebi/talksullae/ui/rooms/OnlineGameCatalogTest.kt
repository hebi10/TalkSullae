package com.hebi.talksullae.ui.rooms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineGameCatalogTest {
    @Test fun allSupportedGamesHaveDistinctGuidance() {
        assertEquals(setOf("identity","liar","forbidden","balance"),onlineGameCatalog.map{it.id}.toSet())
        assertTrue(onlineGameCatalog.all{it.players.isNotBlank()&&it.duration.isNotBlank()&&it.rules.isNotBlank()})
        assertEquals("친",roomInitial(" 친구 방"))
        assertEquals("방",roomInitial(""))
    }
}
