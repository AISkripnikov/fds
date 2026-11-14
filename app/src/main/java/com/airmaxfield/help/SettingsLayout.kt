package com.airmaxfield.help

import com.airmaxfield.airos.CfgEntry
import com.airmaxfield.airos.SystemConfig

/**
 * Раскладка настроек по вкладкам (как в airOS), служебные ключи и понятные названия с учётом интерфейса.
 * Технические имена ключей пользователю не показываются.
 */
object SettingsLayout {
    enum class Tab(val title: String) { WIRELESS("Беспроводная"), NETWORK("Сеть"), ADVANCED("Дополнительно"), SERVICES("Сервисы"), SYSTEM("Система") }

    private val groups: List<Pair<Regex, Tab>> = listOf(
        Regex("""^radio\.\d+\.(ack.*|distance|atpc.*|polling.*|cwm\..*|ieee_mode|rx_sensitivity)$|^wireless\.\d+\.(rate\..*|ampdu\..*|amsdu|mcast\..*|frameaggregation|wmm)$|^airmax\..*|^airview\..*""") to Tab.ADVANCED,
        Regex("""^radio\..*|^wireless\..*|^aaa\..*|^wpasupplicant\..*|^mgmt.*""") to Tab.WIRELESS,
        Regex("""^(netmode|netconf|bridge|vlan|route|resolv\.nameserver|dhcpc|dhcpd|ebtables|iptables|igmpproxy|pppoe.*|tshaper|dnsmasq|upnpd)(\..*)?$""") to Tab.NETWORK,
        Regex("""^(snmp|sshd|httpd|telnetd|ntpclient|syslog|discovery|pwdog|unms|update|dyndns)(\..*)?$|^system\.airosx\..*""") to Tab.SERVICES,
        Regex("""^(users|system|gui|resolv\.host|led)(\..*)?$""") to Tab.SYSTEM,
    )

    fun tabOf(key: String): Tab = groups.firstOrNull { it.first.matches(key) }?.second ?: Tab.SYSTEM

    /**
     * Служебное: имена интерфейсов, внутренние фильтры, главные выключатели радио/моста/сети,
     * технические учётные записи. Менять не нужно, а выключатели — опасно.
     */
    private val HIDDEN = Regex(
        """.*\.devname$|^ebtables\..*|^iptables\.sys\..*|^system\.cfg\..*|^users\.([2-9]|\d\d)\..*|^users\.\d+\.(uid|gid|shell|homedir|status)$""" +
            """|^wpasupplicant\.device\..*|^wpasupplicant\.profile\.\d+\.name$|^wpasupplicant\.profile\.\d+\.network\.([2-9]|\d\d)\..*""" +
            """|.*\.eap\..*|^aaa\.\d+\.radius\..*|^radio\.\d+\.(cmsbias|rc_mode|ff_cap_rep|subsystemid)$|^netconf\.\d+\.(up|role)$""" +
            """|^(netconf|bridge|radio|wireless|users|route|resolv\.nameserver)\.status$|^(radio|wireless|bridge)\.\d+\.status$""" +
            """|^resolv\.host\.\d+\.status$|^gui\.network\..*|^unms\.uri$|^system\.date\.status$|^users\.status$"""
    )

    fun isHidden(key: String): Boolean = HIDDEN.matches(key)

    /** Есть ли у поля понятное название (не сырой ключ). */
    fun hasTitle(key: String): Boolean = HelpContent.forKey(key) != null || KeyTitles.forKey(key) != null

    data class Section(val shown: List<CfgEntry>, val hiddenTechnical: List<CfgEntry>)

    /** Поля вкладки: с названиями — на виду; без описания — отдельно, свёрнуто. Служебные не показываются. */
    fun section(cfg: SystemConfig, tab: Tab): Section {
        val inTab = cfg.entries.filter { tabOf(it.key) == tab && !isHidden(it.key) }
        return Section(inTab.filter { hasTitle(it.key) }, inTab.filterNot { hasTitle(it.key) })
    }

    private val IFACE = Regex("""^(netconf|bridge\.\d+\.port)\.(\d+)\..*""")

    /** Понятное имя интерфейса по devname. */
    fun ifaceName(devname: String?): String? = when {
        devname == null -> null
        devname == "br0" -> "мост — адрес управления"
        devname.startsWith("br") -> "мост $devname"
        devname == "eth0" -> "кабель LAN"
        devname.startsWith("eth") -> "кабель ${devname.removePrefix("eth").toIntOrNull()?.plus(1) ?: devname}"
        devname == "ath0" -> "радио"
        devname == "ath1" -> "служебный Wi‑Fi"
        devname.startsWith("ath") || devname.startsWith("wlan") -> "радио $devname"
        devname.contains('.') -> "VLAN ${devname.substringAfter('.')}"
        else -> devname
    }

    /** Название поля для экрана: справка → словарь → (на крайний случай) ключ; плюс к какому интерфейсу относится. */
    fun title(key: String, cfg: SystemConfig?): String {
        val base = KeyTitles.titleFor(key)
        val m = IFACE.matchEntire(key) ?: return base
        val dev = cfg?.get("${m.groupValues[1]}.${m.groupValues[2]}.devname")
        return ifaceName(dev)?.let { "$base · $it" } ?: base
    }
}
