package com.airmaxfield.airos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StationsParserTest {
    @Test fun v8_from_status() {
        val list = StationsParser.fromStatus8(fixture("airos8/status_full.json"))
        assertEquals(1, list.size)
        val s = list[0]
        assertEquals("24:5A:4C:AA:BB:CC", s.mac); assertEquals("Sever-B", s.name); assertEquals("192.168.1.21", s.ip)
        assertEquals(-58, s.signalDbm); assertEquals(-60, s.remoteSignalDbm); assertEquals(-92, s.noiseDbm)
        assertEquals(listOf(-59, -61), s.chainSignalDbm)
        assertEquals(96, s.airmaxQuality); assertEquals(78, s.airmaxCapacity)
        assertEquals(520.0, s.txRateMbps!!, 0.01); assertEquals(2400, s.distanceM)
        assertEquals(3600L, s.uptimeSec); assertEquals(2, s.latencyMs)
    }

    @Test fun v6_from_sta_cgi() {
        val list = StationsParser.fromSta6(fixture("airos6/sta.json"))
        assertEquals(2, list.size)
        val a = list[0]
        assertEquals("Yug-A", a.name); assertEquals(-61, a.signalDbm); assertEquals(-63, a.remoteSignalDbm)
        assertEquals(listOf(-62, -60), a.chainSignalDbm); assertEquals(95, a.ccqPercent)
        assertEquals(90, a.airmaxQuality); assertEquals(73, a.airmaxCapacity)
        assertEquals(300.0, a.txRateMbps!!, 0.01); assertEquals(270.0, a.rxRateMbps!!, 0.01); assertEquals(3, a.latencyMs)
        val b = list[1]
        assertNull(b.name); assertNull(b.remoteSignalDbm); assertNull(b.airmaxQuality); assertEquals(61, b.ccqPercent)
        assertTrue(b.chainSignalDbm.isEmpty())
    }

    @Test fun empty_and_garbage() {
        assertTrue(StationsParser.fromSta6("[]").isEmpty())
        assertTrue(StationsParser.fromStatus8(fixture("airos8/status_minimal.json")).isEmpty())
        assertThrows(AirOsError.Protocol::class.java) { StationsParser.fromSta6("<html>login</html>") }
    }
}
