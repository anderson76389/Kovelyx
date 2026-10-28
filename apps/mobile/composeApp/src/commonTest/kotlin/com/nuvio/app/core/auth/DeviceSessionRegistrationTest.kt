package com.nuvio.app.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceSessionRegistrationTest {
    @Test
    fun buildsOfficialClientRegistrationPayload() {
        val params = buildDeviceRegistrationParams(
            installationId = "kovelyx-mobile-installation",
            clientVersion = "1.2.3",
            metadata = DeviceClientMetadata(
                clientName = "Kovelyx",
                deviceName = "Google Pixel 9",
                platform = "Android 16",
            ),
        )

        assertEquals("kovelyx-mobile-installation", params.getValue("p_installation_id").toString().trim('"'))
        assertEquals("Kovelyx", params.getValue("p_client_name").toString().trim('"'))
        assertEquals("1.2.3", params.getValue("p_client_version").toString().trim('"'))
        assertEquals("Android 16", params.getValue("p_platform").toString().trim('"'))
        assertEquals("Google Pixel 9", params.getValue("p_device_name").toString().trim('"'))
    }

    @Test
    fun combinesManufacturerAndModelWithoutRepeatingManufacturer() {
        assertEquals(
            "Samsung SM-S918B",
            formatDeviceName("Samsung", "SM-S918B", "Android device"),
        )
        assertEquals(
            "Google Pixel 9",
            formatDeviceName("Google", "Google Pixel 9", "Android device"),
        )
    }
}
