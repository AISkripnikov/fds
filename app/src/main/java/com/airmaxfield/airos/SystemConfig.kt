package com.airmaxfield.airos

/**
 * Запись конфигурации: ключ и значение.
 */
data class CfgEntry(val key: String, val value: String)

/**
 * Модель конфигурации system.cfg с группировкой по секциям.
 * Все настройки только для чтения на этапе 3.
 */
class SystemConfig(val entries: List<CfgEntry>) {
    companion object {
        /**
         * Парсит текст конфигурации в модель.
         * Формат: key=value построчно, пустые строки и комментарии (#) пропускаются.
         */
        fun parse(raw: String): SystemConfig {
            val entries = raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        CfgEntry(parts[0].trim(), parts[1].trim())
                    } else null
                }
            return SystemConfig(entries)
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

    /**
     * Round-trip: конфигурация обратно в текст key=value.
     */
    val raw: String
        get() = entries.joinToString("\n") { "${it.key}=${it.value}" }

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
