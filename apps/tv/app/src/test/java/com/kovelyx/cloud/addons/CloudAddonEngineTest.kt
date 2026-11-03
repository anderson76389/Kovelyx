package com.kovelyx.cloud.addons

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class CloudAddonEngineTest {
    private val one = listOf(CloudAddon("https://addon.example/config%2FA/manifest.json?lang=fr", "Personnel", false))
    @Test fun `two devices share exact configured URLs flags names and order`() = runTest {
        val stores = List(2) { mutableMapOf<String, String>() }
        var cloud: CloudAddonRow? = null
        suspend fun sync(device: Int, local: List<CloudAddon>): List<CloudAddon> {
            var applied = local
            CloudAddonEngine({ stores[device][it] }, { key, value -> stores[device][key] = value })
                .synchronize("owner.profile", local, cloud, { items, revision ->
                    assertEquals(cloud?.revision ?: 0, revision)
                    cloud = CloudAddonRow(revision + 1, items)
                }, { applied = it; true })
            return applied
        }
        sync(0, one)
        assertEquals(one, sync(1, emptyList()))
        // Empty is an intentional removal, not a request to resurrect defaults.
        sync(1, emptyList())
        assertEquals(emptyList<CloudAddon>(), sync(0, one))
    }
    @Test fun `failed first upload survives restart and retries against fresh revision`() = runTest {
        val state = mutableMapOf<String, String>()
        fun engine() = CloudAddonEngine({ state[it] }, { key, value -> state[key] = value })
        var failed = false
        try { engine().synchronize("owner.profile", one, null, { _, _ -> error("offline") }, { true }) }
        catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        var sent: List<CloudAddon>? = null
        engine().synchronize("owner.profile", one, CloudAddonRow(4, emptyList()), { items, revision ->
            assertEquals(4, revision); sent = items
        }, { true })
        assertEquals(one, sent)
    }
    @Test fun `another owner never inherits checkpoints and a concurrent edit is not acknowledged`() = runTest {
        val state = mutableMapOf<String, String>()
        val engine = CloudAddonEngine({ state[it] }, { key, value -> state[key] = value })
        engine.synchronize("alice.profile", one, null, { _, _ -> }, { false })
        assertTrue(state.getValue("alice.profile").contains("\"pending\":true"))
        var uploaded = false
        var applied: List<CloudAddon>? = null
        engine.synchronize("bob.profile", one, CloudAddonRow(2, emptyList()), { _, _ -> uploaded = true }, { applied = it; true })
        assertTrue(!uploaded)
        assertEquals(emptyList<CloudAddon>(), applied)
    }
    @Test fun `unchanged poll does not upload and local reordering is propagated`() = runTest {
        val state = mutableMapOf<String, String>()
        val engine = CloudAddonEngine({ state[it] }, { key, value -> state[key] = value })
        val two = one + CloudAddon("https://second.example/manifest.json")
        engine.synchronize("a.p", two, CloudAddonRow(5, two), { _, _ -> error("echo") }, { true })
        engine.synchronize("a.p", two, CloudAddonRow(5, two), { _, _ -> error("echo") }, { true })
        var sent: List<CloudAddon>? = null
        engine.synchronize("a.p", two.reversed(), CloudAddonRow(5, two), { items, revision ->
            assertEquals(5, revision); sent = items
        }, { true })
        assertEquals(two.reversed(), sent)
    }
}
