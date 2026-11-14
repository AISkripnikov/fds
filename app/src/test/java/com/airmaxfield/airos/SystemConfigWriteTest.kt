package com.airmaxfield.airos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemConfigWriteTest {
    private val text = "# заголовок\nradio.1.freq=5180\n\n  wireless.1.ssid = LINK \nmystery line\nfoo.unknown=1\n"

    @Test fun raw_is_lossless() {
        assertEquals(text, SystemConfig.parse(text).raw)
    }

    @Test fun with_replaces_in_place_and_keeps_other_lines_byte_for_byte() {
        val out = SystemConfig.parse(text).with("radio.1.freq", "5200").raw
        assertEquals("# заголовок\nradio.1.freq=5200\n\n  wireless.1.ssid = LINK \nmystery line\nfoo.unknown=1\n", out)
    }

    @Test fun with_appends_new_key_before_final_newline() {
        val out = SystemConfig.parse(text).with("radio.1.chanbw", "40").raw
        assertTrue(out.endsWith("foo.unknown=1\nradio.1.chanbw=40\n"))
    }

    @Test fun with_same_value_is_noop() {
        val c = SystemConfig.parse(text)
        assertSame(c, c.with("radio.1.freq", "5180"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun value_with_newline_is_rejected() {
        SystemConfig.parse(text).with("wireless.1.ssid", "a\nnetconf.1.ip=1.2.3.4")
    }

    @Test fun diff_reports_changed_added_removed() {
        val a = SystemConfig.parse(text)
        val b = a.with("radio.1.freq", "5200").with("radio.1.chanbw", "40").without("foo.unknown")
        val d = b.let { a.diff(it) }.associateBy { it.key }
        assertEquals(3, d.size)
        assertEquals(CfgChange("radio.1.freq", "5180", "5200"), d["radio.1.freq"])
        assertEquals(CfgChange("radio.1.chanbw", null, "40"), d["radio.1.chanbw"])
        assertEquals(CfgChange("foo.unknown", "1", null), d["foo.unknown"])
        assertTrue(a.diff(a).isEmpty())
    }

    @Test fun dangerous_keys() {
        for (k in listOf("netconf.3.ip", "netconf.1.netmask", "route.1.gateway", "vlan.1.id", "radio.1.mode",
            "radio.1.freq", "radio.1.chanbw", "radio.countrycode", "radio.1.countrycode", "wireless.1.ssid",
            "wireless.1.security", "aaa.1.wpa.psk", "wpasupplicant.profile.1.network.1.psk")) {
            assertTrue(k, DangerousKeys.isDangerous(k))
        }
        for (k in listOf("snmp.community", "system.hostname", "radio.1.txpower", "sshd.port", "netconf.1.mtu")) {
            assertFalse(k, DangerousKeys.isDangerous(k))
        }
        assertEquals(DangerousKeys.Kind.FREQUENCY, DangerousKeys.kind("radio.1.freq"))
        assertNull(DangerousKeys.kind("snmp.location"))
    }

    @Test fun user_password_hash_is_not_editable() {
        assertFalse(isEditableKey("users.1.password"))
        assertTrue(isEditableKey("wireless.1.psk"))
    }
}
