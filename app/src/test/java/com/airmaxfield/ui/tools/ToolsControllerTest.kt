package com.airmaxfield.ui.tools

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.DemoClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolsControllerTest {
    @Test fun close_cancels_queued_reboot() = runTest {
        var reboots = 0
        val client = object : AirOsClient by DemoClient() {
            override suspend fun reboot() { delay(5000); reboots++ } // стоит в очереди за долгим опросом
        }
        val t = ToolsController(client, "192.168.1.20", { null }, null, backgroundScope)
        t.reboot()
        t.close() // пользователь отключился / выбрал другую пушку
        advanceUntilIdle()
        assertEquals(0, reboots)
    }
}
