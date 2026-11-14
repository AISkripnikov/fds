package com.airmaxfield.apply

import com.airmaxfield.airos.CfgChange
import com.airmaxfield.airos.DangerousKeys
import com.airmaxfield.airos.SystemConfig

/** Адрес управления пушкой в конфигурации. */
object Management {
    private val IP_KEY = Regex("""^netconf\.\d+\.ip$""")

    /** Ключ netconf.N.ip, по которому мы сейчас подключены к [host]. */
    fun ipKeyFor(cfg: SystemConfig, host: String): String? =
        cfg.entries.firstOrNull { IP_KEY.matches(it.key) && it.value == host }?.key

    /** Новый адрес управления, если изменение его меняет; иначе null. */
    fun newHost(old: SystemConfig, new: SystemConfig, host: String): String? {
        val key = ipKeyFor(old, host) ?: return null
        return new[key]?.takeIf { it != host && it.isNotBlank() }
    }

    /** Предупреждения к сводке изменений: опасные поля сгруппированы по видам. */
    fun warnings(changes: List<CfgChange>): List<String> =
        changes.mapNotNull { DangerousKeys.kind(it.key) }.distinct().map { kind ->
            when (kind) {
                DangerousKeys.Kind.ADDRESS -> "Меняется IP-адресация — пушка может пропасть по старому адресу"
                DangerousKeys.Kind.VLAN -> "Меняется VLAN — можно потерять доступ к управлению"
                DangerousKeys.Kind.MODE -> "Меняется режим работы — линк моста пропадёт"
                DangerousKeys.Kind.FREQUENCY -> "Меняется частота — линк пропадёт, пока вторая сторона не перейдёт на неё же"
                DangerousKeys.Kind.WIDTH -> "Меняется ширина канала — должна совпадать на обеих сторонах"
                DangerousKeys.Kind.COUNTRY -> "Меняется страна — может сменить допустимые частоты и мощность"
                DangerousKeys.Kind.LINK -> "Меняются SSID или ключ — вторая сторона перестанет подключаться"
            }
        }
}
