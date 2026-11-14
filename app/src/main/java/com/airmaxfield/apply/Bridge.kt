package com.airmaxfield.apply

import com.airmaxfield.airos.SystemConfig
import kotlinx.serialization.Serializable

/** Параметры моста, задаются один раз и хранятся как шаблон (спецификация 5.9). */
@Serializable
data class BridgeProfile(
    val name: String,
    val ssid: String,
    val psk: String,
    /** Код страны airOS (числовой, как в radio.countrycode). */
    val countryCode: String,
    /** null — частота «авто»: на AP оставляем как есть, станция сканирует. */
    val freqMhz: Int? = null,
    val chanbwMhz: Int = 40,
    val txPowerDbm: Int? = null,
    val ipA: String,
    val ipB: String,
    val netmask: String = "255.255.255.0",
    val gateway: String = "",
    val mgmtVlan: Int? = null,
    val user: String,
    val pass: String,
) {
    val link: LinkParams get() = LinkParams(ssid, psk, countryCode, freqMhz, chanbwMhz)
    fun ipFor(role: BridgeRole) = if (role == BridgeRole.AP) ipA else ipB
}

/** Параметры, которые обязаны совпадать на обеих сторонах линка. */
data class LinkParams(
    val ssid: String,
    val psk: String,
    val countryCode: String,
    val freqMhz: Int?,
    val chanbwMhz: Int,
)

enum class BridgeRole(val title: String) { AP("A — точка доступа (AP PtP)"), STATION("B — станция (Station)") }

/**
 * Ключи system.cfg, которые пишет мастер. Сверены с конфигурацией живого LiteBeam 5AC (airOS 8.7.9, станция):
 * radio.1.mode, wireless.1.wds.status, wpasupplicant.profile.1.network.1.*, aaa.1.wpa.psk, radio.countrycode,
 * netconf.N(br0) — совпали. Запись на пушку ещё не проверена;
 * ошибка здесь безопасна (тест-режим откатит), но мост не поднимется. Правка — только здесь.
 */
object BridgeKeys {
    const val SSID = "wireless.1.ssid"
    const val MODE = "radio.1.mode"
    const val MODE_AP = "master"
    const val MODE_STA = "managed"
    const val WDS = "wireless.1.wds.status"
    const val FREQ = "radio.1.freq"
    const val CHANBW = "radio.1.chanbw"
    const val CENTER_FREQ = "radio.1.center.1.freq"
    const val SCAN_LIST_STATUS = "radio.1.scan_list.status"
    const val SCAN_LIST_CHANNELS = "radio.1.scan_list.channels"
    const val TXPOWER = "radio.1.txpower"
    val COUNTRY = listOf("radio.countrycode", "radio.1.countrycode")
    /** Ключ в стиле демо-конфигурации; пишем, только если он уже есть. */
    const val LEGACY_PSK = "wireless.1.psk"

    // WPA2-PSK на AP (hostapd → aaa.*)
    const val AAA_STATUS = "aaa.status"
    const val AAA_1_STATUS = "aaa.1.status"
    const val AAA_SSID = "aaa.1.ssid"
    const val AAA_PSK = "aaa.1.wpa.psk"
    const val AAA_WPA = "aaa.1.wpa"
    const val AAA_MGMT = "aaa.1.wpa.key.1.mgmt"
    const val AAA_PAIRWISE = "aaa.1.wpa.1.pairwise"

    // WPA2-PSK на станции (wpa_supplicant)
    const val SUP_STATUS = "wpasupplicant.status"
    const val SUP = "wpasupplicant.profile.1.network.1"

    const val HOSTNAME = "system.hostname"
    const val RESOLV_HOST = "resolv.host.1.name"
    const val USER_NAME = "users.1.name"
    const val USER_PASS = "users.1.password"
    const val USER_STATUS = "users.1.status"
}

