package com.airmaxfield.align

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class AlignmentTest {
    /** Луч с осью на азимуте 100° и наклоне +2°: −45 dBm в центре, падает квадратично. */
    private fun beam(az: Float, tilt: Float) = (-45 - 0.3 * (az - 100) * (az - 100) - 0.5 * (tilt - 2) * (tilt - 2)).roundToInt()

    private fun sweep(a: AlignmentAssistant, from: Int, to: Int, tilt: Float = 0f, t0: Long = 0): Long {
        var t = t0
        val step = if (to >= from) 1 else -1
        var az = from
        while (true) {
            a.add(AlignSample(t, beam(az.toFloat(), tilt), Orientation(az.toFloat(), tilt)))
            t += 1000
            if (az == to) break
            az += step
        }
        return t
    }

    @Test fun guides_back_to_beam_axis_after_sweep() {
        val a = AlignmentAssistant()
        sweep(a, 85, 115, tilt = 2f)
        val h = a.hint(Orientation(115f, 2f))
        assertEquals(-45, h.peakDbm)
        assertEquals(-15, h.turnRightDeg)        // влево на 15°
        assertTrue(h.message.contains("влево на 15°"))
        val back = a.hint(Orientation(100f, 2f))
        assertEquals(0, back.turnRightDeg)
    }

    @Test fun reports_tilt_too() {
        val a = AlignmentAssistant()
        sweep(a, 90, 110, tilt = 2f)
        val h = a.hint(Orientation(100f, -3f))
        assertEquals(5, h.tiltUpDeg)
        assertTrue(h.message.contains("наклоните вверх на 5°"))
    }

    @Test fun wraps_around_north() {
        assertEquals(20f, AlignmentAssistant.angleDiff(350f, 10f), 0.01f)
        assertEquals(-20f, AlignmentAssistant.angleDiff(10f, 350f), 0.01f)
        assertEquals(0f, AlignmentAssistant.circularMeanDeg(listOf(350f, 10f)) % 360f, 0.01f)
    }

    @Test fun peak_at_edge_of_sweep_is_not_trusted() {
        val a = AlignmentAssistant()
        sweep(a, 80, 98, tilt = 2f) // до оси луча (100°) не дошли
        val h = a.hint(Orientation(98f, 2f))
        assertNull(h.turnRightDeg)
        assertFalse(h.atPeak)
        assertTrue(h.message.contains("влево-вправо"))
    }

    @Test fun at_peak_when_on_axis() {
        val a = AlignmentAssistant()
        val t = sweep(a, 90, 110, tilt = 2f)
        a.add(AlignSample(t, -45, Orientation(100f, 2f)))
        assertTrue(a.hint(Orientation(100f, 2f)).atPeak)
    }

    @Test fun without_compass_hot_and_cold() {
        val a = AlignmentAssistant()
        listOf(-60, -58, -55, -52, -50).forEachIndexed { i, s -> a.add(AlignSample(i * 1000L, s, null)) }
        assertEquals(Trend.BETTER, a.hint(null).trend)
        assertTrue(a.hint(null).message.contains("Лучше"))
        listOf(-53, -56, -58, -60).forEachIndexed { i, s -> a.add(AlignSample(5000L + i * 1000, s, null)) }
        val h = a.hint(null)
        assertEquals(Trend.WORSE, h.trend)
        assertEquals(10, h.belowPeakDb)
        assertTrue(h.message.contains("пик был -50 dBm"))
        assertNull(h.turnRightDeg)
    }

    @Test fun link_budget_for_real_150m_link() {
        assertEquals(91.2, LinkBudget.fsplDb(150.0, 5810.0), 0.1)
        // LiteBeam 5AC с тарелками 23 dBi, у второй стороны 10 dBm
        val withDishes = LinkBudget.expectedDbm(LinkBudget.Input(10, 23, 23, 150, 5810))!!
        assertEquals(-35, withDishes)
        assertTrue(LinkBudget.verdict(-45, withDishes) is LinkBudget.Verdict.Below)
        // а в настройках стоит «Feed only» 6 dBi — фактический −45 заметно лучше расчёта: подсказка про антенну
        val asConfigured = LinkBudget.expectedDbm(LinkBudget.Input(10, 6, 6, 150, 5810))!!
        assertTrue(LinkBudget.verdict(-45, asConfigured) is LinkBudget.Verdict.AboveExpected)
        assertNull(LinkBudget.expectedDbm(LinkBudget.Input(10, 6, 6, 0, 5810)))
    }
}
