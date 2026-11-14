package com.airmaxfield.airos

import kotlinx.serialization.json.JsonObject

/** Подключённая станция (для AP — клиенты, для Station — точка доступа). */
data class StationInfo(
    val mac: String? = null,
    val name: String? = null,
    val ip: String? = null,
    val signalDbm: Int? = null,
    val remoteSignalDbm: Int? = null,
    val noiseDbm: Int? = null,
    val chainSignalDbm: List<Int> = emptyList(),
    val ccqPercent: Int? = null,
    val airmaxQuality: Int? = null,
    val airmaxCapacity: Int? = null,
    val txRateMbps: Double? = null,
    val rxRateMbps: Double? = null,
    val distanceM: Int? = null,
    val uptimeSec: Long? = null,
    val latencyMs: Int? = null,
) {
    val snrDb: Int? get() = if (signalDbm != null && noiseDbm != null) signalDbm - noiseDbm else null
}

object StationsParser {
    /** airOS 8: станции лежат в status.cgi → wireless.sta[]. */
    fun fromStatus8(json: String): List<StationInfo> {
        val w = parseRoot(json).obj("wireless") ?: return emptyList()
        val wNoise = w.int("noisef")
        return w.arr("sta").orEmpty().mapNotNull { it as? JsonObject }.map { s ->
            val noise = s.int("noisefloor") ?: wNoise
            val airmax = s.obj("airmax")
            val remote = s.obj("remote")
            StationInfo(
                mac = s.str("mac"),
                name = remote?.str("hostname") ?: s.str("name"),
                ip = s.str("lastip"),
                signalDbm = s.int("signal"),
                remoteSignalDbm = remote?.int("signal"),
                noiseDbm = noise,
                chainSignalDbm = chainsToDbm(s.arr("chainrssi"), noise),
                ccqPercent = s.int("ccq")?.let(::normCcq),
                airmaxQuality = airmax?.int("quality")?.takeIf { it > 0 } ?: StatusParser8.linkScore(s),
                airmaxCapacity = airmax?.int("capacity")?.takeIf { it > 0 },
                txRateMbps = s.dbl("tx_rate") ?: airmax?.dbl("downlink_capacity")?.div(1000)
                    ?: StatusParser8.capacityMbps(w, s, tx = true),
                rxRateMbps = s.dbl("rx_rate") ?: airmax?.dbl("uplink_capacity")?.div(1000)
                    ?: StatusParser8.capacityMbps(w, s, tx = false),
                distanceM = s.int("distance"),
                uptimeSec = s.long("uptime"),
                latencyMs = s.int("tx_latency") ?: remote?.int("tx_latency"),
            )
        }
    }

    /** airOS 6: GET /sta.cgi — JSON-массив станций. */
    fun fromSta6(json: String): List<StationInfo> =
        parseArray(json).mapNotNull { it as? JsonObject }.map { s ->
            val noise = s.int("noisefloor")
            val airmax = s.obj("airmax")
            val remote = s.obj("remote")
            StationInfo(
                mac = s.str("mac"),
                name = s.str("name") ?: remote?.str("hostname"),
                ip = s.str("lastip"),
                signalDbm = s.int("signal"),
                remoteSignalDbm = remote?.int("signal"),
                noiseDbm = noise,
                chainSignalDbm = chainsToDbm(s.arr("chainrssi"), noise),
                ccqPercent = s.int("ccq")?.let(::normCcq),
                airmaxQuality = airmax?.int("quality")?.takeIf { it > 0 } ?: StatusParser8.linkScore(s),
                airmaxCapacity = airmax?.int("capacity")?.takeIf { it > 0 },
                txRateMbps = s.dbl("tx"),
                rxRateMbps = s.dbl("rx"),
                distanceM = s.int("distance"),
                uptimeSec = s.long("uptime"),
                latencyMs = s.int("tx_latency") ?: remote?.int("tx_latency"),
            )
        }

    /** CCQ в airOS часто ×10 (952 → 95 %). */
    private fun normCcq(v: Int) = if (v > 100) v / 10 else v
}
