package com.airmaxfield.airos

enum class AirOsVersion { V6, V8 }

/** Единая модель статуса пушки. Любое поле может отсутствовать (старые/иные прошивки). */
data class DeviceStatus(
    val version: AirOsVersion,
    val hostname: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val uptimeSec: Long? = null,
    /** "ap-ptp", "sta-ptp", "ap", "sta" — как отдаёт пушка. */
    val mode: String? = null,
    val essid: String? = null,
    val frequencyMhz: Int? = null,
    val channelWidthMhz: Int? = null,
    val txPowerDbm: Int? = null,
    val distanceM: Int? = null,
    val signalDbm: Int? = null,
    val noiseDbm: Int? = null,
    /** Сигнал по цепям в dBm; пустой список, если нет данных. */
    val chainSignalDbm: List<Int> = emptyList(),
    val remoteSignalDbm: Int? = null,
    val ccqPercent: Int? = null,
    val airmaxQuality: Int? = null,
    val airmaxCapacity: Int? = null,
    val txRateMbps: Double? = null,
    val rxRateMbps: Double? = null,
    val lanSpeed: String? = null,
) {
    val snrDb: Int? get() = if (signalDbm != null && noiseDbm != null) signalDbm - noiseDbm else null
}
