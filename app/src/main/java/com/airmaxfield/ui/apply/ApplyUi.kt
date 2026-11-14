package com.airmaxfield.ui.apply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.airmaxfield.airos.CfgChange
import com.airmaxfield.airos.DangerousKeys
import com.airmaxfield.airos.SystemConfig
import com.airmaxfield.apply.ApplyState
import com.airmaxfield.help.HelpContent
import com.airmaxfield.ui.components.StatusDot
import com.airmaxfield.ui.theme.LocalAirColors

fun shownValue(key: String, v: String?): String = when {
    v == null -> "—"
    SystemConfig.isSensitive(key) -> "••••••"
    v.isEmpty() -> "(пусто)"
    else -> v
}

/** Сводка «было → стало» перед записью. */
@Composable
fun ChangesDialog(
    title: String,
    changes: List<CfgChange>,
    warnings: List<String>,
    extra: String?,
    testSeconds: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAirColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, color = c.text) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (ch in changes) {
                    val danger = DangerousKeys.isDangerous(ch.key)
                    Column {
                        Text((if (danger) "⚠ " else "") + (HelpContent.forKey(ch.key)?.title ?: ch.key),
                            color = if (danger) c.fair else c.muted, fontSize = 13.sp)
                        Text("${shownValue(ch.key, ch.old)}  →  ${shownValue(ch.key, ch.new)}",
                            color = c.text, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    }
                }
                if (warnings.isNotEmpty() || extra != null) {
                    Text("Внимание", color = c.bad, fontWeight = FontWeight.SemiBold)
                    for (w in warnings) Text("• $w", color = c.text, fontSize = 14.sp)
                    extra?.let { Text("• $it", color = c.text, fontSize = 14.sp) }
                }
                Text(
                    "Перед записью сохранится резервная копия. Пушка применит изменения в тест-режиме на $testSeconds с: " +
                        "если не нажать «Сохранить навсегда», она сама вернёт прежние настройки.",
                    color = c.muted, fontSize = 13.sp,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Применить с тестом") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Одна строка экрана записи: пушка и её состояние. */
data class ApplyRow(val label: String, val state: ApplyState)

/**
 * Экран записи с обратным отсчётом. Не закрывается, пока запись не закончилась.
 * «Сохранить» активна только когда [canConfirm] (все пушки снова на связи).
 */
@Composable
fun ApplyProgressDialog(
    rows: List<ApplyRow>,
    canConfirm: Boolean,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onRollback: () -> Unit,
    onClose: () -> Unit,
    confirmedText: String = "Изменения сохранены навсегда.",
    note: String? = null,
    canRollback: Boolean = true,
) {
    val c = LocalAirColors.current
    val finished = rows.all { it.state.finished }
    val testing = rows.any { it.state is ApplyState.Testing || it.state is ApplyState.Confirming }
    AlertDialog(
        onDismissRequest = { if (finished) onClose() },
        properties = DialogProperties(dismissOnBackPress = finished, dismissOnClickOutside = false),
        containerColor = c.card,
        title = {
            Text(
                when {
                    rows.all { it.state is ApplyState.Confirmed } -> "Сохранено"
                    rows.any { it.state is ApplyState.RollingBack } -> "Откат…"
                    finished -> "Запись завершена"
                    testing -> "Тест-режим"
                    else -> "Запись…"
                },
                color = c.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (r in rows) RowState(r)
                note?.let { Text(it, color = c.bad, fontSize = 14.sp) }
                if (rows.all { it.state is ApplyState.Confirmed }) Text(confirmedText, color = c.good)
                if (testing && !canConfirm) {
                    Text("Ждём, пока пушка снова ответит. Если связь не вернётся — ничего не делайте: " +
                        "пушка откатится сама по таймеру.", color = c.muted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (finished) {
                Button(onClick = onClose) { Text("Готово") }
            } else if (testing) {
                Button(onClick = onConfirm, enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = c.good)) { Text(confirmLabel) }
            }
        },
        dismissButton = {
            if (testing && canRollback) OutlinedButton(onClick = onRollback) { Text("Откатить сейчас") }
        },
    )
}

@Composable
private fun RowState(r: ApplyRow) {
    val c = LocalAirColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (r.label.isNotEmpty()) Text(r.label, color = c.muted, fontSize = 13.sp)
        when (val s = r.state) {
            ApplyState.Idle -> Text("Ожидание…", color = c.muted)
            ApplyState.BackingUp -> Busy("Сохраняю резервную копию…")
            ApplyState.Uploading -> Busy("Загружаю конфигурацию…")
            ApplyState.Confirming -> Busy("Сохраняю навсегда…")
            is ApplyState.RollingBack -> {
                Busy("Отправляю команду отката…")
                Text("Автооткат в любом случае через ${s.remainingSec} с", color = c.muted, fontSize = 13.sp)
            }
            is ApplyState.Testing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(s.reachable)
                    Text(if (s.reachable) "Пушка на связи" else "Нет связи с пушкой", color = c.text)
                }
                Text("Автооткат через ${s.remainingSec} с", color = if (s.remainingSec < 30) c.bad else c.text,
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { if (s.totalSec > 0) s.remainingSec / s.totalSec.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                s.error?.let { Text(it, color = c.bad, fontSize = 13.sp) }
            }
            ApplyState.Confirmed -> Text("✓ Сохранено", color = c.good)
            is ApplyState.RolledBack -> Text("↺ ${s.reason}", color = c.fair)
            is ApplyState.Failed -> Text("✕ ${s.message}", color = c.bad)
        }
    }
}

@Composable
private fun Busy(text: String) {
    val c = LocalAirColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 4.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, color = c.text)
    }
}
