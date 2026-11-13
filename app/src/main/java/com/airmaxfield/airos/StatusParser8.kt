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
            airmaxQuality = airmax?.int("quality"),
            airmaxCapacity = airmax?.int("capacity"),
            txRateMbps = sta?.dbl("tx_rate") ?: airmax?.dbl("downlink_capacity")?.div(1000),
            rxRateMbps = sta?.dbl("rx_rate") ?: airmax?.dbl("uplink_capacity")?.div(1000),
            lanSpeed = lanSpeed(root),
        )
    }
}
