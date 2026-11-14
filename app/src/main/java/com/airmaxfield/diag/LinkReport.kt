package com.airmaxfield.diag

import com.airmaxfield.airos.DeviceStatus
import com.airmaxfield.ui.theme.Quality
import com.airmaxfield.ui.theme.signalQuality
import com.airmaxfield.ui.theme.snrQuality

/**
 * Текстовый отчёт по линку для отправки в мессенджер: что за пушки, какой сигнал, какой канал.
 * Секреты (пароли, ключи) в отчёт не попадают никогда.
 */
object LinkReport {
    data class Side(val title: String, val host: String, val status: DeviceStatus?)

    fun build(
        near: Side, far: Side? = null, ping: PingStats? = null, at: String, appVersion: String, pingHost: String? = null,
    ): String = buildString {
        appendLine("Отчёт по линку — $at")
        appendLine()
        side(near)
        far?.let { appendLine(); side(it) }
        ping?.let {
            appendLine()
            appendLine("Ping (TCP) до ${pingHost ?: far?.host ?: near.host}: ${it.received}/${it.sent}, потери ${it.lossPercent}%" +
                (if (it.avgMs != null) ", мин/сред/макс ${it.minMs}/${it.avgMs}/${it.maxMs} мс" else ""))
        }
        appendLine()
        append("airMAX Поле $appVersion")
    }

    private fun StringBuilder.side(s: Side) {
        val st = s.status
        appendLine("■ ${s.title} (${s.host})")
        if (st == null) { appendLine("  нет данных"); return }
        line("Модель", listOfNotNull(st.model, st.firmware).joinToString(", "))
        line("Имя", st.hostname)
        line("Режим", st.mode?.let { modeTitle(it) })
        line("SSID", st.essid)
        line("Канал", listOfNotNull(st.frequencyMhz?.let { "$it МГц" }, st.channelWidthMhz?.let { "ширина $it МГц" }).joinToString(", "))
        line("Мощность", st.txPowerDbm?.let { "$it dBm" })
        line("Дистанция", st.distanceM?.let { if (it >= 1000) "%.1f км".format(it / 1000.0) else "$it м" })
        line("Сигнал", st.signalDbm?.let { "$it dBm (${q(signalQuality(it))})" })
        if (st.chainSignalDbm.isNotEmpty()) line("По цепям", st.chainSignalDbm.joinToString(" / ") { "$it" } + " dBm")
        line("Удалённый сигнал", st.remoteSignalDbm?.let { "$it dBm" })
        line("Шум", st.noiseDbm?.let { "$it dBm" })
        line("SNR", st.snrDb?.let { "$it дБ (${q(snrQuality(it))})" })
        line("airMAX качество/ёмкость", if (st.airmaxQuality != null || st.airmaxCapacity != null)
            "${st.airmaxQuality ?: "—"}% / ${st.airmaxCapacity ?: "—"}%" else null)
        line("CCQ", st.ccqPercent?.let { "$it%" })
        line("Скорость TX/RX", if (st.txRateMbps != null || st.rxRateMbps != null)
            "${st.txRateMbps?.let { "%.0f".format(it) } ?: "—"} / ${st.rxRateMbps?.let { "%.0f".format(it) } ?: "—"} Мбит/с" else null)
        line("LAN", st.lanSpeed)
        line("Работает", st.uptimeSec?.let { uptime(it) })
    }

    private fun StringBuilder.line(label: String, value: String?) {
        if (!value.isNullOrBlank()) appendLine("  $label: $value")
    }

    private fun q(q: Quality) = when (q) {
        Quality.GOOD -> "хорошо"; Quality.FAIR -> "средне"; Quality.BAD -> "плохо"; Quality.UNKNOWN -> "?"
    }

    fun modeTitle(m: String) = when (m.lowercase()) {
        "ap-ptp" -> "точка доступа PtP"; "sta-ptp" -> "станция PtP"
        "ap", "ap-ptmp" -> "точка доступа"; "sta", "sta-ptmp" -> "станция"
        else -> m
    }

    fun uptime(sec: Long): String {
        val d = sec / 86_400; val h = sec % 86_400 / 3600; val m = sec % 3600 / 60
        return if (d > 0) "$d д $h ч" else if (h > 0) "$h ч $m мин" else "$m мин"
    }
}
