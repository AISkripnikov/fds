package com.airmaxfield.ui.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.diag.PingResult
import com.airmaxfield.diag.TcpPing
import com.airmaxfield.ui.components.AirCard
import com.airmaxfield.ui.theme.LocalAirColors

/**
 * Диагностика на объекте: ping с телефона (через TCP, по сети пушки) и отчёт по линку.
 * @param targets быстрые адреса: подпись → IP (эта пушка, вторая сторона, шлюз).
 */
@Composable
fun DiagBlock(
    targets: List<Pair<String, String>>,
    pingHost: String,
    running: Boolean,
    results: List<PingResult>,
    canPing: Boolean,
    reportBusy: Boolean,
    onPing: (String) -> Unit,
    onStop: () -> Unit,
    onReport: () -> Unit,
) {
    val c = LocalAirColors.current
    var host by rememberSaveable { mutableStateOf(targets.firstOrNull()?.second ?: "") }
    Text("Диагностика", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    AirCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ping с телефона: доходят ли пакеты до пушки и второй стороны моста и с какой задержкой. " +
                "Идёт по Wi‑Fi или кабелю к пушке, не через мобильную сеть.", color = c.muted, fontSize = 13.sp)
            if (canPing) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    targets.forEach { (label, ip) -> AssistChip(onClick = { host = ip }, label = { Text(label) }) }
                }
                OutlinedTextField(host, { host = it.trim() }, label = { Text("Адрес") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (running) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Остановить") }
                else Button(onClick = { onPing(host) }, enabled = host.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Ping (10 раз)")
                }
            } else {
                Text("В демо-режиме ping недоступен.", color = c.muted, fontSize = 13.sp)
            }
            if (results.isNotEmpty()) {
                val st = TcpPing.stats(results)
                Text("$pingHost: ответов ${st.received} из ${st.sent}, потери ${st.lossPercent}%" +
                    (st.avgMs?.let { " · мин/сред/макс ${st.minMs}/$it/${st.maxMs} мс" } ?: ""),
                    color = if (st.lossPercent == 0) c.good else if (st.received > 0) c.fair else c.bad, fontSize = 14.sp)
                results.takeLast(10).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${r.seq}", color = c.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 8.dp))
                        Text(r.ms?.let { "$it мс" } ?: (r.error ?: "нет ответа"),
                            color = if (r.ok) c.text else c.bad, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            OutlinedButton(onClick = onReport, enabled = !reportBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (reportBusy) "Собираю отчёт…" else "Отчёт по линку — отправить")
            }
        }
    }
}
