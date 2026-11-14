package com.airmaxfield.apply

import com.airmaxfield.airos.SystemConfig

/**
 * Восстановление резервной копии: копия → конфигурация для записи (через тест-режим).
 *
 * Учётные записи (`users.*`) берутся из текущей конфигурации пушки: старая копия могла быть
 * снята с другим паролем, и после восстановления в пушку было бы не войти.
 */
object RestorePlanner {
    data class Plan(val config: SystemConfig, val warnings: List<String>)

    private fun isUserKey(k: String) = k.startsWith("users.")

    fun plan(current: SystemConfig, backup: SystemConfig, host: String = ""): Plan {
        var cfg = backup
        for (e in backup.entries) if (isUserKey(e.key) && current[e.key] == null) cfg = cfg.without(e.key)
        cfg = cfg.with(current.entries.filter { isUserKey(it.key) }.associate { it.key to it.value })

        val w = mutableListOf<String>()
        val hn = current["system.hostname"]
        val bhn = backup["system.hostname"]
        if (hn != null && bhn != null && hn != bhn) w += "Копия, похоже, от другой пушки: имя «$bhn», а у этой «$hn»"
        if (current[BridgeKeys.MODE] != null && backup[BridgeKeys.MODE] != null && current[BridgeKeys.MODE] != backup[BridgeKeys.MODE])
            w += "Режим работы сменится: ${current[BridgeKeys.MODE]} → ${backup[BridgeKeys.MODE]}"
        Management.newHost(current, cfg, host)?.let { w += "Адрес управления сменится на $it" }
        if (backup.entries.any { isUserKey(it.key) && current[it.key] != it.value })
            w += "Логин и пароль в копии другие — останутся текущие, из копии они не берутся"
        return Plan(cfg, w)
    }
}
