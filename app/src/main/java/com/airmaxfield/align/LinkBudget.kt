package com.airmaxfield.align

import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Расчётный сигнал идеально сведённого линка (прямая видимость, без препятствий):
 * мощность второй стороны + усиления антенн − потери в кабелях − затухание в свободном пространстве.
 * Это ориентир, а не обещание: зона Френеля, дождь и неточное расстояние дают ±5 dB.
 */
object LinkBudget {
    /** Затухание в свободном пространстве, dB. */
    fun fsplDb(distanceM: Double, freqMhz: Double): Double =
        20 * log10(distanceM / 1000.0) + 20 * log10(freqMhz) + 32.44

    data class Input(
        val remoteTxDbm: Int,
        val remoteGainDbi: Int,
        val localGainDbi: Int,
        val distanceM: Int,
        val freqMhz: Int,
        val cableLossDb: Int = 0,
    )

    fun expectedDbm(i: Input): Int? {
        if (i.distanceM <= 0 || i.freqMhz <= 0) return null
        return (i.remoteTxDbm + i.remoteGainDbi + i.localGainDbi - i.cableLossDb - fsplDb(i.distanceM.toDouble(), i.freqMhz.toDouble()))
            .roundToInt()
    }

    /** Оценка, насколько хорошо сведено: разница «сейчас − расчёт». */
    sealed interface Verdict {
        /** В пределах 6 dB от расчёта — сведено хорошо. */
        data class Good(val gapDb: Int) : Verdict
        /** Хуже расчёта: есть что доводить (или препятствие на трассе). */
        data class Below(val gapDb: Int) : Verdict
        /** Заметно лучше расчёта — в настройках неверно усиление антенны или расстояние. */
        data class AboveExpected(val gapDb: Int) : Verdict
    }

    fun verdict(actualDbm: Int, expectedDbm: Int): Verdict {
        val gap = actualDbm - expectedDbm
        return when {
            gap > 6 -> Verdict.AboveExpected(gap)
            gap >= -6 -> Verdict.Good(gap)
            else -> Verdict.Below(-gap)
        }
    }
}
