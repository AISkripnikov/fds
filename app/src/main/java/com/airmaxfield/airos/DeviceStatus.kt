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
    /** airOS 8: оценка линка, % (dl/ul linkscore — берём худшую сторону). */
    val linkScore: Int? = null,
    /** airOS 8: эфир занят, % (polling.use). Меньше — лучше. */
    val airtimePercent: Int? = null,
    /** Вторая сторона линка, как её видит эта пушка (без подключения к ней). */
    val remote: RemoteSide? = null,
    /** Усиление антенны, как оно задано в настройках пушки, dBi. */
    val antennaGainDbi: Int? = null,
) {
    val snrDb: Int? get() = if (signalDbm != null && noiseDbm != null) signalDbm - noiseDbm else null
}

/** Данные второй стороны линка из ответа этой пушки (airOS 8: wireless.sta[].remote). */
data class RemoteSide(
    val hostname: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val ip: String? = null,
    val mode: String? = null,
    val signalDbm: Int? = null,
    val noiseDbm: Int? = null,
    val chainSignalDbm: List<Int> = emptyList(),
    val txPowerDbm: Int? = null,
    val distanceM: Int? = null,
    val uptimeSec: Long? = null,
    val lanSpeed: String? = null,
    val antennaGainDbi: Int? = null,
    val cableLossDb: Int? = null,
)
