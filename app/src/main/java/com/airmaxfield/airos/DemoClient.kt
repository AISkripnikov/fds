package com.airmaxfield.airos

import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Демо-пушка: сигнал плавно гуляет в −72…−55 dBm, остальное — правдоподобные константы. */
class DemoClient(seed: Long = 1, private val now: () -> Long = System::currentTimeMillis) : AirOsClient {
    override val version = AirOsVersion.V8
    private val rnd = Random(seed)
    private var t = 0

    /** Сохранённая конфигурация и тестовая (с моментом автоотката). */
    private var saved = DEMO_CONFIG
    private var testing: String? = null
    private var testUntil = 0L

    @Synchronized private fun active(): String {
        if (testing != null && now() >= testUntil) testing = null // автооткат
        return testing ?: saved
    }

    /** Демо в тест-режиме прямо сейчас (для тестов и подсказок). */
    val inTest: Boolean get() { active(); return testing != null }

    override val hasTestMode get() = true

    override suspend fun uploadConfig(cfg: String, testSeconds: Int) {
        if (!ConfigBackup.looksLikeConfig(cfg)) throw AirOsError.Protocol("это не конфигурация")
        synchronized(this) { testing = cfg; testUntil = now() + testSeconds * 1000L }
    }

    override suspend fun confirmTest() = synchronized(this) {
        val cfg = testing.takeIf { now() < testUntil } ?: throw AirOsError.Protocol("тест уже закончился")
        saved = cfg; testing = null
    }

    override suspend fun cancelTest() = synchronized(this) { testing = null }

    override suspend fun login() {}

    override suspend fun status(): DeviceStatus {
        t++
        val wave = sin(t / 8.0) * 7.0 + rnd.nextDouble(-1.5, 1.5)
        val signal = (-63.5 + wave).roundToInt().coerceIn(-72, -55)
        val noise = -92
        val cfg = SystemConfig.parse(active())
        return DeviceStatus(
            version = AirOsVersion.V8,
            hostname = "Демо — Север A",
            model = "NanoStation 5AC loco",
            firmware = "v8.7.11",
            uptimeSec = 86_400L + t,
            mode = "ap-ptp",
            essid = cfg["wireless.1.ssid"] ?: "SEVER-LINK",
            frequencyMhz = cfg["radio.1.freq"]?.toIntOrNull() ?: 5180,
            channelWidthMhz = cfg["radio.1.chanbw"]?.toIntOrNull() ?: 40,
            txPowerDbm = cfg["radio.1.txpower"]?.toIntOrNull() ?: 20,
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

    override suspend fun downloadConfig(): String = active()

    override suspend fun reboot() { cancelTest() }
}

private val DEMO_CONFIG = """
# Демо-конфигурация для airMAX Field Tool Stage 3
# Wireless
radio.1.freq=5180
radio.1.ieee_mode=11acvht40
radio.1.chanbw=40
radio.1.txpower=20
radio.1.distance=2400
radio.1.ack.auto=enabled
radio.1.country=643
wireless.1.ssid=SEVER-LINK
wireless.1.psk=demo_password_123
wireless.1.hide_ssid=disabled
wireless.1.wds=disabled
wireless.1.security=wpa2
# Network
netconf.1.ip=192.168.1.20
netconf.1.netmask=255.255.255.0
netconf.1.gateway=192.168.1.1
netconf.1.mtu=1500
bridge.1.port.1.enabled=true
bridge.1.port.2.enabled=false
vlan.1.id=100
vlan.1.enabled=false
route.1.dest=0.0.0.0
route.1.netmask=0.0.0.0
route.1.gateway=192.168.1.1
resolv.nameserver.1.ip=8.8.8.8
resolv.nameserver.2.ip=1.1.1.1
# Advanced
airmax.enabled=true
airmax.priority=2
# Services
snmp.enabled=true
snmp.community=public
snmp.location=Север-А башня
snmp.contact=admin@demo.local
sshd.port=22
sshd.enabled=true
httpd.port=443
httpd.https.enabled=true
ntpclient.enabled=true
ntpclient.server.1.name=pool.ntp.org
syslog.remote.ip=192.168.1.10
syslog.remote.port=514
syslog.remote.enabled=false
discovery.enabled=false
# System
users.1.name=ubnt
users.1.password=demo_pass
users.1.uid=1000
system.hostname=sever-tower-a
system.timezone=Europe/Moscow
update.url=https://fw-update.ubnt.com
update.check.enabled=true
""".trimIndent() + "\n"
