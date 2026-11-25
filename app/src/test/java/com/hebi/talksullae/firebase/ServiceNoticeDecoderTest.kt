package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServiceNoticeDecoderTest {
    @Test fun disabledAndAlreadyAcceptedDoNotRequireAcknowledgement() {
        assertNull(ServiceNoticeDecoder.decode(mapOf("enabled" to false,"required" to false)))
        assertNull(ServiceNoticeDecoder.decode(mapOf("enabled" to true,"required" to false)))
    }
    @Test fun requiredNoticeNeedsBothVersionedDocuments() {
        val data=mapOf("enabled" to true,"required" to true,"termsVersion" to "1",
            "privacyVersion" to "2","termsText" to "이용약관","privacyText" to "개인정보 안내")
        assertEquals(ServiceNotice("1","2","이용약관","개인정보 안내"),ServiceNoticeDecoder.decode(data))
        assertThrows(IllegalStateException::class.java){ServiceNoticeDecoder.decode(data-"privacyText")}
        assertThrows(IllegalStateException::class.java){ServiceNoticeDecoder.decode(mapOf("enabled" to false,"required" to true))}
    }
}
