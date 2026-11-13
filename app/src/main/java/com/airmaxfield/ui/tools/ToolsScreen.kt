package com.airmaxfield.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.airos.SurveyEntry
import com.airmaxfield.airos.channelLoad
import com.airmaxfield.ui.components.AirCard
import com.airmaxfield.ui.components.DASH
import com.airmaxfield.ui.components.Label
import com.airmaxfield.ui.theme.LocalAirColors
import com.airmaxfield.ui.theme.color
import com.airmaxfield.ui.theme.signalQuality
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolsScreen(
    survey: SurveyUi,
    tools: ToolsUi,
    deviceName: String,
    onSurvey: () -> Unit,
    onSaveConfig: () -> Unit,
    onExport: (File) -> Unit,
    onDelete: (File) -> Unit,
    onReboot: () -> Unit,
    onRefreshBackups: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAirColors.current
    var askSurvey by remember { mutableStateOf(false) }
    var askReboot by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { onRefreshBackups() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tools.busy?.let { Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("  $it", color = c.muted, fontSize = 14.sp)
        } }

        // ── Обзор эфира
        Section("Обзор эфира")
        AirCard(Modifier.fillMaxWidth()) {
            Text("Какие сети слышит пушка и насколько загружены частоты. Помогает выбрать свободный канал.",
                color = c.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { askSurvey = true }, enabled = !survey.running) {
                    if (survey.running) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  Сканирую…") }
                    else Text(if (survey.entries == null) "Начать обзор" else "Повторить обзор")
                }
                if (survey.canRetry) OutlinedButton(onClick = onSurvey) { Text("Получить результаты") }
            }
            survey.error?.let { Text(it, color = c.fair, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        survey.entries?.let { SurveyResults(it) }

        // ── Резервная копия
        Section("Резервная копия конфигурации")
        AirCard(Modifier.fillMaxWidth()) {
            Text("Сохраняет полные настройки пушки на телефон. Сделайте копию перед любыми изменениями.",
                color = c.muted, fontSize = 14.sp)
            Button(onClick = onSaveConfig, enabled = tools.busy == null, modifier = Modifier.padding(top = 8.dp)) {
                Text("Сохранить конфигурацию")
            }
            Text("Копия содержит пароли и ключ Wi‑Fi — пересылайте её только тем, кому доверяете. " +
                "Восстановление из копии — в следующей версии.", color = c.muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        if (tools.backups.isEmpty()) Label("Копий этой пушки на телефоне пока нет")
        tools.backups.forEach { f ->
            AirCard(Modifier.fillMaxWidth(), padding = 8) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(f.lastModified())),
                            color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(f.name, color = c.muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onExport(f) }) { Icon(Icons.Filled.IosShare, "Экспорт", tint = c.accent) }
                    IconButton(onClick = { askDelete = f }) { Icon(Icons.Filled.Delete, "Удалить", tint = c.muted) }
                }
            }
        }

        // ── Перезагрузка
        Section("Перезагрузка")
        AirCard(Modifier.fillMaxWidth()) {
            Text("Связь с пушкой (и через неё) пропадёт примерно на минуту.", color = c.muted, fontSize = 14.sp)
            Button(onClick = { askReboot = true }, enabled = tools.busy == null, modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.bad, contentColor = c.text)) {
                Text("Перезагрузить пушку")
            }
        }
    }

    if (askSurvey) ConfirmDialog(
        title = "Начать обзор эфира?",
        text = "На несколько секунд связь с пушкой прервётся — и с теми, кто подключён через неё.",
        confirm = "Начать", onConfirm = { askSurvey = false; onSurvey() }, onDismiss = { askSurvey = false },
    )
    if (askReboot) ConfirmDialog(
        title = "Перезагрузить «$deviceName»?",
        text = "Связь пропадёт примерно на минуту. Приложение переподключится само.",
        confirm = "Перезагрузить", onConfirm = { askReboot = false; onReboot() }, onDismiss = { askReboot = false },
    )
    askDelete?.let { f -> ConfirmDialog(
        title = "Удалить копию?", text = "${f.name}\nЭто нельзя отменить.",
        confirm = "Удалить", onConfirm = { askDelete = null; onDelete(f) }, onDismiss = { askDelete = null },
    ) }
}

@Composable
private fun SurveyResults(entries: List<SurveyEntry>) {
    val c = LocalAirColors.current
    if (entries.isEmpty()) { Label("Сетей не найдено"); return }
    AirCard(Modifier.fillMaxWidth()) {
        Text("Загрузка частот", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        channelLoad(entries).forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${l.frequencyMhz} МГц", color = c.text, fontSize = 15.sp, modifier = Modifier.width(96.dp))
                Text("сетей: ${l.count}", color = c.muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("сильнейшая ${l.strongestDbm}", color = signalQuality(l.strongestDbm).color(), fontSize = 15.sp)
            }
        }
    }
    Label("Найдено сетей: ${entries.size}")
    entries.forEach { e ->
        AirCard(Modifier.fillMaxWidth(), padding = 10) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.ssid ?: "(скрытая сеть)", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(
                        e.frequencyMhz?.let { "$it МГц" }, e.channel?.let { "канал $it" }, e.security, e.bssid,
                    ).joinToString(" · "), color = c.muted, fontSize = 13.sp)
                }
                Text(e.signalDbm?.toString() ?: DASH, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = e.signalDbm?.let { signalQuality(it).color() } ?: c.text)
            }
        }
    }
}

@Composable
private fun Section(text: String) =
    Text(text, color = LocalAirColors.current.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp))

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAirColors.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(title, color = c.text) }, text = { Text(text, color = c.muted, fontSize = 15.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
