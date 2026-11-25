package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileAvatarCompatibilityTest {
    @Test fun modernPreferredAndUnknownDoesNotForceSetup() {
        assertEquals("펭귄",NicknamePolicy.resolveAvatar("펭귄","부엉이"))
        assertEquals("토끼",NicknamePolicy.resolveAvatar("unknown","토끼"))
        assertEquals("여우",NicknamePolicy.resolveAvatar(null,"여우"))
        assertEquals("부엉이",NicknamePolicy.resolveAvatar(null,"unknown"))
    }
}
