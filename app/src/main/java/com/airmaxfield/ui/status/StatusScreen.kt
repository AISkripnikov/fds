package com.airmaxfield.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.airos.AirOsError
import com.airmaxfield.airos.AirOsVersion
import com.airmaxfield.airos.DeviceStatus
import com.airmaxfield.ui.components.AirCard
import com.airmaxfield.ui.components.DASH
import com.airmaxfield.ui.components.InfoRow
import com.airmaxfield.ui.components.MetricTile
import com.airmaxfield.ui.components.SignalCard
import com.airmaxfield.ui.components.SignalGraph
import com.airmaxfield.ui.theme.AirmaxTheme
import com.airmaxfield.ui.theme.LocalAirColors
import com.airmaxfield.ui.theme.Quality
import com.airmaxfield.ui.theme.percentQuality
import com.airmaxfield.ui.theme.snrQuality

@Composable
fun StatusScreen(
    ui: StatusUi,
    staleSec: Int?,
    toneOn: Boolean,
    onToneToggle: (Boolean) -> Unit,
    onEditPassword: () -> Unit,
    onSaveDump: () -> Unit,
    modifier: Modifier = Modifier,
    /** Подключиться ко второй стороне (null — кнопку не показывать). */
    onOpenRemote: ((com.airmaxfield.airos.RemoteSide) -> Unit)? = null,
    onAlign: (() -> Unit)? = null,
) {
    val c = LocalAirColors.current
    val s = ui.status
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ErrorBanner(ui, staleSec, onEditPassword, onSaveDump)
        SignalCard(s)
        if (onAlign != null) {
            Button(onClick = onAlign, modifier = Modifier.fillMaxWidth()) {
                Text("Режим юстировки — куда крутить", fontSize = 16.sp)
            }
        }
        TileGrid(s)
        SignalGraph(ui.history)
        AirCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = c.accent)
                Text("  Звук юстировки", color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(checked = toneOn, onCheckedChange = onToneToggle)
            }
            Text("Чем выше тон, тем лучше сигнал. Вибрация — когда стало лучше на 2 dB.",
                color = c.muted, fontSize = 13.sp)
        }
        Summary(s)
        s?.remote?.let { RemoteCard(it, onOpenRemote) }
    }
}

