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
}
