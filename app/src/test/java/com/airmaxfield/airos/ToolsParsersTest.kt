package com.airmaxfield.airos

import com.airmaxfield.data.BackupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Calendar
import java.util.TimeZone

class ToolsParsersTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun survey_parses_and_sorts_by_signal() {
        val list = SurveyParser.parse(fixture("survey.json"))
        assertEquals(listOf("SEVER-LINK", null, "Neighbor-1"), list.map { it.ssid })
        assertEquals(5180, list[0].frequencyMhz); assertEquals(5745, list[1].frequencyMhz)
        assertEquals(-55, list[0].signalDbm); assertEquals(36, list[0].channel); assertEquals("WPA2", list[0].security)
        assertEquals("none", list[1].security)
    }

    @Test fun channel_load_groups_by_frequency() {
        val load = channelLoad(SurveyParser.parse(fixture("survey.json")))
        assertEquals(listOf(5180, 5745), load.map { it.frequencyMhz })
        assertEquals(2, load[0].count); assertEquals(-55, load[0].strongestDbm)
        assertEquals(1, load[1].count)
    }

    private val cfg = (1..12).joinToString("\n") { "radio.1.opt$it=v$it" } +
        "\nwireless.1.ssid=LINK\nnetconf.1.ip=192.168.1.20\n"

    @Test fun accepts_system_cfg() = assertTrue(ConfigBackup.looksLikeConfig(cfg))

    @Test fun rejects_non_config() {
        assertFalse(ConfigBackup.looksLikeConfig(""))
        assertFalse(ConfigBackup.looksLikeConfig("<html><form action=\"/login.cgi\"></form></html>"))
        assertFalse(ConfigBackup.looksLikeConfig("a=1\nb=2"))
    }

    @Test fun file_name_is_sanitized() {
        val at = Calendar.getInstance(TimeZone.getDefault()).apply { set(2026, 10, 13, 9, 30, 0) }.time
        assertEquals("Sever-A_192-168-1-20_20261113-0930.cfg", ConfigBackup.fileName("Sever-A", "192.168.1.20", at))
        assertEquals("Ug_B__1_192-168-1-21_20261113-0930.cfg", ConfigBackup.fileName("Ug/B (1", "192.168.1.21", at))
        assertEquals("192-168-1-21_20261113-0930.cfg", ConfigBackup.fileName(null, "192.168.1.21", at))
    }

    @Test fun backup_store_roundtrip() {
        val store = BackupStore(tmp.root)
        val a = store.save("a.cfg", "x=1"); Thread.sleep(20)
        val b = store.save("b.cfg", "y=2")
        assertEquals(listOf(b.name, a.name), store.list().map { it.name })
        assertEquals("y=2", b.readText())
        store.delete(a)
        assertEquals(listOf(b.name), store.list().map { it.name })
        assertNull(store.list().firstOrNull { it.name == "a.cfg" })
    }
}
