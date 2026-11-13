package com.airmaxfield.diag

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Диагностический дамп ответов пушки. Пароли, cookie и CSRF в него не попадают. */
object DiagDump {
    private val SECRET = Regex("pass|password|cookie|csrf|session|token", RegexOption.IGNORE_CASE)

    fun buildZip(dump: Map<String, String>, meta: Map<String, String>, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            dump.forEach { (path, body) ->
                val name = path.trim('/').replace('/', '_').ifEmpty { "root" } + ".json"
                z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry()
            }
            val text = meta.filterKeys { !SECRET.containsMatchIn(it) }
                .entries.joinToString("\n") { "${it.key}: ${it.value}" }
            z.putNextEntry(ZipEntry("meta.txt")); z.write(text.toByteArray()); z.closeEntry()
        }
    }
}
