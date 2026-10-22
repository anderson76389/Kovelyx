package com.nuvio.tv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareTvProfileBindingTest {
    private val a = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val b = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    @Test fun singleProfileCanMatchWithoutMatchingLegacyName() {
        assertEquals(
            mapOf(1 to a),
            CloudflareTvProfileBinding.resolve(
                listOf(CloudflareTvProfileBinding.Local(1, "Profil 1")),
                listOf(CloudflareTvProfileBinding.Remote(a, "Principal"))
            )
        )
    }

    @Test fun multipleProfilesUseUniqueNamesNotPositions() {
        val links = CloudflareTvProfileBinding.resolve(
            listOf(CloudflareTvProfileBinding.Local(1, "Principal"),
                CloudflareTvProfileBinding.Local(2, "Enfants")),
            listOf(CloudflareTvProfileBinding.Remote(b, "enfants"),
                CloudflareTvProfileBinding.Remote(a, "Principal"))
        )
        assertEquals(mapOf(1 to a, 2 to b), links)
    }

    @Test fun ambiguousNamesNeverReceiveCloudData() {
        val links = CloudflareTvProfileBinding.resolve(
            listOf(CloudflareTvProfileBinding.Local(1, "Même nom"),
                CloudflareTvProfileBinding.Local(2, "Même nom")),
            listOf(CloudflareTvProfileBinding.Remote(a, "Même nom"),
                CloudflareTvProfileBinding.Remote(b, "Autre"))
        )
        assertTrue(links.isEmpty())
    }

    @Test fun unmatchedProfilesStayUnassociated() {
        val links = CloudflareTvProfileBinding.resolve(
            listOf(CloudflareTvProfileBinding.Local(1, "Moi"),
                CloudflareTvProfileBinding.Local(2, "Enfants")),
            listOf(CloudflareTvProfileBinding.Remote(a, "Inconnu"),
                CloudflareTvProfileBinding.Remote(b, "Autre"))
        )
        assertTrue(links.isEmpty())
    }

    @Test fun cachedCloudProfileBindingIsScopedToAccountAndLocalProfile() {
        val first = CloudflareTvLibraryStore.bindingKey(
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 1
        )
        val otherAccount = CloudflareTvLibraryStore.bindingKey(
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", 1
        )
        val otherLocalProfile = CloudflareTvLibraryStore.bindingKey(
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 2
        )
        assertTrue(first.startsWith("binding_"))
        assertTrue(first != otherAccount)
        assertTrue(first != otherLocalProfile)
        assertEquals(first, CloudflareTvLibraryStore.bindingKey(
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 1
        ))
    }

    @Test fun malformedOrDuplicatedIdentifiersCannotBeLinked() {
        assertTrue(CloudflareTvProfileBinding.resolve(
            listOf(CloudflareTvProfileBinding.Local(1, "Principal")),
            listOf(CloudflareTvProfileBinding.Remote("invalid", "Principal"))
        ).isEmpty())
        assertTrue(CloudflareTvProfileBinding.resolve(
            listOf(CloudflareTvProfileBinding.Local(1, "A"),
                CloudflareTvProfileBinding.Local(1, "B")),
            listOf(CloudflareTvProfileBinding.Remote(a, "A"),
                CloudflareTvProfileBinding.Remote(b, "B"))
        ).isEmpty())
    }
}
