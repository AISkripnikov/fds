package com.airmaxfield.help

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyTitlesTest {
    @Test fun real_airos8_keys_from_screenshot_have_titles() {
        assertEquals("Усиление антенны", KeyTitles.titleFor("radio.1.antenna.gain"))
        assertEquals("Антенна", KeyTitles.titleFor("radio.1.antenna.id"))
        assertEquals("ATPC: разрешить точке управлять мощностью", KeyTitles.titleFor("radio.1.atpc.sta.status"))
        assertEquals("Автоподстройка мощности (ATPC)", KeyTitles.titleFor("radio.1.atpc.status"))
        assertEquals("ATPC: целевой уровень", KeyTitles.titleFor("radio.1.atpc.threshold"))
        assertEquals("Потери в кабеле", KeyTitles.titleFor("radio.1.cable.loss"))
    }

    @Test fun detailed_help_wins_and_unknown_key_stays() {
        assertEquals("Частота", KeyTitles.titleFor("radio.1.freq"))
        assertEquals("Порог индикатора сигнала", KeyTitles.titleFor("wireless.1.signal_led1"))
        assertEquals("foo.bar.baz", KeyTitles.titleFor("foo.bar.baz"))
    }
}
