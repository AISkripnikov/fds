package com.airmaxfield.airos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusParser8Test {
    @Test fun parses_full() {
        val s = StatusParser8.parse(fixture("airos8/status_full.json"))
        assertEquals(AirOsVersion.V8, s.version)
        assertEquals("Sever-A", s.hostname); assertEquals("NanoStation 5AC loco", s.model); assertEquals("v8.7.11", s.firmware)
        assertEquals(-58, s.signalDbm); assertEquals(-92, s.noiseDbm); assertEquals(34, s.snrDb)
        assertEquals(listOf(-59, -61), s.chainSignalDbm); assertEquals(-60, s.remoteSignalDbm)
        assertEquals(96, s.airmaxQuality); assertEquals(78, s.airmaxCapacity)
        assertEquals(5180, s.frequencyMhz); assertEquals(40, s.channelWidthMhz)
        assertEquals(520.0, s.txRateMbps!!, 0.01); assertEquals(480.0, s.rxRateMbps!!, 0.01)
        assertEquals("ap-ptp", s.mode); assertEquals(2400, s.distanceM); assertEquals("100 Мбит/с", s.lanSpeed)
    }

    @Test fun parses_minimal() {
        val s = StatusParser8.parse(fixture("airos8/status_minimal.json"))
        assertEquals("Bare", s.hostname)
        assertNull(s.signalDbm); assertTrue(s.chainSignalDbm.isEmpty()); assertNull(s.snrDb); assertNull(s.airmaxQuality)
    }

    @Test fun rates_fall_back_to_airmax_capacity() {
        val json = """{"wireless":{"noisef":-90,"sta":[{"signal":-60,"airmax":{"downlink_capacity":300000,"uplink_capacity":250000}}]}}"""
        val s = StatusParser8.parse(json)
        assertEquals(300.0, s.txRateMbps!!, 0.01); assertEquals(250.0, s.rxRateMbps!!, 0.01)
    }

    @Test fun garbage_is_protocol_error() {
        assertThrows(AirOsError.Protocol::class.java) { StatusParser8.parse("<html>") }
    }
}
