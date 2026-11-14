package com.airmaxfield.apply

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.AirOsError
import com.airmaxfield.airos.DemoClient
import com.airmaxfield.airos.DeviceStatus
import com.airmaxfield.airos.SystemConfig
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
class SafeApplyTest {
    private fun TestScope.demo() = DemoClient(now = { testScheduler.currentTime })
    private fun TestScope.safe(c: AirOsClient, backups: MutableList<String> = mutableListOf()) =
        SafeApply(c, { backups += it }, backgroundScope, clock = { testScheduler.currentTime })

    private suspend fun changed(c: AirOsClient) = SystemConfig.parse(c.downloadConfig()).with("radio.1.freq", "5200").raw

    @Test fun backup_is_taken_before_upload() = runTest {
        val d = demo(); val backups = mutableListOf<String>()
        val orig = d.downloadConfig()
        val s = safe(d, backups)
        assertTrue(s.prepare(changed(d)))
        assertEquals(listOf(orig), backups)
        assertTrue(s.state.value is ApplyState.Testing)
    }

    @Test fun auto_rollback_on_timeout() = runTest {
        val d = demo(); val s = safe(d)
        val orig = d.downloadConfig()
        s.prepare(changed(d))
        advanceTimeBy(100_000); runCurrent()
        val t = s.state.value as ApplyState.Testing
        assertEquals(80, t.remainingSec); assertTrue(t.reachable)
        advanceTimeBy(81_000); runCurrent()
        assertEquals(ApplyState.RolledBack(SafeApply.EXPIRED), s.state.value)
        assertEquals(orig, d.downloadConfig())
    }

    @Test fun confirm_saves_when_reachable() = runTest {
        val d = demo(); val s = safe(d)
        val next = changed(d)
        s.prepare(next); runCurrent()
        assertTrue(s.confirmNow())
        assertEquals(ApplyState.Confirmed, s.state.value)
        advanceTimeBy(1_000_000); runCurrent()
        assertEquals(ApplyState.Confirmed, s.state.value)
        assertEquals(next, d.downloadConfig())
    }

    @Test fun confirm_unavailable_without_link() = runTest {
        var up = false
        val d = demo()
        val c = object : AirOsClient by d {
            override suspend fun status(): DeviceStatus = if (up) d.status() else throw AirOsError.Unreachable(null)
        }
        val s = safe(c)
        s.prepare(changed(d)); runCurrent()
        assertFalse((s.state.value as ApplyState.Testing).reachable)
        assertFalse(s.confirmNow())
        assertTrue(d.inTest)
        up = true; advanceTimeBy(2_100); runCurrent()
        assertTrue((s.state.value as ApplyState.Testing).reachable)
        assertTrue(s.confirmNow())
    }

    @Test fun manual_rollback() = runTest {
        val d = demo(); val s = safe(d)
        s.prepare(changed(d)); runCurrent()
        s.rollbackNow()
        assertEquals(ApplyState.RolledBack(SafeApply.MANUAL), s.state.value)
        assertFalse(d.inTest)
    }

    @Test fun rollback_that_does_not_reach_waits_for_timer() = runTest {
        val d = demo()
        val c = object : AirOsClient by d {
            override suspend fun cancelTest() = throw AirOsError.Unreachable(null)
        }
        val s = safe(c)
        s.prepare(changed(d)); runCurrent()
        s.rollbackNow()
        val t = s.state.value as ApplyState.Testing
        assertTrue(t.error!!.contains("по таймеру"))
        advanceTimeBy(181_000); runCurrent()
        assertTrue(s.state.value is ApplyState.RolledBack)
    }

    @Test fun upload_error_fails_without_test() = runTest {
        val d = demo()
        val c = object : AirOsClient by d {
            override suspend fun uploadConfig(cfg: String, testSeconds: Int) = throw AirOsError.Protocol("HTTP 500")
        }
        val s = safe(c)
        assertFalse(s.prepare(changed(d)))
        assertTrue(s.state.value is ApplyState.Failed)
        assertFalse(d.inTest)
    }

    @Test fun backup_failure_blocks_upload() = runTest {
        val d = demo()
        val s = SafeApply(d, { error("диск полон") }, backgroundScope, clock = { testScheduler.currentTime })
        assertFalse(s.prepare(changed(d)))
        assertTrue((s.state.value as ApplyState.Failed).message.contains("резервную копию"))
        assertFalse(d.inTest)
    }

    @Test fun no_test_mode_is_refused() = runTest {
        val d = demo()
        val c = object : AirOsClient by d { override val hasTestMode get() = false }
        val s = safe(c)
        assertFalse(s.prepare(changed(d)))
        assertTrue(s.state.value is ApplyState.Failed)
    }

    @Test fun probes_and_confirms_via_new_target_after_ip_change() = runTest {
        val old = demo()
        val moved = object : AirOsClient by old {
            override suspend fun status(): DeviceStatus = throw AirOsError.Unreachable(null) // старый адрес пропал
        }
        var confirmedVia = ""
        val fresh = object : AirOsClient by old {
            override suspend fun confirmTest() { confirmedVia = "new"; old.confirmTest() }
        }
        val s = SafeApply(moved, {}, backgroundScope, clock = { testScheduler.currentTime }, target = { fresh })
        s.prepare(changed(old)); runCurrent()
        assertTrue(s.confirmNow())
        assertEquals("new", confirmedVia)
    }
}
