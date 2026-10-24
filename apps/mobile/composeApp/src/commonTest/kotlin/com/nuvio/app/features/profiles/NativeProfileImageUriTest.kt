package com.nuvio.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeProfileImageUriTest {
    @Test
    fun preservesOnlyManagedPrivateImageUris() {
        val imported = "file:///data/user/0/com.kovelyx.mobile/files/kovelyx-profile-media/avatar-a1b2.gif"
        assertTrue(imported.isValidAvatarUrl())
        assertEquals(imported, normalizedAvatarUrl(imported))
    }

    @Test
    fun rejectsArbitraryLocalFilesAndContentGrants() {
        assertFalse("file:///storage/emulated/0/Download/private.jpg".isValidAvatarUrl())
        assertFalse("content://gallery/provider/1".isValidAvatarUrl())
        assertFalse("file:///data/user/0/com.other.app/files/avatar.jpg".isValidAvatarUrl())
    }

    @Test
    fun keepsPublicHttpsAvatarUrls() {
        assertTrue("https://images.example.test/avatar.png".isValidAvatarUrl())
    }
}
