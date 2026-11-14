package com.airmaxfield.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.airos.DeviceStatus
import com.airmaxfield.ui.theme.BigSignal
import com.airmaxfield.ui.theme.LocalAirColors
import com.airmaxfield.ui.theme.Quality
import com.airmaxfield.ui.theme.TileValue
import com.airmaxfield.ui.theme.color
import com.airmaxfield.ui.theme.signalQuality
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

const val DASH = "—"

@Composable
fun AirCard(modifier: Modifier = Modifier, padding: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAirColors.current
    Column(
        modifier
            .background(c.card, RoundedCornerShape(12.dp))
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .padding(padding.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, color = LocalAirColors.current.muted, fontSize = 13.sp)

/** Строка «подпись — значение». */
@Composable
fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    val c = LocalAirColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.muted, fontSize = 15.sp)
        Text(value, color = valueColor ?: c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Главная карточка сигнала: крупная цифра, шкала, цепи, удалённый сигнал. */
@Composable
fun SignalCard(s: DeviceStatus?, modifier: Modifier = Modifier) {
    val c = LocalAirColors.current
    val sig = s?.signalDbm
    AirCard(modifier.fillMaxWidth()) {
        Label("Сигнал")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(sig?.toString() ?: DASH, style = BigSignal, color = signalQuality(sig).color().takeIf { sig != null } ?: c.text)
            Spacer(Modifier.width(6.dp))
            Text("dBm", color = c.muted, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        SignalScale(sig)
        Spacer(Modifier.height(8.dp))
        val chains = s?.chainSignalDbm.orEmpty()
        InfoRow("Цепь 0 / Цепь 1", if (chains.isEmpty()) DASH else chains.joinToString(" / "))
        InfoRow("Удалённый сигнал", s?.remoteSignalDbm?.let { "$it dBm" } ?: DASH)
    }
}

/** Градиентная шкала −90…−40 dBm с маркером. */
@Composable
fun SignalScale(sig: Int?) {
    val c = LocalAirColors.current
    Canvas(Modifier.fillMaxWidth().height(18.dp).padding(top = 8.dp)) {
        val h = 10.dp.toPx()
        drawRoundRect(
            brush = Brush.horizontalGradient(0f to c.bad, 0.45f to c.fair, 0.75f to c.good),
            size = Size(size.width, h), cornerRadius = CornerRadius(h / 2),
        )
        if (sig != null) {
            val x = ((sig + 90f) / 50f).coerceIn(0f, 1f) * size.width
            drawRoundRect(c.text, topLeft = Offset(x - 2.dp.toPx(), -4.dp.toPx()),
                size = Size(4.dp.toPx(), h + 8.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
fun MetricTile(label: String, value: String?, unit: String, quality: Quality, modifier: Modifier = Modifier) {
    val c = LocalAirColors.current
    AirCard(modifier, padding = 10) {
        Label(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: DASH, style = TileValue, color = if (value == null) c.text else quality.color())
            if (value != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** График сигнала за последние 60 опросов. */
@Composable
fun SignalGraph(history: List<Int>, modifier: Modifier = Modifier) {
    val c = LocalAirColors.current
    AirCard(modifier.fillMaxWidth(), padding = 10) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("Сигнал за 60 с")
            if (history.isNotEmpty()) Label("мин ${history.min()} · макс ${history.max()}")
        }
        Canvas(Modifier.fillMaxWidth().height(64.dp).padding(top = 6.dp)) {
            if (history.size < 2) return@Canvas
            val lo = (history.min() - 3).toFloat()
            val hi = (history.max() + 3).toFloat()
            val step = size.width / 59f
            val off = 60 - history.size
            val path = Path()
            history.forEachIndexed { i, v ->
                val x = (i + off) * step
                val y = size.height - (v - lo) / (hi - lo) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c.good, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** Точка состояния связи. */
@Composable
fun StatusDot(ok: Boolean) {
    val c = LocalAirColors.current
    Box(Modifier.size(10.dp).background(if (ok) c.good else c.fair, CircleShape))
}

/** Поле пароля с «глазом»: можно проверить, что вписано. */
@androidx.compose.runtime.Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var shown by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.OutlinedTextField(
        value, onValueChange, label = { androidx.compose.material3.Text(label) }, singleLine = true, modifier = modifier,
        visualTransformation = if (shown) androidx.compose.ui.text.input.VisualTransformation.None
            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        trailingIcon = {
            androidx.compose.material3.IconButton(onClick = { shown = !shown }) {
                androidx.compose.material3.Icon(
                    if (shown) androidx.compose.material.icons.Icons.Filled.VisibilityOff
                    else androidx.compose.material.icons.Icons.Filled.Visibility,
                    contentDescription = if (shown) "Скрыть пароль" else "Показать пароль",
                )
            }
        },
    )
}
