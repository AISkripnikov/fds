package com.airmaxfield.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class UbntDiscoveryParserTest {
    private fun tlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
        out.write(type); out.write(value.size shr 8); out.write(value.size and 0xFF); out.write(value)
    }

    private fun packet(build: (ByteArrayOutputStream) -> Unit): ByteArray {
        val body = ByteArrayOutputStream().also(build).toByteArray()
        return byteArrayOf(1, 0, (body.size shr 8).toByte(), (body.size and 0xFF).toByte()) + body
    }

    private val mac = byteArrayOf(0x24, 0x5A, 0x4C, 0x11, 0x22, 0x33)
    private val ip = byteArrayOf(192.toByte(), 168.toByte(), 1, 20)

    @Test fun parses_full_reply() {
        val p = packet {
            tlv(it, 0x02, mac + ip)
            tlv(it, 0x0B, "Sever-A".toByteArray())
            tlv(it, 0x0C, "LOCO5AC".toByteArray())
            tlv(it, 0x14, "NanoStation 5AC loco".toByteArray())
            tlv(it, 0x03, "WA.v8.7.11".toByteArray())
        }
        assertEquals(
            Discovered("192.168.1.20", "24:5A:4C:11:22:33", "Sever-A", "NanoStation 5AC loco", "WA.v8.7.11", null),
            UbntDiscoveryParser.parse(p, "192.168.1.20"),
        )
    }

    @Test fun short_model_used_when_no_full() {
        val p = packet { tlv(it, 0x0C, "LOCO5AC".toByteArray()); tlv(it, 0x0D, "LINK".toByteArray()) }
        val d = UbntDiscoveryParser.parse(p, "10.0.0.5")!!
        assertEquals("LOCO5AC", d.model); assertEquals("LINK", d.essid); assertEquals("10.0.0.5", d.ip)
    }

    @Test fun truncated_packet_returns_null() {
        assertNull(UbntDiscoveryParser.parse(byteArrayOf(1, 0), "1.2.3.4"))
        val p = packet { tlv(it, 0x0B, "Sever".toByteArray()) }
        assertNull(UbntDiscoveryParser.parse(p.copyOf(p.size - 2), "1.2.3.4"))
    }

    @Test fun unknown_tlv_skipped() {
        val p = packet { tlv(it, 0x7E, byteArrayOf(1, 2, 3)); tlv(it, 0x0B, "X".toByteArray()) }
        assertEquals("X", UbntDiscoveryParser.parse(p, "1.2.3.4")!!.hostname)
    }

    @Test fun request_packet_is_ignored() {
        assertNull(UbntDiscoveryParser.parse(byteArrayOf(1, 0, 0, 0), "1.2.3.4"))
    }
}
