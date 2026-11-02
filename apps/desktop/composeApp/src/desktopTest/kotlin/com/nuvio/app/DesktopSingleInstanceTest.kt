package com.nuvio.app

import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSingleInstanceTest {
    @Test fun secondLaunchForwardsExactUrlsAndLockIsReleasedAfterExit() {
        val root = Files.createTempDirectory("kovelyx-instance-")
        val received = LinkedBlockingQueue<List<String>>()
        try {
            DesktopSingleInstance(root).use { first ->
                assertTrue(first.acquireOrForward(emptyArray()) { received.put(it.toList()) })
                DesktopSingleInstance(root).use { second ->
                    val args = arrayOf("kovelyx://import", "stremio://example.org/CaseSensitive/manifest.json?token=AbC")
                    assertFalse(second.acquireOrForward(args) { error("Not the owner") })
                    assertEquals(args.toList(), received.poll(5, TimeUnit.SECONDS))
                    assertFalse(second.acquireOrForward(emptyArray()) { error("Not the owner") })
                    assertEquals(emptyList(), received.poll(5, TimeUnit.SECONDS))
                }
            }
            DesktopSingleInstance(root).use { reopened ->
                assertTrue(reopened.acquireOrForward(emptyArray()) {})
            }
        } finally { root.toFile().deleteRecursively() }
    }
}
