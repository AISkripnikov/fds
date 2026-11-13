package com.airmaxfield.airos

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConfigBackup {
    private val LINE = Regex("""^[\w.\-]+=.*$""")

    /** Похоже на system.cfg: ≥ 10 строк `ключ=значение` и есть ключи radio./wireless./netconf. */
    fun looksLikeConfig(text: String): Boolean {
        val lines = text.lineSequence().map { it.trim() }.filter { LINE.matches(it) }.toList()
        return lines.size >= 10 && lines.any {
            it.startsWith("radio.") || it.startsWith("wireless.") || it.startsWith("netconf.")
        }
    }

    fun fileName(hostname: String?, host: String, at: Date): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(at)
        val n = hostname?.trim()?.takeIf { it.isNotEmpty() }?.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return listOfNotNull(n, hostToken(host), stamp).joinToString("_") + ".cfg"
    }

    fun hostToken(host: String) = host.replace('.', '-').replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** Файл копии относится именно к этому адресу (а не к 192.168.1.2 для 192.168.1.20). */
    fun belongsTo(fileName: String, host: String): Boolean =
        Regex("(^|_)" + Regex.escape(hostToken(host)) + "_\\d{8}-\\d{4}\\.cfg$").containsMatchIn(fileName)
}
