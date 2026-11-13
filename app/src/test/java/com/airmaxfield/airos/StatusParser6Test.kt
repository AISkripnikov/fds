package com.airmaxfield.airos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusParser6Test {
    @Test fun parses_full() {
        val s = StatusParser6.parse(fixture("airos6/status_full.json"))
        assertEquals(AirOsVersion.V6, s.version)
        assertEquals("NanoStation M5", s.model); assertEquals("Yug-B", s.hostname)
        assertEquals(-62, s.signalDbm); assertEquals(-95, s.noiseDbm); assertEquals(33, s.snrDb)
        assertEquals(listOf(-63, -61), s.chainSignalDbm)
        assertEquals(95, s.ccqPercent); assertEquals(91, s.airmaxQuality); assertEquals(74, s.airmaxCapacity)
        assertEquals(5180, s.frequencyMhz); assertEquals(40, s.channelWidthMhz)
        assertEquals(300.0, s.txRateMbps!!, 0.01); assertEquals(270.0, s.rxRateMbps!!, 0.01)
        assertNull(s.remoteSignalDbm)
        assertEquals("sta", s.mode); assertEquals(1500, s.distanceM); assertEquals("100 Мбит/с", s.lanSpeed)
    }

    @Test fun parses_minimal() {
        val s = StatusParser6.parse(fixture("airos6/status_minimal.json"))
        assertEquals("Old", s.hostname)
        assertNull(s.signalDbm); assertTrue(s.chainSignalDbm.isEmpty()); assertNull(s.ccqPercent); assertNull(s.frequencyMhz)
    }
}