object BridgePlanner {
    private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$""")

    fun isIp(s: String) = IPV4.matches(s.trim())

    private fun ipToInt(ip: String) = ip.split('.').fold(0L) { acc, p -> (acc shl 8) or p.toLong() }

    fun sameSubnet(a: String, b: String, mask: String) =
        (ipToInt(a) and ipToInt(mask)) == (ipToInt(b) and ipToInt(mask))

    /** Ошибки профиля по-русски; пусто — можно применять. */
    fun validate(p: BridgeProfile): List<String> = buildList {
        if (p.name.isBlank()) add("Укажите имя моста")
        if (p.ssid.isBlank() || p.ssid.toByteArray().size > 32) add("SSID: от 1 до 32 байт")
        if (p.psk.length !in 8..63) add("Ключ WPA2: от 8 до 63 символов")
        if (p.countryCode.isBlank() || !p.countryCode.all { it.isDigit() }) add("Код страны — число (например 643)")
        if (p.chanbwMhz !in listOf(5, 10, 20, 30, 40, 50, 60, 80)) add("Ширина канала: 5…80 МГц")
        p.freqMhz?.let { if (it !in 2400..6000) add("Частота: 2400…6000 МГц или авто") }
        if (!isIp(p.ipA)) add("IP стороны A некорректен")
        if (!isIp(p.ipB)) add("IP стороны B некорректен")
        if (!isIp(p.netmask)) add("Маска некорректна")
        if (p.gateway.isNotBlank() && !isIp(p.gateway)) add("Шлюз некорректен")
        if (isIp(p.ipA) && p.ipA == p.ipB) add("У сторон A и B должны быть разные IP")
        if (isIp(p.ipA) && isIp(p.ipB) && isIp(p.netmask) && !sameSubnet(p.ipA, p.ipB, p.netmask))
            add("IP сторон A и B должны быть в одной подсети")
        p.mgmtVlan?.let { if (it !in 1..4094) add("VLAN управления: 1…4094") }
        if (p.user.isBlank()) add("Укажите логин")
        if (p.pass.length < 8) add("Пароль: не короче 8 символов")
        if (p.user == "ubnt" && p.pass == "ubnt") add("Заводской пароль ubnt/ubnt оставлять нельзя")
    }

    /** Ключ netconf.N.* интерфейса управления: br0, иначе первый netconf. */
    fun mgmtNetconf(base: SystemConfig): String {
        val br = base.entries.firstOrNull { Regex("""^netconf\.\d+\.devname$""").matches(it.key) && it.value == "br0" }
        return br?.key?.removeSuffix(".devname") ?: base.entries.firstOrNull { it.key.startsWith("netconf.") }
            ?.key?.split('.')?.take(2)?.joinToString(".") ?: "netconf.1"
    }

    /** Роль пушки по её конфигурации: master/ap — точка доступа, иначе станция. */
    fun roleOf(cfg: SystemConfig): BridgeRole =
        if (cfg[BridgeKeys.MODE]?.lowercase()?.startsWith("ap") == true || cfg[BridgeKeys.MODE]?.lowercase() == BridgeKeys.MODE_AP)
            BridgeRole.AP else BridgeRole.STATION

    /** Текущие параметры линка из конфигурации (для подстановки в форму). */
    fun linkOf(cfg: SystemConfig): LinkParams = LinkParams(
        ssid = cfg[BridgeKeys.SSID] ?: "",
        psk = cfg[BridgeKeys.AAA_PSK] ?: cfg["${BridgeKeys.SUP}.psk"] ?: cfg[BridgeKeys.LEGACY_PSK] ?: "",
        countryCode = cfg[BridgeKeys.COUNTRY[0]] ?: cfg[BridgeKeys.COUNTRY[1]] ?: cfg["radio.1.country"] ?: "",
        freqMhz = cfg[BridgeKeys.FREQ]?.toIntOrNull(),
        chanbwMhz = cfg[BridgeKeys.CHANBW]?.toIntOrNull() ?: 20,
    )

    /** Только параметры линка (для смены канала на обеих сторонах). */
    fun relink(base: SystemConfig, role: BridgeRole, link: LinkParams): SystemConfig {
        val m = linkedMapOf<String, String>()
        m[BridgeKeys.SSID] = link.ssid
        m[BridgeKeys.CHANBW] = link.chanbwMhz.toString()
        BridgeKeys.COUNTRY.forEach { m[it] = link.countryCode }
        if (base[BridgeKeys.LEGACY_PSK] != null) m[BridgeKeys.LEGACY_PSK] = link.psk
        link.freqMhz?.let { f ->
            // airOS 8 хранит центральную частоту отдельно (на живом LiteBeam она равна freq).
            if (base[BridgeKeys.CENTER_FREQ] != null) m[BridgeKeys.CENTER_FREQ] = f.toString()
            if (role == BridgeRole.STATION) {
                if (base[BridgeKeys.FREQ] != null) m[BridgeKeys.FREQ] = f.toString()
                // Станция с ограниченным списком сканирования не найдёт AP на новой частоте.
                if (base[BridgeKeys.SCAN_LIST_STATUS] == "enabled") m[BridgeKeys.SCAN_LIST_CHANNELS] = f.toString()
            }
        }
        when (role) {
            BridgeRole.AP -> {
                link.freqMhz?.let { m[BridgeKeys.FREQ] = it.toString() }
                m[BridgeKeys.AAA_STATUS] = "enabled"
                m[BridgeKeys.AAA_1_STATUS] = "enabled"
                m[BridgeKeys.AAA_SSID] = link.ssid
                m[BridgeKeys.AAA_PSK] = link.psk
                m[BridgeKeys.AAA_WPA] = "2"
                m[BridgeKeys.AAA_MGMT] = "WPA-PSK"
                m[BridgeKeys.AAA_PAIRWISE] = "CCMP"
            }
            BridgeRole.STATION -> {
                m[BridgeKeys.SUP_STATUS] = "enabled"
                m["${BridgeKeys.SUP}.ssid"] = link.ssid
                m["${BridgeKeys.SUP}.psk"] = link.psk
                m["${BridgeKeys.SUP}.key_mgmt.1.name"] = "WPA-PSK"
                m["${BridgeKeys.SUP}.proto.1.name"] = "RSN"
                m["${BridgeKeys.SUP}.pairwise.1.name"] = "CCMP"
            }
        }
        return base.with(m)
    }

    /** Полная конфигурация стороны моста по профилю. */
    fun plan(p: BridgeProfile, role: BridgeRole, base: SystemConfig, salt: String = Md5Crypt.randomSalt()): SystemConfig {
        var cfg = relink(base, role, p.link)
        val m = linkedMapOf<String, String>()
        m[BridgeKeys.MODE] = if (role == BridgeRole.AP) BridgeKeys.MODE_AP else BridgeKeys.MODE_STA
        m[BridgeKeys.WDS] = "enabled"
        p.txPowerDbm?.let { m[BridgeKeys.TXPOWER] = it.toString() }
        val nc = mgmtNetconf(base)
        m["$nc.ip"] = p.ipFor(role)
        m["$nc.netmask"] = p.netmask
        if (p.gateway.isNotBlank()) {
            m["route.1.ip"] = "0.0.0.0"
            m["route.1.netmask"] = "0"
            m["route.1.gateway"] = p.gateway
            m["route.1.status"] = "enabled"
        }
        p.mgmtVlan?.let { vid ->
            m["vlan.status"] = "enabled"
            m["vlan.1.status"] = "enabled"
            m["vlan.1.id"] = vid.toString()
            m["vlan.1.devname"] = "br0"
        }
        val host = hostname(p.name, role)
        m[BridgeKeys.HOSTNAME] = host
        m[BridgeKeys.RESOLV_HOST] = host
        cfg = cfg.with(m)
        return FirstLogin.credentials(cfg, p.user, p.pass, salt)
    }

    fun hostname(name: String, role: BridgeRole): String {
        val base = name.trim().replace(Regex("[^A-Za-z0-9-]"), "-").trim('-').ifEmpty { "bridge" }.take(28)
        return "$base-${if (role == BridgeRole.AP) "A" else "B"}"
    }
}

/** Экран первого входа для пушки в заводском состоянии (ubnt/ubnt). */
object FirstLogin {
    const val FACTORY_USER = "ubnt"
    const val FACTORY_PASS = "ubnt"
    const val FACTORY_IP = "192.168.1.20"

    fun isFactoryLogin(user: String, pass: String) = user == FACTORY_USER && pass == FACTORY_PASS

    fun credentials(base: SystemConfig, user: String, pass: String, salt: String = Md5Crypt.randomSalt()): SystemConfig =
        base.with(linkedMapOf(
            BridgeKeys.USER_NAME to user,
            BridgeKeys.USER_PASS to Md5Crypt.hash(pass, salt),
            BridgeKeys.USER_STATUS to "enabled",
        ))

    fun plan(base: SystemConfig, user: String, pass: String, countryCode: String, salt: String = Md5Crypt.randomSalt()): SystemConfig {
        var cfg = credentials(base, user, pass, salt)
        cfg = cfg.with(BridgeKeys.COUNTRY.associateWith { countryCode })
        return cfg
    }

    fun validate(user: String, pass: String, pass2: String, countryCode: String, accepted: Boolean): List<String> = buildList {
        if (user.isBlank() || user.any { it.isWhitespace() }) add("Логин без пробелов")
        if (pass.length < 8) add("Пароль: не короче 8 символов")
        if (isFactoryLogin(user, pass)) add("Заводской пароль оставлять нельзя")
        if (pass != pass2) add("Пароли не совпадают")
        if (countryCode.isBlank() || !countryCode.all { it.isDigit() }) add("Выберите страну")
        if (!accepted) add("Нужно принять условия использования airOS")
    }

    /** Коды стран airOS (ISO 3166 числовые) для выбора; остальное — вручную. */
    val COUNTRIES = listOf(
        "643" to "Россия", "112" to "Беларусь", "398" to "Казахстан", "860" to "Узбекистан",
        "417" to "Киргизия", "51" to "Армения", "31" to "Азербайджан", "762" to "Таджикистан",
    )
}
