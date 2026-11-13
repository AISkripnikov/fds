package com.airmaxfield.ui.status

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.AirOsError
import com.airmaxfield.airos.AirOsVersion
import com.airmaxfield.airos.DemoClient
import com.airmaxfield.airos.DeviceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatusPollerTest {
    /** Отдаёт заранее заданную последовательность результатов. */
    private class Scripted(private val steps: MutableList<() -> DeviceStatus>) : AirOsClient {
        override val version = AirOsVersion.V8
        var calls = 0
        override suspend fun login() {}
        override suspend fun status(): DeviceStatus {
            calls++
            return (if (steps.size > 1) steps.removeAt(0) else steps[0])()
        }
        override suspend fun rawDump() = emptyMap<String, String>()
    }

    private fun ok(sig: Int) = { DeviceStatus(AirOsVersion.V8, signalDbm = sig) }

    private fun TestScope.poller(c: AirOsClient) =
        StatusPoller(c, backgroundScope, periodMs = 1000, clock = { testScheduler.currentTime })

    @Test fun history_keeps_last_60_points() = runTest {
        var n = 0
        val c = Scripted(mutableListOf({ DeviceStatus(AirOsVersion.V8, signalDbm = -(n++)) }))
        val p = poller(c); p.start()
        runCurrent(); repeat(69) { advanceTimeBy(1000); runCurrent() }
        val s = p.state.value
        assertEquals(70, c.calls)
        assertEquals(60, s.history.size)
        assertEquals(-69, s.history.last())
    }

    @Test fun keeps_last_status_on_failure() = runTest {
        val fail = { throw AirOsError.Unreachable(null) }
        val c = Scripted(mutableListOf(ok(-60), fail, fail, fail))
        val p = poller(c); p.start()
        runCurrent(); repeat(3) { advanceTimeBy(1000); runCurrent() }
        val s = p.state.value
        assertEquals(-60, s.status!!.signalDbm)
        assertEquals(3, s.secondsSinceUpdate)
        assertTrue(s.error is AirOsError.Unreachable)
        assertFalse(s.stopped)
    }

    @Test fun recovers_after_failure() = runTest {
        val fail = { throw AirOsError.Unreachable(null) }
        val c = Scripted(mutableListOf(ok(-60), fail, ok(-55)))
        val p = poller(c); p.start()
        runCurrent(); repeat(2) { advanceTimeBy(1000); runCurrent() }
        val s = p.state.value
        assertEquals(-55, s.status!!.signalDbm); assertEquals(0, s.secondsSinceUpdate); assertEquals(null, s.error)
    }

    @Test fun stops_on_bad_credentials() = runTest {
        val c = Scripted(mutableListOf({ throw AirOsError.BadCredentials() }))
        val p = poller(c); p.start()
        runCurrent(); repeat(5) { advanceTimeBy(1000); runCurrent() }
        assertTrue(p.state.value.stopped)
        assertEquals(1, c.calls)
    }

    @Test fun demo_client_signal_varies_in_range() = runTest {
        val d = DemoClient()
        val values = (1..100).map { d.status().signalDbm!! }
        assertTrue(values.all { it in -72..-55 })
        assertTrue(values.toSet().size > 1)
    }
}
