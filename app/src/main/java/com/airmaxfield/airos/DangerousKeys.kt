package com.airmaxfield.airos

/**
 * Поля, при смене которых может пропасть связь с пушкой или линк моста
 * (спецификация 5.4, 5.7, 5.10): IP-адресация, VLAN, режим, частота, ширина канала,
 * страна, SSID и безопасность.
 *
 * Без тест-режима такие поля только для чтения. Имена ключей — по system.cfg airOS 6/8;
 * сверить с дампами живых пушек.
 */
object DangerousKeys {
    enum class Kind(val title: String) {
        ADDRESS("IP-адресация"),
        VLAN("VLAN"),
        MODE("режим работы"),
        FREQUENCY("частота"),
        WIDTH("ширина канала"),
        COUNTRY("страна"),
        LINK("параметры линка (SSID, безопасность)"),
    }

    private val rules: List<Pair<Regex, Kind>> = listOf(
        Regex("""^netconf\.\d+\.(ip|netmask|devname|role|autoip\..*)$""") to Kind.ADDRESS,
        Regex("""^route\.\d+\.(gateway|ip|netmask|devname)$""") to Kind.ADDRESS,
        Regex("""^(dhcpc|dhcp6c)\..*""") to Kind.ADDRESS,
        Regex("""^netmode$""") to Kind.MODE,
        Regex("""^bridge\..*""") to Kind.ADDRESS,
        Regex("""^vlan\..*""") to Kind.VLAN,
        Regex("""^radio\.\d+\.(mode|ieee_mode|ptpmode)$""") to Kind.MODE,
        Regex("""^wireless\.\d+\.(mode|ap|wds\..*|autowds)$""") to Kind.MODE,
        Regex("""^radio\.\d+\.(freq|scan_list\..*|dfs\..*)$""") to Kind.FREQUENCY,
        Regex("""^radio\.\d+\.(chanbw|clksel|chanshift|cwm\..*)$""") to Kind.WIDTH,
        Regex("""^radio\.(\d+\.)?countrycode$|^radio\.\d+\.country$|^system\.country$""") to Kind.COUNTRY,
        Regex("""^wireless\.\d+\.(ssid|security.*|psk|hide_ssid|mac_acl\..*)$""") to Kind.LINK,
        Regex("""^(aaa|wpasupplicant)\..*""") to Kind.LINK,
    )

    fun kind(key: String): Kind? = rules.firstOrNull { it.first.matches(key) }?.second

    fun isDangerous(key: String): Boolean = kind(key) != null
}

/** Ключи, которые в приложении не правятся вручную (хеши паролей пользователей). */
fun isEditableKey(key: String): Boolean = !key.startsWith("users.")
