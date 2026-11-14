package com.airmaxfield.airos

/**
 * Запись конфигурации: ключ и значение.
 */
data class CfgEntry(val key: String, val value: String)

/** Изменение одного ключа: old == null — ключ добавлен, new == null — удалён. */
data class CfgChange(val key: String, val old: String?, val new: String?)

/**
 * Модель system.cfg без потерь: хранит исходные строки (комментарии, пустые строки,
 * неизвестные ключи, порядок). [raw] отдаёт неизменённые строки байт-в-байт,
 * переписываются только строки, затронутые [with] / [without].
 */
class SystemConfig private constructor(private val lines: List<Line>) {
    private sealed interface Line {
        val text: String
    }
    private data class Entry(val key: String, val value: String, override val text: String) : Line
    private data class Other(override val text: String) : Line

    companion object {
        /**
         * Парсит текст конфигурации. Строка `key=value` — запись; всё остальное
         * (комментарии #, пустые строки, мусор) сохраняется как есть.
         */
        fun parse(raw: String): SystemConfig = SystemConfig(raw.split("\n").map { parseLine(it) })

        private fun parseLine(text: String): Line {
            val t = text.trim()
            if (t.isEmpty() || t.startsWith("#")) return Other(text)
            val parts = t.split("=", limit = 2)
            if (parts.size != 2 || parts[0].isBlank()) return Other(text)
            return Entry(parts[0].trim(), parts[1].trim(), text)
        }

        /**
         * Проверяет, является ли ключ чувствительным (пароль, ключ).
         */
        fun isSensitive(key: String): Boolean {
            val lower = key.lowercase()
            return lower.contains("password") ||
                   lower.contains("psk") ||
                   lower.contains("secret")
        }
    }

    /** Все записи `ключ=значение` в исходном порядке. */
    val entries: List<CfgEntry> = lines.filterIsInstance<Entry>().map { CfgEntry(it.key, it.value) }

    private val entryMap = entries.associateBy { it.key }

    /**
     * Получить значение по ключу.
     */
    operator fun get(key: String): String? = entryMap[key]?.value

    /**
     * Получить все записи, ключи которых начинаются с prefix.
     */
    fun section(prefix: String): List<CfgEntry> =
        entries.filter { it.key.startsWith(prefix) }

    /** Текст конфигурации: неизменённые строки — байт-в-байт как пришли с пушки. */
    val raw: String
        get() = lines.joinToString("\n") { it.text }

    /** Новая конфигурация с заменённым на месте значением; неизвестный ключ дописывается в конец. */
    fun with(key: String, value: String): SystemConfig {
        require(key.isNotBlank() && !key.contains('=') && !key.contains('\n')) { "плохой ключ: $key" }
        require(!value.contains('\n')) { "значение с переводом строки: $key" }
        if (key in entryMap) {
            if (entryMap[key]!!.value == value) return this
            return SystemConfig(lines.map { if (it is Entry && it.key == key) Entry(key, value, "$key=$value") else it })
        }
        // Дописываем перед хвостовыми пустыми строками, чтобы финальный перевод строки остался последним.
        val tail = lines.takeLastWhile { it is Other && it.text.isEmpty() }.size
        val out = lines.toMutableList()
        out.add(lines.size - tail, Entry(key, value, "$key=$value"))
        return SystemConfig(out)
    }

    fun with(changes: Map<String, String>): SystemConfig =
        changes.entries.fold(this) { cfg, (k, v) -> cfg.with(k, v) }

    /** Новая конфигурация без ключа. */
    fun without(key: String): SystemConfig =
        if (key !in entryMap) this else SystemConfig(lines.filterNot { it is Entry && it.key == key })

    /** Что изменится при переходе от этой конфигурации к [other]: изменённые, добавленные, удалённые ключи. */
    fun diff(other: SystemConfig): List<CfgChange> {
        val out = mutableListOf<CfgChange>()
        for (e in entries) {
            val n = other[e.key]
            if (n != e.value) out += CfgChange(e.key, e.value, n)
        }
        for (e in other.entries) if (e.key !in entryMap) out += CfgChange(e.key, null, e.value)
        return out.distinctBy { it.key }
    }

    /**
     * Группировка: Дополнительные настройки.
     * Специфичные для радио: ieee_mode, ack, distance, txpower, airmax.
     */
    val advanced: List<CfgEntry>
        get() = entries.filter {
            it.key.contains(".ieee_mode") ||
            it.key.contains(".ack") ||
            it.key.contains(".distance") ||
            it.key.contains(".txpower") ||
            it.key.startsWith("airmax.")
        }

    /**
     * Группировка: Беспроводная сеть (исключая advanced).
     */
    val wireless: List<CfgEntry>
        get() {
            val advancedKeys = advanced.map { it.key }.toSet()
            return entries.filter {
                (it.key.startsWith("radio.") || it.key.startsWith("wireless.")) &&
                it.key !in advancedKeys
            }
        }

    /**
     * Группировка: Сеть.
     */
    val network: List<CfgEntry>
        get() = entries.filter {
            it.key.startsWith("netconf.") ||
            it.key.startsWith("bridge.") ||
            it.key.startsWith("vlan.") ||
            it.key.startsWith("route.")
        }

    /**
     * Группировка: Сервисы.
     */
    val services: List<CfgEntry>
        get() = entries.filter {
            it.key.startsWith("snmp.") ||
            it.key.startsWith("sshd.") ||
            it.key.startsWith("httpd.") ||
            it.key.startsWith("ntpclient.") ||
            it.key.startsWith("syslog.") ||
            it.key.startsWith("discovery.")
        }

    /**
     * Группировка: Система.
     */
    val system: List<CfgEntry>
        get() = entries.filter {
            it.key.startsWith("users.") ||
            it.key.startsWith("resolv.") ||
            it.key.startsWith("system.") ||
            it.key.startsWith("update.")
        }

    /**
     * Группировка: Прочие (всё, что не попало в другие секции).
     */
    val other: List<CfgEntry>
        get() {
            val known = wireless + network + advanced + services + system
            val knownKeys = known.map { it.key }.toSet()
            return entries.filter { it.key !in knownKeys }
        }
}
