package com.airmaxfield.airos

import kotlinx.serialization.json.JsonObject

/** Сеть, найденная обзором эфира. */
data class SurveyEntry(
    val bssid: String?,
    val ssid: String?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val signalDbm: Int?,
    val noiseDbm: Int?,
    val security: String?,
    val mode: String?,
)

/** Загрузка частоты: сколько сетей и самый сильный сигнал. */
data class ChannelLoad(val frequencyMhz: Int, val count: Int, val strongestDbm: Int)

object SurveyParser {
    /**
     * /survey.json.cgi. Формат зависит от прошивки: JSON-массив сетей, либо объект,
     * внутри которого такой массив (например, {"survey": [...]}). Сильные сети первыми.
     */
    fun parse(json: String): List<SurveyEntry> {
        val root = try {
            kotlinx.serialization.json.Json.parseToJsonElement(json)
        } catch (e: Exception) {
            throw AirOsError.Protocol("обзор эфира: не JSON")
        }
        val list = findList(root) ?: throw AirOsError.Protocol(
            "обзор эфира: пушка не вернула список сетей (${json.trim().take(80)}). Сохраните дамп — по нему поправлю")
        return list.mapNotNull { it as? JsonObject }.map { e ->
            SurveyEntry(
                bssid = e.str("mac") ?: e.str("bssid"),
                ssid = e.str("essid")?.takeIf { it.isNotBlank() } ?: e.str("ssid")?.takeIf { it.isNotBlank() },
                frequencyMhz = (e.dbl("frequency") ?: e.dbl("freq"))?.let { if (it < 10) (it * 1000).toInt() else it.toInt() },
                channel = e.int("channel"),
                signalDbm = e.int("signal_level") ?: e.int("signal") ?: e.int("rssi")?.takeIf { it < 0 },
                noiseDbm = e.int("noise_level") ?: e.int("noise") ?: e.int("noisefloor"),
                security = e.str("encryption") ?: e.str("security") ?: e.str("auth"),
                mode = e.str("ieee_mode") ?: e.str("mode"),
            )
        }.sortedWith(compareByDescending<SurveyEntry> { it.signalDbm ?: Int.MIN_VALUE })
    }

    /** Первый список объектов: сам корень или значение поля (до двух уровней вложенности). */
    private fun findList(e: kotlinx.serialization.json.JsonElement, depth: Int = 0): kotlinx.serialization.json.JsonArray? {
        if (e is kotlinx.serialization.json.JsonArray) return e.takeIf { a -> a.isEmpty() || a.any { it is JsonObject } }
        if (e !is JsonObject || depth >= 2) return null
        return e.values.firstNotNullOfOrNull { findList(it, depth + 1) }
    }
}

fun channelLoad(entries: List<SurveyEntry>): List<ChannelLoad> =
    entries.filter { it.frequencyMhz != null }
        .groupBy { it.frequencyMhz!! }
        .map { (f, list) -> ChannelLoad(f, list.size, list.mapNotNull { it.signalDbm }.maxOrNull() ?: -100) }
        .sortedBy { it.frequencyMhz }
