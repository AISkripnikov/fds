package com.airmaxfield.airos

import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Демо-пушка: сигнал плавно гуляет в −72…−55 dBm, остальное — правдоподобные константы. */
class DemoClient(seed: Long = 1) : AirOsClient {
    override val version = AirOsVersion.V8
    private val rnd = Random(seed)
    private var t = 0

    override suspend fun login() {}

    override suspend fun status(): DeviceStatus {
        t++
        val wave = sin(t / 8.0) * 7.0 + rnd.nextDouble(-1.5, 1.5)
        val signal = (-63.5 + wave).roundToInt().coerceIn(-72, -55)
        val noise = -92
        return DeviceStatus(
            version = AirOsVersion.V8,
            hostname = "Демо — Север A",
            model = "NanoStation 5AC loco",
            firmware = "v8.7.11",
            uptimeSec = 86_400L + t,
            mode = "ap-ptp",
            essid = "SEVER-LINK",
            frequencyMhz = 5180,
            channelWidthMhz = 40,
            txPowerDbm = 20,
            distanceM = 2400,
            signalDbm = signal,
            noiseDbm = noise,
            chainSignalDbm = listOf(signal - 1, signal - 3),
            remoteSignalDbm = signal - 2,
            ccqPercent = null,
            airmaxQuality = (96 + (signal + 58) / 2).coerceIn(40, 100),
            airmaxCapacity = (78 + (signal + 58)).coerceIn(30, 100),
            txRateMbps = 520.0 + (signal + 58) * 10,
            rxRateMbps = 480.0 + (signal + 58) * 10,
            lanSpeed = "100 Мбит/с",
        )
    }

    override suspend fun rawDump(): Map<String, String> = mapOf("/demo" to "демо-режим, данных пушки нет")

    override suspend fun stations(): List<StationInfo> {
        val s = status()
        return listOf(StationInfo(
            mac = "24:5A:4C:AA:BB:CC", name = "Демо — Север B", ip = "192.168.1.21",
            signalDbm = s.signalDbm, remoteSignalDbm = s.remoteSignalDbm, noiseDbm = s.noiseDbm,
            chainSignalDbm = s.chainSignalDbm, airmaxQuality = s.airmaxQuality, airmaxCapacity = s.airmaxCapacity,
            txRateMbps = s.txRateMbps, rxRateMbps = s.rxRateMbps, distanceM = 2400, uptimeSec = 3600L + t, latencyMs = 2,
        ))
    }

    override suspend fun survey(): List<SurveyEntry> {
        kotlinx.coroutines.delay(1500)
        return listOf(
            SurveyEntry("24:5A:4C:00:00:02", "SEVER-LINK", 5180, 36, -56, -94, "WPA2", "11acvht40"),
            SurveyEntry("24:5A:4C:00:00:05", "Kafe-WiFi", 5200, 40, -67, -94, "WPA2", "11acvht80"),
            SurveyEntry("24:5A:4C:00:00:06", null, 5745, 149, -71, -95, "WPA2", "11naht40"),
            SurveyEntry("24:5A:4C:00:00:01", "Neighbor-1", 5180, 36, -78, -95, "WPA2", "11naht40"),
            SurveyEntry("24:5A:4C:00:00:07", "Sklad", 5500, 100, -83, -95, "none", "11naht20"),
            SurveyEntry("24:5A:4C:00:00:08", "Ferma-PtP", 5745, 149, -86, -95, "WPA2", "11acvht40"),
        )
    }

    override suspend fun downloadConfig(): String =
        (1..18).joinToString("\n") { "radio.1.demo$it=$it" } + "\nwireless.1.ssid=SEVER-LINK\nnetconf.1.ip=192.168.1.20\n"

    override suspend fun reboot() {}
}
