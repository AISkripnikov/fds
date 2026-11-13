package com.airmaxfield.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityTest {
    @Test fun signal() {
        assertEquals(Quality.GOOD, signalQuality(-65))
        assertEquals(Quality.FAIR, signalQuality(-66))
        assertEquals(Quality.FAIR, signalQuality(-75))
        assertEquals(Quality.BAD, signalQuality(-76))
        assertEquals(Quality.UNKNOWN, signalQuality(null))
    }

    @Test fun snr() {
        assertEquals(Quality.GOOD, snrQuality(25))
        assertEquals(Quality.FAIR, snrQuality(15))
        assertEquals(Quality.BAD, snrQuality(14))
    }

    @Test fun percent() {
        assertEquals(Quality.GOOD, percentQuality(80))
        assertEquals(Quality.FAIR, percentQuality(50))
        assertEquals(Quality.BAD, percentQuality(49))
    }
}
