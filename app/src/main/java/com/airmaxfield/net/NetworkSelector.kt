package com.airmaxfield.net

/** Сеть телефона в виде, удобном для логики и тестов. */
data class LocalNet(
    val id: String,
    val kind: Kind,
    val ipv4: String?,
    val prefix: Int?,
    val hasInternet: Boolean,
) {
    enum class Kind { WIFI, ETHERNET, CELLULAR, OTHER }
}

/**
 * Выбирает сеть, через которую ходить к пушке [targetIp]:
 * WIFI/ETHERNET, в чьей подсети лежит цель; иначе первая WIFI, затем ETHERNET.
 * Мобильная сеть не выбирается никогда.
 */
fun chooseNetwork(nets: List<LocalNet>, targetIp: String): LocalNet? {
    val local = nets.filter { it.kind == LocalNet.Kind.WIFI || it.kind == LocalNet.Kind.ETHERNET }
    return local.firstOrNull { n -> n.ipv4 != null && n.prefix != null && inSameSubnet(n.ipv4, n.prefix, targetIp) }
        ?: local.firstOrNull { it.kind == LocalNet.Kind.WIFI }
        ?: local.firstOrNull { it.kind == LocalNet.Kind.ETHERNET }
}

fun ipv4ToInt(ip: String): Int? {
    val p = ip.split('.')
    if (p.size != 4) return null
    var r = 0
    for (s in p) {
        val v = s.toIntOrNull() ?: return null
        if (v !in 0..255) return null
        r = (r shl 8) or v
    }
    return r
}

fun inSameSubnet(ip: String, prefix: Int, other: String): Boolean {
    val a = ipv4ToInt(ip) ?: return false
    val b = ipv4ToInt(other) ?: return false
    if (prefix !in 0..32) return false
    val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
    return (a and mask) == (b and mask)
}

/** Частные диапазоны, в которых живут пушки: 10/8, 172.16/12, 192.168/16, 169.254/16. */
fun isPrivateIp(host: String): Boolean {
    ipv4ToInt(host) ?: return false
    return inSameSubnet("10.0.0.0", 8, host) || inSameSubnet("172.16.0.0", 12, host) ||
        inSameSubnet("192.168.0.0", 16, host) || inSameSubnet("169.254.0.0", 16, host)
}
