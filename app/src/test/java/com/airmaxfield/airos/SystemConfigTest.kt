package com.airmaxfield.airos

import org.junit.Assert.*
import org.junit.Test

class SystemConfigTest {

    @Test
    fun parses_basic_config() {
        val raw = """
            radio.1.freq=5180
            wireless.1.ssid=test-network
            netconf.1.ip=192.168.1.1
            users.1.name=ubnt
            httpd.port=443
        """.trimIndent()

        val cfg = SystemConfig.parse(raw)

        assertEquals(5, cfg.entries.size)
        assertEquals("5180", cfg["radio.1.freq"])
        assertEquals("test-network", cfg["wireless.1.ssid"])
        assertEquals("192.168.1.1", cfg["netconf.1.ip"])
        assertEquals("ubnt", cfg["users.1.name"])
        assertEquals("443", cfg["httpd.port"])
    }

    @Test
    fun skips_empty_and_comments() {
        val raw = """
            # This is a comment
            radio.1.freq=5180

            # Another comment
            wireless.1.ssid=test

        """.trimIndent()

        val cfg = SystemConfig.parse(raw)

        assertEquals(2, cfg.entries.size)
        assertEquals("5180", cfg["radio.1.freq"])
        assertEquals("test", cfg["wireless.1.ssid"])
    }

    @Test
    fun round_trip() {
        val raw = """
            radio.1.freq=5180
            wireless.1.ssid=test-network
            netconf.1.ip=192.168.1.1
        """.trimIndent()

        val cfg1 = SystemConfig.parse(raw)
        val raw2 = cfg1.raw
        val cfg2 = SystemConfig.parse(raw2)

        assertEquals(cfg1.entries.size, cfg2.entries.size)
        cfg1.entries.forEachIndexed { i, entry ->
            assertEquals(entry.key, cfg2.entries[i].key)
            assertEquals(entry.value, cfg2.entries[i].value)
        }
    }

    @Test
    fun sections_group_correctly() {
        val raw = """
            radio.1.freq=5180
            wireless.1.ssid=test
            netconf.1.ip=192.168.1.1
            bridge.1.port.1.enabled=true
            vlan.1.id=10
            route.1.dest=0.0.0.0
            radio.1.ieee_mode=11a
            radio.1.ack.auto=enabled
            radio.1.distance=5000
            radio.1.txpower=20
            airmax.enabled=true
            snmp.enabled=false
            sshd.port=22
            httpd.port=443
            ntpclient.enabled=true
            syslog.remote.ip=192.168.1.2
            discovery.enabled=true
            users.1.name=ubnt
            resolv.nameserver.1.ip=8.8.8.8
            system.hostname=bridge1
            update.url=http://update.ubnt.com
        """.trimIndent()

        val cfg = SystemConfig.parse(raw)

        // Wireless: radio.*, wireless.*
        assertEquals(2, cfg.wireless.size)
        assertTrue(cfg.wireless.any { it.key == "radio.1.freq" })
        assertTrue(cfg.wireless.any { it.key == "wireless.1.ssid" })

        // Network: netconf.*, bridge.*, vlan.*, route.*
        assertEquals(4, cfg.network.size)
        assertTrue(cfg.network.any { it.key == "netconf.1.ip" })
        assertTrue(cfg.network.any { it.key == "bridge.1.port.1.enabled" })

        // Advanced: ieee_mode, ack, distance, txpower, airmax.*
        assertEquals(5, cfg.advanced.size)
        assertTrue(cfg.advanced.any { it.key == "radio.1.ieee_mode" })
        assertTrue(cfg.advanced.any { it.key == "airmax.enabled" })

        // Services: snmp.*, sshd.*, httpd.*, ntpclient.*, syslog.*, discovery.*
        assertEquals(6, cfg.services.size)
        assertTrue(cfg.services.any { it.key == "sshd.port" })

        // System: users.*, resolv.*, system.*, update.*
        assertEquals(4, cfg.system.size)
        assertTrue(cfg.system.any { it.key == "users.1.name" })
        assertTrue(cfg.system.any { it.key == "system.hostname" })

        // Other: должно быть пусто
        assertEquals(0, cfg.other.size)
    }

    @Test
    fun sensitive_keys_detected() {
        assertTrue(SystemConfig.isSensitive("wireless.1.psk"))
        assertTrue(SystemConfig.isSensitive("users.1.password"))
        assertTrue(SystemConfig.isSensitive("wpa.secret"))
        assertTrue(SystemConfig.isSensitive("PSK"))
        assertTrue(SystemConfig.isSensitive("PASSWORD"))
        assertFalse(SystemConfig.isSensitive("wireless.1.ssid"))
        assertFalse(SystemConfig.isSensitive("radio.1.freq"))
    }

    @Test
    fun unknown_keys_go_to_other() {
        val raw = """
            radio.1.freq=5180
            foo.bar=1
            baz.qux.value=test
        """.trimIndent()

        val cfg = SystemConfig.parse(raw)

        assertEquals(1, cfg.wireless.size)
        assertEquals(2, cfg.other.size)
        assertTrue(cfg.other.any { it.key == "foo.bar" })
        assertTrue(cfg.other.any { it.key == "baz.qux.value" })
    }

    @Test
    fun section_prefix_works() {
        val raw = """
            radio.1.freq=5180
            radio.1.txpower=20
            radio.2.freq=2437
            wireless.1.ssid=test
        """.trimIndent()

        val cfg = SystemConfig.parse(raw)

        val radio1 = cfg.section("radio.1")
        assertEquals(2, radio1.size)
        assertTrue(radio1.any { it.key == "radio.1.freq" })
        assertTrue(radio1.any { it.key == "radio.1.txpower" })

        val radio2 = cfg.section("radio.2")
        assertEquals(1, radio2.size)
        assertEquals("2437", radio2[0].value)
    }
}
