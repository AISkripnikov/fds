package com.airmaxfield.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class DiagDumpTest {
    @Test fun zip_contains_each_path_and_meta_no_secrets() {
        val out = ByteArrayOutputStream()
        DiagDump.buildZip(
            dump = mapOf("/status.cgi" to "{\"a\":1}", "/sta.cgi" to "[]"),
            meta = mapOf("model" to "NanoStation M5", "pass" to "secret", "Password" to "secret",
                "cookie" to "secret", "X-CSRF-ID" to "secret"),
            out = out,
        )
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) { val e = z.nextEntry ?: break; files[e.name] = z.readBytes().decodeToString() }
        }
        assertEquals(setOf("status.cgi.json", "sta.cgi.json", "meta.txt"), files.keys)
        assertEquals("{\"a\":1}", files["status.cgi.json"])
        assertTrue(files["meta.txt"]!!.contains("model: NanoStation M5"))
        files.values.forEach { assertFalse(it.contains("secret")) }
    }

    @Test fun response_bodies_are_redacted() {
        val cfg = "users.1.name=ubnt\nusers.1.password=\$1\$hash\nwpasupplicant.profile.1.network.1.psk=MyWifiKey\n" +
            "snmp.community=public\nradio.1.freq=5180\n"
        val json = """{"essid":"LINK","wpa_psk":"MyWifiKey","nested":{"password":"pw1"},"signal":-58}"""
        val out = ByteArrayOutputStream()
        DiagDump.buildZip(mapOf("/getcfg.sh" to cfg, "/status.cgi" to json), emptyMap(), out)
        val all = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            buildString { while (z.nextEntry != null) append(z.readBytes().decodeToString()).append('\n') }
        }
        listOf("\$1\$hash", "MyWifiKey", "public", "pw1").forEach { assertFalse("leaked $it", all.contains(it)) }
        assertTrue(all.contains("radio.1.freq=5180")); assertTrue(all.contains("users.1.name=ubnt"))
        assertTrue(all.contains("\"signal\":-58")); assertTrue(all.contains("\"essid\":\"LINK\""))
    }
}
