package com.nuvio.app.features.profiles

import com.nuvio.app.core.storage.DesktopStorage
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileLocalImagesDesktopTest {
    @Test
    fun `image references are accepted only inside Kovelyx managed profile media`() {
        if (!ProfileLocalImages.supported) return
        val managed = DesktopStorage.rootDir.resolve("profile-media")
        Files.createDirectories(managed)
        val file = Files.createTempFile(managed, "profile-test-", ".png")
        val outside = Files.createTempFile("outside-kovelyx-", ".png")
        try {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", file.toFile())
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", outside.toFile())
            assertTrue(ProfileLocalImages.isManagedUri(file.toUri().toString()))
            assertFalse(ProfileLocalImages.isManagedUri(outside.toUri().toString()))
            assertFalse(ProfileLocalImages.isManagedUri("file:///Windows/System32/kernel32.dll"))
            assertEquals(file.toUri().toString(), normalizedAvatarUrl(file.toUri().toString()))
            assertEquals(null, normalizedAvatarUrl(outside.toUri().toString()))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `stored images are scoped to the account and profile index`() {
        if (!ProfileLocalImages.supported) return
        val imageDir = DesktopStorage.rootDir.resolve("profile-media")
        Files.createDirectories(imageDir)
        val image = Files.createTempFile(imageDir, "avatar-test-", ".png")
        val owner = "local-profile-test-${UUID.randomUUID()}"
        try {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", image.toFile())
            val url = image.toUri().toString()
            ProfileLocalImages.saveImage(owner, 1, false, url)
            assertEquals(url, ProfileLocalImages.savedImage(owner, 1, false))
            assertEquals(null, ProfileLocalImages.savedImage(owner, 2, false))
            assertEquals(null, ProfileLocalImages.savedImage("other-$owner", 1, false))
            assertEquals(null, ProfileLocalImages.savedImage(owner, 1, true))
            ProfileLocalImages.saveImage(owner, 1, false, null)
            assertEquals(null, ProfileLocalImages.savedImage(owner, 1, false))
        } finally {
            ProfileLocalImages.saveImage(owner, 1, false, null)
            Files.deleteIfExists(image)
        }
    }
}
