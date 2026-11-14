package com.airmaxfield.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.align.AlignSample
import com.airmaxfield.align.AlignmentAssistant
import com.airmaxfield.align.LinkBudget
import com.airmaxfield.align.OrientationSensor
import com.airmaxfield.align.Trend
import com.airmaxfield.ui.components.AirCard
import com.airmaxfield.ui.components.InfoRow
import com.airmaxfield.ui.theme.LocalAirColors
import com.airmaxfield.ui.theme.color
import com.airmaxfield.ui.theme.signalQuality
import kotlin.math.abs

/**
 * Режим юстировки: крупный сигнал, пик, «лучше/хуже» и — если телефон закреплён на антенне —
 * на сколько градусов и куда повернуть, чтобы вернуться к оси луча.
 */
@Composable
fun AlignScreen(ui: StatusUi, toneOn: Boolean, onToneToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAirColors.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val sensor = remember { OrientationSensor(ctx) }
    var useSensor by rememberSaveable { mutableStateOf(sensor.available) }
    val orientation by sensor.orientation.collectAsState()
    val assistant = remember { AlignmentAssistant() }
    var version by remember { mutableIntStateOf(0) }

    // Экран не гаснет, пока сводим антенну; датчик работает только на этом экране.
    DisposableEffect(useSensor) {
        view.keepScreenOn = true
        if (useSensor) sensor.start()
        onDispose { view.keepScreenOn = false; sensor.stop() }
    }
    // Каждый новый успешный опрос — точка «сигнал + положение антенны».
    LaunchedEffect(ui.lastOkAt) {
        val sig = ui.status?.signalDbm ?: return@LaunchedEffect
        assistant.add(AlignSample(System.currentTimeMillis(), sig, if (useSensor) sensor.orientation.value else null))
        version++
    }
    val hint = remember(version, orientation, useSensor) { assistant.hint(if (useSensor) orientation else null) }
    val s = ui.status

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Сигнал и пик
        AirCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(hint.currentDbm?.toString() ?: "—", fontSize = 84.sp, fontWeight = FontWeight.Bold,
                    color = signalQuality(hint.currentDbm).color())
                Text(" dBm", color = c.muted, fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(when (hint.trend) { Trend.BETTER -> "▲ лучше"; Trend.WORSE -> "▼ хуже"; Trend.SAME -> "= ровно"; null -> "" },
                        color = when (hint.trend) { Trend.BETTER -> c.good; Trend.WORSE -> c.bad; else -> c.muted },
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            hint.peakDbm?.let { p ->
                Text("Пик $p dBm" + (hint.belowPeakDb?.takeIf { it > 0 }?.let { " · сейчас ниже на $it dB" } ?: " · вы на пике"),
                    color = c.text, fontSize = 17.sp)
            }
        }

        // Куда крутить
        AirCard(Modifier.fillMaxWidth()) {
            val turn = hint.turnRightDeg
            val tilt = hint.tiltUpDeg
            if (turn != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    DirectionBig(
                        when { abs(turn) <= AlignmentAssistant.ON_TARGET_DEG -> "●"; turn > 0 -> "→"; else -> "←" },
                        if (abs(turn) <= AlignmentAssistant.ON_TARGET_DEG) "по горизонтали" else "${abs(turn)}°",
                        abs(turn) <= AlignmentAssistant.ON_TARGET_DEG,
                    )
                    if (tilt != null) DirectionBig(
                        when { abs(tilt) <= AlignmentAssistant.ON_TARGET_DEG -> "●"; tilt > 0 -> "↑"; else -> "↓" },
                        if (abs(tilt) <= AlignmentAssistant.ON_TARGET_DEG) "по вертикали" else "${abs(tilt)}°",
                        abs(tilt) <= AlignmentAssistant.ON_TARGET_DEG,
                    )
                }
            }
            Text(hint.message, color = if (hint.atPeak) c.good else c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }

        // Управление
        AirCard(Modifier.fillMaxWidth()) {
            if (sensor.available) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Телефон закреплён на антенне", color = c.text, fontSize = 16.sp)
                        Text("Вертикально, экраном к себе, камерой в сторону второй пушки. Тогда приложение покажет, " +
                            "на сколько градусов и куда повернуть.", color = c.muted, fontSize = 13.sp)
                    }
                    Switch(checked = useSensor, onCheckedChange = { useSensor = it; assistant.reset(); version++ })
                }
            } else {
                Text("В телефоне нет датчика поворота — подсказки только «лучше/хуже» и пик.", color = c.muted, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("Звук юстировки", color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(checked = toneOn, onCheckedChange = onToneToggle)
            }
            OutlinedButton(onClick = { assistant.reset(); version++ }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Начать заново (сбросить пик)")
            }
        }

        // Цепи и вторая сторона
        if (s != null) AirCard(Modifier.fillMaxWidth()) {
            if (s.chainSignalDbm.size >= 2) {
                InfoRow("Цепь 0 / Цепь 1", s.chainSignalDbm.joinToString(" / ") + " dBm")
                val imb = abs(s.chainSignalDbm[0] - s.chainSignalDbm[1])
                if (imb >= 5) Text("Цепи расходятся на $imb dB — проверьте наклон и поворот антенны вокруг оси (поляризацию), " +
                    "а также разъёмы и препятствия.", color = c.fair, fontSize = 13.sp)
            }
            s.remote?.signalDbm?.let { InfoRow("Сигнал на второй стороне", "$it dBm", signalQuality(it).color()) }
            s.snrDb?.let { InfoRow("SNR", "$it dB") }
        }

        BudgetCard(s)

        AirCard(Modifier.fillMaxWidth()) {
            Text("Как сводить", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "1. Грубо наведите по карте или компасу.\n" +
                    "2. Медленно (≈1° в секунду) поверните антенну по горизонтали в одну сторону, пока сигнал не упадёт, " +
                    "затем в другую — тоже до падения.\n" +
                    "3. Следуйте стрелке: она ведёт к центру максимума, а не к его краю.\n" +
                    "4. То же по вертикали.\n" +
                    "5. Затяните крепление, следя, чтобы сигнал не ушёл. Затем то же на второй стороне.",
                color = c.muted, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun DirectionBig(arrow: String, label: String, ok: Boolean) {
    val c = LocalAirColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(arrow, fontSize = 72.sp, color = if (ok) c.good else c.accent, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 22.sp, color = c.text, fontWeight = FontWeight.SemiBold)
    }
}

/** Расчётный сигнал идеально сведённого линка и что значит разница с текущим. */
@Composable
private fun BudgetCard(s: com.airmaxfield.airos.DeviceStatus?) {
    val c = LocalAirColors.current
    val r = s?.remote
    var distanceText by rememberSaveable { mutableStateOf("") }
    val distance = distanceText.toIntOrNull() ?: s?.distanceM
    val input = if (s?.frequencyMhz != null && r?.txPowerDbm != null && distance != null) LinkBudget.Input(
        remoteTxDbm = r.txPowerDbm, remoteGainDbi = r.antennaGainDbi ?: 0, localGainDbi = s.antennaGainDbi ?: 0,
        distanceM = distance, freqMhz = s.frequencyMhz, cableLossDb = r.cableLossDb ?: 0,
    ) else null
    val expected = input?.let { LinkBudget.expectedDbm(it) }
    AirCard(Modifier.fillMaxWidth()) {
        Text("Сколько можно выжать", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (input == null || expected == null) {
            Text("Нет данных для расчёта (нужны частота, мощность второй стороны и расстояние).", color = c.muted, fontSize = 13.sp)
        } else {
            InfoRow("Расчётный сигнал", "≈ $expected dBm")
            Text("Расстояние ${input.distanceM} м, ${input.freqMhz} МГц, мощность второй ${input.remoteTxDbm} dBm, " +
                "антенны ${input.localGainDbi} + ${input.remoteGainDbi} dBi (как указано в настройках пушек).",
                color = c.muted, fontSize = 13.sp)
            s?.signalDbm?.let { now ->
                when (val v = LinkBudget.verdict(now, expected)) {
                    is LinkBudget.Verdict.Good -> Text("Сигнал в пределах ${abs(v.gapDb)} dB от расчёта — сведено хорошо.",
                        color = c.good, fontSize = 14.sp)
                    is LinkBudget.Verdict.Below -> Text("Ниже расчёта на ${v.gapDb} dB — есть что доводить " +
                        "(или на трассе препятствие: деревья, крыши в зоне Френеля).", color = c.fair, fontSize = 14.sp)
                    is LinkBudget.Verdict.AboveExpected -> Text("Сигнал лучше расчёта на ${v.gapDb} dB — значит, в настройках " +
                        "неверно указано усиление антенны (сейчас ${input.localGainDbi} / ${input.remoteGainDbi} dBi) или расстояние. " +
                        "Для LiteBeam 5AC с тарелкой выберите антенну 23 dBi, а не «Feed only». Расчёт тогда станет точным.",
                        color = c.fair, fontSize = 14.sp)
                }
            }
            OutlinedTextField(distanceText, { v -> distanceText = v.filter { it.isDigit() }.take(6) },
                label = { Text("Точное расстояние, м (если знаете)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Text("Пушка определяет расстояние приблизительно — по карте точнее.", color = c.muted, fontSize = 12.sp)
        }
    }
}
