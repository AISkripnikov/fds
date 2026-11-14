package com.airmaxfield.help

import com.airmaxfield.airos.SystemConfig
import com.airmaxfield.airos.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    private val cfg = SystemConfig.parse(fixture("airos8/system_lb5ac_879_sta.cfg"))

    @Test fun every_visible_key_of_real_unit_has_title_and_description() {
        val missing = cfg.entries.map { it.key }.filterNot { SettingsLayout.isHidden(it) }
            .filter { HelpContent.forKey(it)?.description.isNullOrBlank() }
        assertTrue("без описания: $missing", missing.isEmpty())
    }

    @Test fun nothing_left_in_technical_bucket_for_real_unit() {
        SettingsLayout.Tab.entries.forEach { t ->
            assertTrue(t.name, SettingsLayout.section(cfg, t).hiddenTechnical.isEmpty())
        }
    }

    @Test fun groups_like_airos() {
        assertEquals(SettingsLayout.Tab.WIRELESS, SettingsLayout.tabOf("wpasupplicant.profile.1.network.1.psk"))
        assertEquals(SettingsLayout.Tab.WIRELESS, SettingsLayout.tabOf("radio.1.txpower"))
        assertEquals(SettingsLayout.Tab.ADVANCED, SettingsLayout.tabOf("radio.1.polling"))
        assertEquals(SettingsLayout.Tab.ADVANCED, SettingsLayout.tabOf("radio.1.ack.auto"))
        assertEquals(SettingsLayout.Tab.NETWORK, SettingsLayout.tabOf("netmode"))
        assertEquals(SettingsLayout.Tab.SERVICES, SettingsLayout.tabOf("pwdog.status"))
        assertEquals(SettingsLayout.Tab.SYSTEM, SettingsLayout.tabOf("system.external.reset"))
    }

    @Test fun technical_switches_are_hidden() {
        listOf("radio.1.devname", "radio.status", "wireless.1.status", "bridge.status", "ebtables.sys.eap.1.status",
            "users.2.uid", "wpasupplicant.device.1.profile", "system.cfg.version", "netconf.3.role")
            .forEach { assertTrue(it, SettingsLayout.isHidden(it)) }
        listOf("radio.1.freq", "netconf.3.ip", "wpasupplicant.profile.1.network.1.psk", "pwdog.status")
            .forEach { assertFalse(it, SettingsLayout.isHidden(it)) }
    }

    @Test fun titles_say_which_interface() {
        assertEquals("MTU · кабель LAN", SettingsLayout.title("netconf.2.mtu", cfg))
        assertEquals("IP-адрес · мост — адрес управления", SettingsLayout.title("netconf.3.ip", cfg))
        assertEquals("Порт в мосту · радио", SettingsLayout.title("bridge.1.port.2.status", cfg))
        assertEquals("Сторожевой ping", SettingsLayout.title("pwdog.status", cfg))
    }
}
