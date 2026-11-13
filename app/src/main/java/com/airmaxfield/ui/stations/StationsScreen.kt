package com.airmaxfield.ui.stations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.airos.StationInfo
import com.airmaxfield.ui.components.AirCard
import com.airmaxfield.ui.components.DASH
import com.airmaxfield.ui.components.InfoRow
import com.airmaxfield.ui.components.Label
import com.airmaxfield.ui.status.fmtDistance
import com.airmaxfield.ui.status.fmtRate
import com.airmaxfield.ui.status.fmtUptime
import com.airmaxfield.ui.theme.LocalAirColors
import com.airmaxfield.ui.theme.color
import com.airmaxfield.ui.theme.percentQuality
import com.airmaxfield.ui.theme.signalQuality
import com.airmaxfield.ui.theme.snrQuality
import com.airmaxfield.ui.tools.StationsUi

@Composable
fun StationsScreen(ui: StationsUi, modifier: Modifier = Modifier) {
    val c = LocalAirColors.current
    val list = ui.list
    if (list == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (ui.error != null) Text(ui.error, color = c.bad, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
            else CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ui.error != null) Text("Не обновилось: ${ui.error}", color = c.fair, fontSize = 14.sp)
        if (list.isEmpty()) {
            AirCard(Modifier.fillMaxWidth()) {
                Text("Нет подключённых станций", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Для точки доступа — к ней никто не подключён. Для станции — нет связи с точкой доступа.",
                    color = c.muted, fontSize = 14.sp)
            }
        } else {
            Label("Станций: ${list.size} · обновление раз в 2 с")
            list.forEach { StationCard(it) }
        }
    }
}

@Composable
private fun StationCard(s: StationInfo) {
    val c = LocalAirColors.current
    AirCard(Modifier.fillMaxWidth()) {
        Text(s.name ?: s.mac ?: "Станция", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(listOfNotNull(s.mac.takeIf { s.name != null }, s.ip).joinToString(" · ").ifEmpty { DASH },
            color = c.muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            BigValue("Сигнал здесь", s.signalDbm, Modifier.weight(1f))
            BigValue("Сигнал там", s.remoteSignalDbm, Modifier.weight(1f))
        }
        InfoRow("Цепь 0 / Цепь 1", if (s.chainSignalDbm.isEmpty()) DASH else s.chainSignalDbm.joinToString(" / "))
        InfoRow("Шум", s.noiseDbm?.let { "$it dBm" } ?: DASH)
        InfoRow("SNR", s.snrDb?.let { "$it dB" } ?: DASH, s.snrDb?.let { snrQuality(it).color() })
        if (s.airmaxQuality != null || s.ccqPercent == null) {
            InfoRow("airMAX качество", s.airmaxQuality?.let { "$it %" } ?: DASH, s.airmaxQuality?.let { percentQuality(it).color() })
            InfoRow("airMAX ёмкость", s.airmaxCapacity?.let { "$it %" } ?: DASH, s.airmaxCapacity?.let { percentQuality(it).color() })
        } else {
            InfoRow("CCQ", "${s.ccqPercent} %", percentQuality(s.ccqPercent).color())
        }
        InfoRow("TX / RX", listOf(s.txRateMbps, s.rxRateMbps).joinToString(" / ") { it?.let(::fmtRate) ?: DASH } + " Мбит/с")
        InfoRow("Дистанция", s.distanceM?.let(::fmtDistance) ?: DASH)
        InfoRow("Задержка", s.latencyMs?.let { "$it мс" } ?: DASH)
        InfoRow("На связи", s.uptimeSec?.let(::fmtUptime) ?: DASH)
    }
}

@Composable
private fun BigValue(label: String, dbm: Int?, modifier: Modifier) {
    val c = LocalAirColors.current
    Column(modifier) {
        Label(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(dbm?.toString() ?: DASH, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                color = if (dbm == null) c.text else signalQuality(dbm).color())
            if (dbm != null) Text(" dBm", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}
