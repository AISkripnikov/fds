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
}
