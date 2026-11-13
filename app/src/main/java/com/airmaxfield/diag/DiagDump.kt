package com.airmaxfield.diag

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Диагностический дамп ответов пушки. Пароли, cookie и CSRF в него не попадают. */
object DiagDump {
    private val SECRET = Regex("pass|password|cookie|csrf|session|token", RegexOption.IGNORE_CASE)

    /** Ключи конфигурации/JSON, значения которых вырезаются из ответов пушки. */
    private val SECRET_KEY = """[\w.\-]*(?:passw|psk|secret|community|token|key|hash)[\w.\-]*"""

    /** system.cfg: `ключ=значение` по строкам. */
    private val CFG_LINE = Regex("""(?im)^($SECRET_KEY)=.*$""")

    /** JSON: `"ключ": "значение"` или `"ключ": число`. */
    private val JSON_PAIR = Regex("""(?i)("$SECRET_KEY"\s*:\s*)("(?:[^"\\]|\\.)*"|[^,}\]\s]+)""")

    fun redact(body: String): String =
        JSON_PAIR.replace(CFG_LINE.replace(body) { "${it.groupValues[1]}=***" }) { "${it.groupValues[1]}\"***\"" }

    fun buildZip(dump: Map<String, String>, meta: Map<String, String>, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            dump.forEach { (path, body) ->
                val name = path.trim('/').replace('/', '_').ifEmpty { "root" } + ".json"
                z.putNextEntry(ZipEntry(name)); z.write(redact(body).toByteArray()); z.closeEntry()
            }
            val text = meta.filterKeys { !SECRET.containsMatchIn(it) }
                .entries.joinToString("\n") { "${it.key}: ${it.value}" }
            z.putNextEntry(ZipEntry("meta.txt")); z.write(text.toByteArray()); z.closeEntry()
        }
    }
}
