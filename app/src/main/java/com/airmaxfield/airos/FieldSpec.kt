package com.airmaxfield.airos

/** Тип поля настройки: как его показать и какие значения airOS примет. */
sealed interface FieldType {
    /** Переключатель: значения airOS для «вкл» и «выкл». */
    data class Toggle(val on: String, val off: String) : FieldType
    /** Выбор из списка: значение → подпись. */
    data class Choice(val options: List<Pair<String, String>>) : FieldType
    data class Number(val min: Long, val max: Long, val unit: String = "") : FieldType
    data object Ip : FieldType
    data object Netmask : FieldType
    data class Text(val minLen: Int = 0, val maxBytes: Int = 256) : FieldType
}

/**
 * Понятные поля вместо сырых `ключ=значение` (спецификация 5.4).
 * Тип выбирается по ключу и текущему значению: если значение не подходит под тип
 * (например, у пушки там не IP), поле правится как текст — ничего не ломаем.
 */
object FieldSpec {
    private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$""")
    private fun k(p: String) = Regex(p)

    private val WIDTHS = listOf(5, 10, 20, 30, 40, 50, 60, 80).map { "$it" to "$it МГц" }
    private val MODES = listOf("master" to "Точка доступа (AP)", "managed" to "Станция (Station)")

    private val rules: List<Pair<Regex, FieldType>> = listOf(
        k("""^radio\.\d+\.chanbw$""") to FieldType.Choice(WIDTHS),
        k("""^radio\.\d+\.mode$""") to FieldType.Choice(MODES),
        k("""^radio\.\d+\.freq$""") to FieldType.Number(0, 6200, "МГц"),
        k("""^radio\.\d+\.txpower$""") to FieldType.Number(0, 30, "dBm"),
        k("""^radio\.\d+\.distance$""") to FieldType.Number(0, 200_000, "м"),
        k("""^radio\.(\d+\.)?countrycode$""") to FieldType.Number(0, 999),
        k("""^.*\.port$""") to FieldType.Number(1, 65535),
        k("""^netconf\.\d+\.mtu$""") to FieldType.Number(576, 9000),
        k("""^vlan\.\d+\.id$""") to FieldType.Number(1, 4094),
        k("""^airmax\.priority$""") to FieldType.Number(0, 3),
        k("""^.*\.(ip|gateway)$""") to FieldType.Ip,
        k("""^.*\.netmask$""") to FieldType.Netmask,
        k("""^wireless\.\d+\.ssid$|^aaa\.\d+\.ssid$|^wpasupplicant\..*\.ssid$""") to FieldType.Text(1, 32),
        k("""^.*\.psk$""") to FieldType.Text(8, 63),
        k("""^system\.hostname$|^resolv\.host\.\d+\.name$""") to FieldType.Text(1, 63),
    )

    private val TOGGLES = listOf("enabled" to "disabled", "true" to "false", "on" to "off")

    fun typeFor(key: String, value: String): FieldType {
        // enabled/disabled и true/false — всегда переключатель; on/off — только у ключей-флагов.
        TOGGLES.firstOrNull { (on, off) -> value == on || value == off }?.let { (on, off) ->
            if (on != "on" || key.endsWith(".status") || key.endsWith("enabled")) return FieldType.Toggle(on, off)
        }
        val t = rules.firstOrNull { it.first.matches(key) }?.second ?: return FieldType.Text()
        return if (fits(t, value)) t else FieldType.Text()
    }

    /** Текущее значение пушки годится для типа (иначе правим как текст). */
    private fun fits(t: FieldType, v: String): Boolean = when (t) {
        is FieldType.Choice -> t.options.any { it.first == v }
        is FieldType.Number -> v.toLongOrNull() != null
        FieldType.Ip -> v.isEmpty() || IPV4.matches(v)
        FieldType.Netmask -> IPV4.matches(v)
        else -> true
    }

    /** Ошибка значения по-русски или null. */
    fun validate(t: FieldType, v: String): String? = when (t) {
        is FieldType.Toggle -> if (v == t.on || v == t.off) null else "Нужно «вкл» или «выкл»"
        is FieldType.Choice -> if (t.options.any { it.first == v }) null else "Выберите из списка"
        is FieldType.Number -> {
            val n = v.toLongOrNull()
            when {
                n == null -> "Нужно число"
                n !in t.min..t.max -> "От ${t.min} до ${t.max}${if (t.unit.isNotEmpty()) " ${t.unit}" else ""}"
                else -> null
            }
        }
        FieldType.Ip -> if (IPV4.matches(v)) null else "IP-адрес вида 192.168.1.20"
        FieldType.Netmask -> if (isMask(v)) null else "Маска вида 255.255.255.0"
        is FieldType.Text -> when {
            v.contains('\n') -> "Без переводов строки"
            v.length < t.minLen -> "Не короче ${t.minLen} символов"
            v.toByteArray().size > t.maxBytes -> "Не длиннее ${t.maxBytes} байт"
            else -> null
        }
    }

    private fun isMask(v: String): Boolean {
        if (!IPV4.matches(v)) return false
        val n = v.split('.').fold(0L) { a, p -> (a shl 8) or p.toLong() }
        val inv = n.inv() and 0xFFFFFFFFL
        return (inv and (inv + 1)) == 0L // единицы слева, нули справа
    }

    /** Понятная подпись значения для карточки. */
    fun label(key: String, value: String): String = when (val t = typeFor(key, value)) {
        is FieldType.Toggle -> if (value == t.on) "Вкл" else "Выкл"
        is FieldType.Choice -> t.options.firstOrNull { it.first == value }?.second ?: value
        is FieldType.Number -> if (t.unit.isNotEmpty()) "$value ${t.unit}" else value
        else -> value
    }
}
