package com.airmaxfield.net

import com.airmaxfield.net.LocalNet.Kind.CELLULAR
import com.airmaxfield.net.LocalNet.Kind.ETHERNET
import com.airmaxfield.net.LocalNet.Kind.WIFI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSelectorTest {
    @Test fun prefers_wifi_in_same_subnet_and_never_cellular() {
        val cell = LocalNet("c", CELLULAR, "10.20.0.5", 24, true)
        val wifi = LocalNet("w", WIFI, "192.168.172.10", 24, false)
        val eth = LocalNet("e", ETHERNET, "192.168.1.100", 24, false)
        assertEquals("e", chooseNetwork(listOf(cell, wifi, eth), "192.168.1.20")?.id)
        assertEquals("w", chooseNetwork(listOf(cell, wifi, eth), "192.168.172.1")?.id)
        assertEquals("w", chooseNetwork(listOf(cell, wifi), "10.0.0.1")?.id)
        assertNull(chooseNetwork(listOf(cell), "192.168.1.20"))
    }

    @Test fun cellular_in_same_subnet_still_ignored() {
        val cell = LocalNet("c", CELLULAR, "192.168.1.5", 24, true)
        val eth = LocalNet("e", ETHERNET, "10.0.0.2", 24, false)
        assertEquals("e", chooseNetwork(listOf(cell, eth), "192.168.1.20")?.id)
    }

    @Test fun private_ranges() {
        assertTrue(isPrivateIp("192.168.1.20")); assertTrue(isPrivateIp("10.1.2.3"))
        assertTrue(isPrivateIp("172.31.0.1")); assertTrue(isPrivateIp("169.254.1.1"))
        assertFalse(isPrivateIp("172.32.0.1")); assertFalse(isPrivateIp("8.8.8.8"))
        assertFalse(isPrivateIp("example.com")); assertFalse(isPrivateIp("127.0.0.1"))
    }

    @Test fun in_subnet() {
        assertTrue(inSameSubnet("192.168.1.100", 24, "192.168.1.20"))
        assertFalse(inSameSubnet("192.168.2.100", 24, "192.168.1.20"))
        assertTrue(inSameSubnet("10.0.5.1", 8, "10.200.0.1"))
    }
}
