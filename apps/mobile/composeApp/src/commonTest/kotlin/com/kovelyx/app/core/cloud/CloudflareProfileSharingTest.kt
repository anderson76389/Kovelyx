package com.kovelyx.app.core.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.*

class CloudflareProfileSharingTest {
    private val first = Json.parseToJsonElement(
        """{"defaultsVersion":1,"items":[
          {"url":"https://addons.example/manifest.json","name":"Mon extension","enabled":false},
          {"url":"https://only-target.example/manifest.json","name":"Locale","enabled":true}
        ]}"""
    ).jsonObject
    private val second = Json.parseToJsonElement(
        """{"defaultsVersion":1,"items":[
          {"url":"https://ADDONS.EXAMPLE/manifest.json","name":"Ext distante","enabled":true},
          {"url":"https://new.example/config/Private/manifest.json?access=VALUE","name":"Nouvelle","enabled":true}
        ]}"""
    ).jsonObject

    @Test
    fun recipientKeepsItsConfigurationAndOrder() {
        val merged = CloudflareProfileSharing.mergeAddons(first,second)
        val items = merged["items"]!!.jsonArray
        assertEquals(3,items.size)
        assertEquals("Mon extension",items[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(false,items[0].jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("Locale",items[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Nouvelle",items[2].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun copyingTwiceDoesNotDuplicateConfiguredExtensions() {
        val firstMerge = CloudflareProfileSharing.mergeAddons(first,second)
        val secondMerge = CloudflareProfileSharing.mergeAddons(firstMerge,second)
        assertEquals(firstMerge,secondMerge)
    }

    @Test
    fun emptyIncomingLeavesExistingExtensions() {
        val origin = Json.parseToJsonElement("""{"items":[]}""").jsonObject
        val merged = CloudflareProfileSharing.mergeAddons(first,origin)
        assertEquals(2,merged["items"]!!.jsonArray.size)
    }

    @Test
    fun malformedUrlIsRejectedWithoutModifyingRecipient() {
        val invalid = Json.parseToJsonElement("""{"items":[{"url":"javascript:alert(1)"}]}""").jsonObject
        assertFailsWith<IllegalArgumentException> {
            CloudflareProfileSharing.mergeAddons(first,invalid)
        }
        assertEquals(2,first["items"]!!.jsonArray.size)
    }
}
