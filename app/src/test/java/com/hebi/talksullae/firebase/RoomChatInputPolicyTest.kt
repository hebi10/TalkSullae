package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomChatInputPolicyTest {
    @Test
    fun validKoreanMessageAndNewlinesAreNormalized() {
        assertEquals("첫 줄\n둘째 줄", RoomChatInputPolicy.normalize("  첫 줄\r\n둘째 줄  "))
        assertNull(RoomChatInputPolicy.error("안녕하세요!"))
        assertNull(RoomChatInputPolicy.error("a".repeat(1000)))
    }

    @Test
    fun emptyOversizedAndControlInputsAreRejected() {
        assertEquals("메시지를 입력해 주세요.", RoomChatInputPolicy.error("    "))
        assertEquals(
            "메시지는 1000자 이하로 입력해 주세요.",
            RoomChatInputPolicy.error("가".repeat(1001))
        )
        assertEquals(
            "제어 문자가 포함되어 메시지를 전송할 수 없습니다.",
            RoomChatInputPolicy.error("제어\u0000문자")
        )
    }
}
