package com.airmaxfield.airos

import kotlinx.serialization.json.JsonObject

object StatusParser8 {
    fun parse(json: String): DeviceStatus {
        val root = parseRoot(json)
        val host = root.obj("host")
        val w = root.obj("wireless")
        val sta = w?.arr("sta")?.firstOrNull() as? JsonObject
        val airmax = sta?.obj("airmax")
        val noise = w?.int("noisef") ?: sta?.int("noisefloor")
        return DeviceStatus(
            version = AirOsVersion.V8,
            hostname = host?.str("hostname"),
            model = host?.str("devmodel"),
            firmware = host?.str("fwversion"),
            uptimeSec = host?.long("uptime"),
            mode = w?.str("mode"),
            essid = w?.str("essid"),
            frequencyMhz = w?.int("frequency"),
            channelWidthMhz = w?.int("chanbw"),
            txPowerDbm = w?.int("txpower"),
            distanceM = sta?.int("distance") ?: w?.int("distance"),
            signalDbm = sta?.int("signal") ?: w?.int("signal"),
            noiseDbm = noise,
            chainSignalDbm = chainsToDbm(sta?.arr("chainrssi"), noise),
            remoteSignalDbm = sta?.obj("remote")?.int("signal"),
            ccqPercent = w?.int("ccq")?.let { if (it > 100) it / 10 else it },
            airmaxQuality = airmax?.int("quality")?.takeIf { it > 0 },
            airmaxCapacity = airmax?.int("capacity")?.takeIf { it > 0 },
            txRateMbps = sta?.dbl("tx_rate") ?: airmax?.dbl("downlink_capacity")?.div(1000)
                ?: capacityMbps(w, sta, tx = true),
            rxRateMbps = sta?.dbl("rx_rate") ?: airmax?.dbl("uplink_capacity")?.div(1000)
                ?: capacityMbps(w, sta, tx = false),
            lanSpeed = lanSpeed(root),
            linkScore = linkScore(sta),
            airtimePercent = w?.obj("polling")?.int("use"),
            remote = sta?.obj("remote")?.let(::remoteSide),
            antennaGainDbi = w?.int("antenna_gain"),
        )
    }

    /** Оценка линка 8.7+: худшая из dl/ul (0 — нет данных). */
    internal fun linkScore(sta: JsonObject?): Int? =
        listOfNotNull(sta?.int("dl_linkscore"), sta?.int("ul_linkscore")).filter { it > 0 }.minOrNull()

    /**
     * Пропускная способность 8.7+ (кбит/с → Мбит/с): dl — от точки доступа к станции.
     * Для станции «передача» — uplink, для точки доступа — downlink.
     */
    internal fun capacityMbps(w: JsonObject?, sta: JsonObject?, tx: Boolean): Double? {
        val src = sta?.obj("airmax")?.takeIf { it["dl_capacity"] != null } ?: w?.obj("polling") ?: return null
        val isAp = w?.str("mode")?.startsWith("ap") == true
        val key = if (tx == isAp) "dl_capacity" else "ul_capacity"
        return src.dbl(key)?.takeIf { it > 0 }?.div(1000)
    }

    private val FW = Regex("""v\d+\.\d+(\.\d+)?""")

    internal fun remoteSide(r: JsonObject): RemoteSide {
        val noise = r.int("noisefloor")
        val eth = r.arr("ethlist")?.firstOrNull() as? JsonObject
        return RemoteSide(
            hostname = r.str("hostname"),
            model = r.str("platform"),
            firmware = r.str("version")?.let { FW.find(it)?.value ?: it },
            ip = (r.arr("ipaddr")?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)?.content ?: r.str("lastip"),
            mode = r.str("mode"),
            signalDbm = r.int("signal"),
            noiseDbm = noise,
            chainSignalDbm = chainsToDbm(r.arr("chainrssi"), noise),
            txPowerDbm = r.int("tx_power"),
            distanceM = r.int("distance"),
            uptimeSec = r.long("uptime"),
            lanSpeed = eth?.let { e -> if (e.str("plugged") == "false") "нет линка" else e.int("speed")?.let { "$it Мбит/с" } },
            antennaGainDbi = r.int("antenna_gain"),
            cableLossDb = r.int("cable_loss"),
        )
    }
}
