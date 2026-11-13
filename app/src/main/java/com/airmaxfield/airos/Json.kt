package com.airmaxfield.airos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** Терпимый к отсутствию полей доступ к JSON пушки. */
internal fun parseRoot(json: String): JsonObject = try {
    Json.parseToJsonElement(json).jsonObject
} catch (e: Exception) {
    throw AirOsError.Protocol("не JSON")
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private val NUMBER = Regex("""-?\d+(\.\d+)?""")

/** Число из JSON: 5180, "5180 MHz", "300". */
internal fun JsonElement?.num(): Double? {
    val p = this as? JsonPrimitive ?: return null
    p.doubleOrNull?.let { return it }
    return p.contentOrNull?.let { NUMBER.find(it)?.value?.toDoubleOrNull() }
}

internal fun JsonObject.dbl(key: String): Double? = this[key].num()
internal fun JsonObject.int(key: String): Int? = dbl(key)?.toInt()
internal fun JsonObject.long(key: String): Long? = dbl(key)?.toLong()

/** chainrssi → dBm: rssi + noise, нулевые цепи отбрасываются. */
internal fun chainsToDbm(chains: JsonArray?, noise: Int?): List<Int> {
    if (chains == null || noise == null) return emptyList()
    return chains.mapNotNull { it.num()?.toInt() }.filter { it > 0 }.map { it + noise }
}

/** Скорость LAN первого eth-интерфейса. */
internal fun lanSpeed(root: JsonObject): String? {
    val eth = root.arr("interfaces")?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.str("ifname")?.startsWith("eth") == true } ?: return null
    val st = eth.obj("status") ?: return null
    if (st.str("plugged") == "false") return "нет линка"
    return st.int("speed")?.let { "$it Мбит/с" }
}
