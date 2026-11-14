package com.airmaxfield.airos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Живой LiteBeam 5AC, airOS 8.7.9, станция (обезличенный дамп заказчика, 2026-11-14). */
class RealLb5acTest {
    private val json = fixture("airos8/status_lb5ac_879_sta.json")

    @Test fun status_fields_from_real_unit() {
        val s = StatusParser8.parse(json)
        assertEquals("LiteBeam 5AC", s.model)
        assertEquals("v8.7.9", s.firmware)
        assertEquals("sta-ptmp", s.mode)
        assertEquals(5810, s.frequencyMhz); assertEquals(40, s.channelWidthMhz)
        assertEquals(-45, s.signalDbm); assertEquals(-95, s.noiseDbm); assertEquals(50, s.snrDb)
        assertEquals(listOf(-47, -48), s.chainSignalDbm)
        assertEquals(-41, s.remoteSignalDbm)
        assertNull(s.airmaxQuality)                  // в 8.7.9 такого поля нет…
        assertEquals(96, s.linkScore)                // …зато есть оценка линка
        assertEquals(46, s.airtimePercent)
        // станция: передача = uplink 298.8, приём = downlink 150.66 Мбит/с
        assertEquals(298.8, s.txRateMbps!!, 0.01)
        assertEquals(150.66, s.rxRateMbps!!, 0.01)
        assertEquals("1000 Мбит/с", s.lanSpeed)
    }

    @Test fun remote_side_without_connecting_to_it() {
        val r = StatusParser8.parse(json).remote!!
        assertEquals("LiteBeam 5AC", r.model)
        assertEquals("v8.7.11", r.firmware)
        assertEquals("192.168.1.141", r.ip)
        assertEquals("ap-ptmp", r.mode)
        assertEquals(-41, r.signalDbm); assertEquals(-92, r.noiseDbm)
        assertEquals(listOf(-40, -40), r.chainSignalDbm)
        assertEquals(10, r.txPowerDbm)
        assertEquals("100 Мбит/с", r.lanSpeed)
    }

    @Test fun station_list_from_real_unit() {
        val st = StationsParser.fromStatus8(json).single()
        assertEquals("192.168.1.141", st.ip)
        assertEquals(96, st.airmaxQuality)
        assertEquals(298.8, st.txRateMbps!!, 0.01)
        assertEquals(2, st.latencyMs)
    }
}
