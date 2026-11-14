package com.airmaxfield.airos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSpecTest {
    @Test fun types_by_key_and_value() {
        assertEquals(FieldType.Toggle("enabled", "disabled"), FieldSpec.typeFor("radio.1.ack.auto", "enabled"))
        assertEquals(FieldType.Toggle("true", "false"), FieldSpec.typeFor("snmp.enabled", "true"))
        assertTrue(FieldSpec.typeFor("radio.1.chanbw", "40") is FieldType.Choice)
        assertTrue(FieldSpec.typeFor("radio.1.mode", "master") is FieldType.Choice)
        assertEquals(FieldType.Ip, FieldSpec.typeFor("netconf.3.ip", "192.168.1.20"))
        assertEquals(FieldType.Netmask, FieldSpec.typeFor("netconf.3.netmask", "255.255.255.0"))
        assertTrue(FieldSpec.typeFor("sshd.port", "22") is FieldType.Number)
    }

    @Test fun unexpected_current_value_falls_back_to_text() {
        assertEquals(FieldType.Text(), FieldSpec.typeFor("radio.1.mode", "ap-ptp")) // не из списка
        assertEquals(FieldType.Text(), FieldSpec.typeFor("route.1.netmask", "0"))    // не маска вида a.b.c.d
        assertEquals(FieldType.Text(), FieldSpec.typeFor("some.port", "auto"))
    }

    @Test fun validation() {
        val port = FieldSpec.typeFor("sshd.port", "22")
        assertNull(FieldSpec.validate(port, "2222"))
        assertNotNull(FieldSpec.validate(port, "70000"))
        assertNotNull(FieldSpec.validate(port, "x"))
        assertNull(FieldSpec.validate(FieldType.Ip, "10.0.0.1"))
        assertNotNull(FieldSpec.validate(FieldType.Ip, "10.0.0.256"))
        assertNull(FieldSpec.validate(FieldType.Netmask, "255.255.252.0"))
        assertNotNull(FieldSpec.validate(FieldType.Netmask, "255.0.255.0"))
        val psk = FieldSpec.typeFor("wireless.1.psk", "secret123")
        assertNotNull(FieldSpec.validate(psk, "short"))
        assertNull(FieldSpec.validate(psk, "long-enough"))
        assertNotNull(FieldSpec.validate(FieldSpec.typeFor("radio.1.chanbw", "40"), "45"))
    }

    @Test fun labels() {
        assertEquals("Вкл", FieldSpec.label("snmp.enabled", "true"))
        assertEquals("Выкл", FieldSpec.label("radio.1.ack.auto", "disabled"))
        assertEquals("40 МГц", FieldSpec.label("radio.1.chanbw", "40"))
        assertEquals("Точка доступа (AP)", FieldSpec.label("radio.1.mode", "master"))
        assertEquals("20 dBm", FieldSpec.label("radio.1.txpower", "20"))
        assertEquals("SEVER", FieldSpec.label("wireless.1.ssid", "SEVER"))
    }
}
