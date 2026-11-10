package com.hebi.talksullae.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomUiModelsTest {
    @Test
    fun roomNameMustContainBetween2And24Characters() {
        assertFalse(RoomInputRules.valid(" ", ""))
        assertFalse(RoomInputRules.valid("a", ""))
        assertFalse(RoomInputRules.valid("가".repeat(25), ""))
        assertTrue(RoomInputRules.valid("방 이름", ""))
        assertNull(RoomInputRules.nameError("  주말 톡술래 "))
        assertEquals("방 이름에는 줄바꿈을 사용할 수 없습니다.", RoomInputRules.nameError("첫째\n둘째"))
    }

    @Test
    fun descriptionCanBeEmptyButNotTooLong() {
        assertNull(RoomInputRules.descriptionError(""))
        assertNull(RoomInputRules.descriptionError("설명".repeat(60)))
        assertFalse(RoomInputRules.valid("방 이름", "길다".repeat(61)))
    }

    @Test
    fun ownerIsDistinguishedFromRegularMember() {
        assertEquals(DemoRoomMembers.MY_ID, DemoRoomMembers.initialOwner("friday"))
        assertEquals("minji", DemoRoomMembers.initialOwner("friends"))
        assertEquals(6, DemoRoomMembers.initialMembers("friday").size)
        assertEquals(1, DemoRoomMembers.initialMembers("local-1").size)
    }

    @Test
    fun invitationStatesHaveDistinctMessages() {
        val messages = JoinPreviewState.entries.map { joinStateMessage(it).first }
        assertEquals(6, messages.size)
        assertEquals(6, messages.toSet().size)
    }
}
