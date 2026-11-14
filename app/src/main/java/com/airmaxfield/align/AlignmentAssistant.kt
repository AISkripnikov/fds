package com.airmaxfield.align

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Куда смотрит антенна (телефон закреплён на ней камерой в сторону второй пушки), градусы. */
data class Orientation(val azimuthDeg: Float, val tiltUpDeg: Float)

data class AlignSample(val timeMs: Long, val signalDbm: Int, val orientation: Orientation?)

enum class Trend { BETTER, WORSE, SAME }

/**
 * Подсказка для юстировки.
 * [turnRightDeg] > 0 — повернуть вправо (по часовой, если смотреть сверху), < 0 — влево.
 * [tiltUpDeg] > 0 — наклонить вверх, < 0 — вниз. null — направление неизвестно (нет компаса или мало данных).
 */
data class AlignHint(
    val currentDbm: Int?,
    val peakDbm: Int?,
    val belowPeakDb: Int?,
    val trend: Trend?,
    val turnRightDeg: Int?,
    val tiltUpDeg: Int?,
    val message: String,
    val atPeak: Boolean,
)

/**
 * Помощник юстировки: запоминает сигнал в каждом положении антенны и показывает, куда вернуться к максимуму.
 *
 * Без датчика ориентации работает «горячо/холодно»: пик, насколько ниже пика сейчас и тренд.
 * С датчиком: центр «плато» максимума (все положения в пределах [PLATEAU_DB] от пика) — это ось луча,
 * и разница углов до него.
 */
class AlignmentAssistant(private val trendWindowMs: Long = 3_000) {
    private val samples = ArrayDeque<AlignSample>()

    fun reset() = samples.clear()

    fun add(s: AlignSample) {
        samples.addLast(s)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    val sampleCount: Int get() = samples.size

    private fun trend(): Trend? {
        val last = samples.lastOrNull() ?: return null
        val older = samples.filter { it.timeMs <= last.timeMs - trendWindowMs }.takeLast(3)
        if (older.isEmpty()) return null
        val before = older.map { it.signalDbm }.average()
        val d = last.signalDbm - before
        return when {
            d >= 2 -> Trend.BETTER
            d <= -2 -> Trend.WORSE
            else -> Trend.SAME
        }
    }

    /** Центр плато максимума по положениям с известной ориентацией. null — мало данных или нет датчика. */
    private fun peakOrientation(): Orientation? {
        val oriented = samples.filter { it.orientation != null }
        if (oriented.size < 5) return null
        val max = oriented.maxOf { it.signalDbm }
        val plateau = oriented.filter { it.signalDbm >= max - PLATEAU_DB }.map { it.orientation!! }
        val az = circularMeanDeg(plateau.map { it.azimuthDeg })
        val tilt = plateau.map { it.tiltUpDeg }.average().toFloat()
        return Orientation(az, tilt)
    }

    /** Антенну проводили достаточно широко, чтобы пик был найден, а не «где-то дальше». */
    private fun sweptAcross(peakAz: Float): Boolean {
        val az = samples.mapNotNull { it.orientation?.azimuthDeg }
        if (az.size < 5) return false
        val rel = az.map { angleDiff(peakAz, it) } // < 0 — левее пика, > 0 — правее
        return rel.minOrNull()!! <= -SWEEP_MARGIN_DEG && rel.maxOrNull()!! >= SWEEP_MARGIN_DEG
    }

    fun hint(current: Orientation?): AlignHint {
        val last = samples.lastOrNull()
            ?: return AlignHint(null, null, null, null, null, null, "Жду сигнал от пушки…", false)
        val peak = samples.maxOf { it.signalDbm }
        val below = peak - last.signalDbm
        val trend = trend()
        val po = peakOrientation()

        if (po != null && current != null) {
            val turn = angleDiff(current.azimuthDeg, po.azimuthDeg).roundToInt()
            val tilt = (po.tiltUpDeg - current.tiltUpDeg).roundToInt()
            val found = sweptAcross(po.azimuthDeg)
            val atPeak = found && abs(turn) <= ON_TARGET_DEG && abs(tilt) <= ON_TARGET_DEG && below <= 2
            val msg = when {
                !found -> "Медленно ведите антенну влево-вправо (≈1° в секунду), пока сигнал не начнёт падать с обеих сторон"
                atPeak -> "На максимуме. Затягивайте крепление, следя, чтобы сигнал не ушёл"
                abs(turn) > ON_TARGET_DEG -> (if (turn > 0) "Поверните вправо на ${turn}°" else "Поверните влево на ${-turn}°") +
                    tiltText(tilt)
                else -> "По горизонтали точно." + tiltText(tilt).ifEmpty { " Сигнал ниже пика на $below dB — подождите пару секунд" }
            }
            return AlignHint(last.signalDbm, peak, below, trend, if (found) turn else null, if (found) tilt else null, msg, atPeak)
        }

        val atPeak = below <= 1 && samples.size >= 5
        val msg = when {
            samples.size < 5 -> "Медленно поворачивайте антенну и следите за цифрой"
            trend == Trend.BETTER -> "Лучше — продолжайте в ту же сторону"
            atPeak -> "Вы на максимуме из найденных. Проверьте чуть дальше в обе стороны"
            trend == Trend.WORSE -> "Хуже — вернитесь назад: пик был $peak dBm (ниже на $below dB)"
            else -> "Ниже пика на $below dB — найдите положение, где было $peak dBm"
        }
        return AlignHint(last.signalDbm, peak, below, trend, null, null, msg, atPeak)
    }

    private fun tiltText(tilt: Int) = when {
        tilt > ON_TARGET_DEG -> " и наклоните вверх на ${tilt}°"
        tilt < -ON_TARGET_DEG -> " и наклоните вниз на ${-tilt}°"
        else -> ""
    }

    companion object {
        const val MAX_SAMPLES = 600
        const val PLATEAU_DB = 1
        const val ON_TARGET_DEG = 1
        /** Насколько по обе стороны от пика нужно пройти, чтобы считать пик найденным. */
        const val SWEEP_MARGIN_DEG = 3f

        /** Кратчайший угол от [from] до [to], −180…180 (плюс — по часовой). */
        fun angleDiff(from: Float, to: Float): Float {
            var d = (to - from) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }

        fun circularMeanDeg(a: List<Float>): Float {
            val s = a.sumOf { sin(Math.toRadians(it.toDouble())) }
            val c = a.sumOf { cos(Math.toRadians(it.toDouble())) }
            val m = Math.toDegrees(atan2(s, c)).toFloat()
            return if (m < 0) m + 360f else m
        }
    }
}
