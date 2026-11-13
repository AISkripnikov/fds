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
    /** /survey.json.cgi — JSON-массив. Сильные сети первыми. */
    fun parse(json: String): List<SurveyEntry> =
        parseArray(json).mapNotNull { it as? JsonObject }.map { e ->
            SurveyEntry(
                bssid = e.str("mac") ?: e.str("bssid"),
                ssid = e.str("essid")?.takeIf { it.isNotBlank() } ?: e.str("ssid")?.takeIf { it.isNotBlank() },
                frequencyMhz = e.dbl("frequency")?.let { if (it < 10) (it * 1000).toInt() else it.toInt() },
                channel = e.int("channel"),
                signalDbm = e.int("signal_level") ?: e.int("signal"),
                noiseDbm = e.int("noise_level") ?: e.int("noise"),
                security = e.str("encryption") ?: e.str("security"),
                mode = e.str("ieee_mode") ?: e.str("mode"),
            )
        }.sortedWith(compareByDescending<SurveyEntry> { it.signalDbm ?: Int.MIN_VALUE })
}

fun channelLoad(entries: List<SurveyEntry>): List<ChannelLoad> =
    entries.filter { it.frequencyMhz != null }
        .groupBy { it.frequencyMhz!! }
        .map { (f, list) -> ChannelLoad(f, list.size, list.mapNotNull { it.signalDbm }.maxOrNull() ?: -100) }
        .sortedBy { it.frequencyMhz }
