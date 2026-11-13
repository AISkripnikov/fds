package com.airmaxfield.airos

object StatusParser6 {
    fun parse(json: String): DeviceStatus {
        val root = parseRoot(json)
        val host = root.obj("host")
        val w = root.obj("wireless")
        val polling = w?.obj("polling")
        val noise = w?.int("noisef")
        return DeviceStatus(
            version = AirOsVersion.V6,
            hostname = host?.str("hostname"),
            model = host?.str("devmodel"),
            firmware = host?.str("fwversion"),
            uptimeSec = host?.long("uptime"),
            mode = w?.str("mode"),
            essid = w?.str("essid"),
            frequencyMhz = w?.int("frequency"),
            channelWidthMhz = w?.int("chwidth") ?: w?.int("chanbw"),
            txPowerDbm = w?.int("txpower"),
            distanceM = w?.int("distance"),
            signalDbm = w?.int("signal"),
            noiseDbm = noise,
            chainSignalDbm = chainsToDbm(w?.arr("chainrssi"), noise),
            remoteSignalDbm = null,
            // В airOS 6 CCQ отдаётся ×10 (951 → 95 %).
            ccqPercent = w?.int("ccq")?.let { if (it > 100) it / 10 else it },
            airmaxQuality = polling?.int("quality")?.takeIf { it > 0 },
            airmaxCapacity = polling?.int("capacity")?.takeIf { it > 0 },
            txRateMbps = w?.dbl("txrate"),
            rxRateMbps = w?.dbl("rxrate"),
            lanSpeed = lanSpeed(root),
        )
    }
}
