package com.hebi.talksullae.firebase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileResponseGuardTest {
    @Test fun abandoningEditorDoesNotCancelPendingProfileRead() {
        val versions = ProfileRequestVersions()
        val pendingRead = versions.beginLoad()
        val pendingWrite = versions.beginSave()
        versions.invalidateSave()
        assertTrue(versions.isLatestLoad(pendingRead))
        assertFalse(versions.isLatestSave(pendingWrite))
        val nextRead = versions.beginLoad()
        assertFalse(versions.isLatestLoad(pendingRead))
        assertTrue(versions.isLatestLoad(nextRead))
    }

    @Test fun appliesOnlyToSameUidRouteAndRequestToken() {
        assertTrue(ProfileResponseGuard.isCurrent("first","first","profile-editor","profile-editor",7,7))
        assertFalse(ProfileResponseGuard.isCurrent("first","second","profile-editor","profile-editor",7,7))
        assertFalse(ProfileResponseGuard.isCurrent("first",null,"profile-editor","profile-editor",7,7))
        assertFalse(ProfileResponseGuard.isCurrent("first","first","profile-editor","main",7,7))
        assertFalse(ProfileResponseGuard.isCurrent("first","first","profile-editor","profile-editor",7,8))
        assertFalse(ProfileResponseGuard.isCurrent(null,null,"profile-editor","profile-editor",7,7))
    }
}