/** Вторая сторона линка — по данным этой пушки, без подключения к ней. */
@Composable
private fun RemoteCard(r: com.airmaxfield.airos.RemoteSide, onOpen: ((com.airmaxfield.airos.RemoteSide) -> Unit)?) {
    val c = LocalAirColors.current
    AirCard(Modifier.fillMaxWidth()) {
        Text("Вторая сторона", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("Данные этой пушки о второй — подключаться к ней не нужно.", color = c.muted, fontSize = 13.sp)
        InfoRow("Имя", r.hostname ?: DASH)
        InfoRow("Модель", r.model ?: DASH)
        InfoRow("Прошивка", r.firmware ?: DASH)
        InfoRow("IP", r.ip ?: DASH)
        InfoRow("Режим", modeName(r.mode))
        InfoRow("Сигнал у неё", r.signalDbm?.let { "$it dBm" } ?: DASH,
            valueColor = r.signalDbm?.let { com.airmaxfield.ui.theme.signalQuality(it) }?.let {
                when (it) { Quality.GOOD -> c.good; Quality.FAIR -> c.fair; Quality.BAD -> c.bad; else -> null }
            })
        if (r.chainSignalDbm.isNotEmpty()) InfoRow("Цепи у неё", r.chainSignalDbm.joinToString(" / "))
        InfoRow("Шум у неё", r.noiseDbm?.let { "$it dBm" } ?: DASH)
        InfoRow("SNR у неё", if (r.signalDbm != null && r.noiseDbm != null) "${r.signalDbm - r.noiseDbm} dB" else DASH)
        InfoRow("Мощность", r.txPowerDbm?.let { "$it dBm" } ?: DASH)
        InfoRow("LAN", r.lanSpeed ?: DASH)
        InfoRow("Время работы", r.uptimeSec?.let { fmtUptime(it) } ?: DASH)
        if (onOpen != null && r.ip != null) {
            androidx.compose.material3.OutlinedButton(onClick = { onOpen(r) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Подключиться к ней (${r.ip})") }
        }
    }
}

/** Занятость эфира: ≤ 50% хорошо, ≤ 80% средне, больше — перегружен. */
private fun airtimeQuality(p: Int): Quality = when {
    p <= 50 -> Quality.GOOD
    p <= 80 -> Quality.FAIR
    else -> Quality.BAD
}

@Composable
private fun TileGrid(s: DeviceStatus?) {
    val useCcq = s != null && s.airmaxQuality == null && s.ccqPercent != null
    val rows = listOf(
        Triple("Шум", s?.noiseDbm?.toString(), "dBm" to Quality.UNKNOWN),
        Triple("SNR", s?.snrDb?.toString(), "dB" to snrQuality(s?.snrDb)),
        when {
            s?.airmaxQuality == null && s?.linkScore != null ->
                Triple("Оценка линка", s.linkScore.toString(), "%" to percentQuality(s.linkScore))
            useCcq -> Triple("CCQ", s?.ccqPercent?.toString(), "%" to percentQuality(s?.ccqPercent))
            else -> Triple("airMAX качество", s?.airmaxQuality?.toString(), "%" to percentQuality(s?.airmaxQuality))
        },
        if (s?.airmaxCapacity == null && s?.airtimePercent != null)
            Triple("Эфир занят", s.airtimePercent.toString(), "%" to airtimeQuality(s.airtimePercent))
        else Triple("airMAX ёмкость", s?.airmaxCapacity?.toString(), "%" to percentQuality(s?.airmaxCapacity)),
        Triple("TX", s?.txRateMbps?.let { fmtRate(it) }, "Мбит/с" to Quality.UNKNOWN),
        Triple("RX", s?.rxRateMbps?.let { fmtRate(it) }, "Мбит/с" to Quality.UNKNOWN),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value, uq) ->
                    MetricTile(label, value, uq.first, uq.second, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Summary(s: DeviceStatus?) {
    AirCard(Modifier.fillMaxWidth()) {
        Text("Пушка", color = LocalAirColors.current.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        InfoRow("Модель", s?.model ?: DASH)
        InfoRow("Прошивка", s?.firmware ?: DASH)
        InfoRow("Режим", modeName(s?.mode))
        InfoRow("SSID", s?.essid ?: DASH)
        InfoRow("Частота / ширина", listOfNotNull(s?.frequencyMhz?.let { "$it МГц" }, s?.channelWidthMhz?.let { "$it МГц" })
            .joinToString(" / ").ifEmpty { DASH })
        InfoRow("Мощность", s?.txPowerDbm?.let { "$it dBm" } ?: DASH)
        InfoRow("Дистанция", s?.distanceM?.let { fmtDistance(it) } ?: DASH)
        InfoRow("LAN", s?.lanSpeed ?: DASH)
        InfoRow("Время работы", s?.uptimeSec?.let { fmtUptime(it) } ?: DASH)
    }
}

@Composable
private fun ErrorBanner(ui: StatusUi, staleSec: Int?, onEditPassword: () -> Unit, onSaveDump: () -> Unit) {
    val e = ui.error
    val stale = staleSec != null && staleSec > STALE_AFTER_SEC
    if (e == null && !stale) return
    val c = LocalAirColors.current
    AirCard(Modifier.fillMaxWidth()) {
        val text = when {
            e is AirOsError.BadCredentials -> "Неверный логин или пароль. Опрос остановлен."
            e == null || e is AirOsError.Unreachable ->
                "Нет данных ${staleSec ?: 0} с — пушка не отвечает. Продолжаю опрос."
            else -> e.message ?: "Ошибка"
        }
        Text(text, color = if (e == null || e is AirOsError.Unreachable) c.fair else c.bad, fontSize = 15.sp)
        if (ui.stopped || e is AirOsError.Protocol) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (ui.stopped) Button(onClick = onEditPassword) { Text("Изменить пароль") }
                if (e is AirOsError.Protocol) Button(onClick = onSaveDump,
                    colors = ButtonDefaults.buttonColors(containerColor = c.line, contentColor = c.text)) {
                    Text("Сохранить дамп")
                }
            }
        }
    }
}

/** Данные старше этого считаются устаревшими: «нет данных», звук юстировки молчит. */
const val STALE_AFTER_SEC = 2

fun modeName(m: String?): String = when (m) {
    null -> DASH
    "ap-ptp" -> "Точка доступа PtP"
    "sta-ptp" -> "Станция PtP"
    "ap-ptmp", "ap" -> "Точка доступа"
    "sta-ptmp", "sta" -> "Станция"
    else -> m
}

fun fmtRate(v: Double): String = if (v >= 100) v.toInt().toString() else "%.1f".format(v)
fun fmtDistance(m: Int): String = if (m >= 1000) "%.1f км".format(m / 1000.0) else "$m м"
fun fmtUptime(sec: Long): String {
    val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60
    return if (d > 0) "$d д %02d:%02d".format(h, m) else "%02d:%02d".format(h, m)
}

@Preview(heightDp = 1100)
@Composable
private fun StatusPreview() {
    AirmaxTheme {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().then(Modifier),
        ) {
            StatusScreen(
                StatusUi(
                    status = DeviceStatus(AirOsVersion.V8, model = "NanoStation 5AC loco", firmware = "v8.7.11",
                        mode = "ap-ptp", essid = "SEVER", frequencyMhz = 5180, channelWidthMhz = 40, signalDbm = -58,
                        noiseDbm = -92, chainSignalDbm = listOf(-59, -61), remoteSignalDbm = -60, airmaxQuality = 96,
                        airmaxCapacity = 78, txRateMbps = 520.0, rxRateMbps = 480.0, uptimeSec = 90000, distanceM = 2400),
                    history = (0 until 60).map { -65 + it / 6 },
                ),
                staleSec = 0, toneOn = false, onToneToggle = {}, onEditPassword = {}, onSaveDump = {},
            )
        }
    }
}
