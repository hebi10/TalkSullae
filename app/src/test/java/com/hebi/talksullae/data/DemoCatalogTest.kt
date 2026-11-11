package com.hebi.talksullae.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCatalogTest {
    @Test
    fun `four distinct games have valid player limits`() {
        assertEquals(4, DemoCatalog.games.size)
        assertEquals(4, DemoCatalog.games.map { it.id }.distinct().size)
        assertTrue(DemoCatalog.games.all {
            it.minimumPlayers >= 2 && it.maximumPlayers >= it.minimumPlayers
        })
    }

    @Test
    fun `demo rooms and messages are local placeholders`() {
        assertTrue(DemoCatalog.rooms.isNotEmpty())
        assertTrue(DemoCatalog.rooms.all { it.memberCount > 0 })
        assertTrue(DemoCatalog.roomMessages.any { it.isOwn })
        assertTrue(DemoCatalog.anonymousMessages.any { it.isOwn })
    }

    @Test
    fun `sample rankings are ordered`() {
        assertEquals((1..DemoCatalog.ranking.size).toList(), DemoCatalog.ranking.map { it.place })
        assertEquals(DemoCatalog.ranking.map { it.points }.sortedDescending(), DemoCatalog.ranking.map { it.points })
    }
}
