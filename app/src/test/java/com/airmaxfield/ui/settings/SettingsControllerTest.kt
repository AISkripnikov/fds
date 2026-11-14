package com.airmaxfield.ui.settings

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.DemoClient
import com.airmaxfield.apply.ApplyState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsControllerTest {
    @Test fun drafts_diff_and_confirmed_apply() = runTest {
        val d = DemoClient(now = { testScheduler.currentTime })
        val backups = mutableListOf<String>()
        var confirmedHost: String? = "unset"
        val c = SettingsController(d, backgroundScope, host = "192.168.1.20", backup = { backups += it },
            onConfirmed = { confirmedHost = it }, clock = { testScheduler.currentTime })
        c.load(); runCurrent()
        c.setDraft("radio.1.freq", "5200")
        c.setDraft("snmp.location", c.config.value!!["snmp.location"]!!) // то же значение — не черновик
        c.setDraft("users.1.password", "x") // не правится
        c.setDraft("sshd.port", "99999")     // вне диапазона — не принимается
        assertEquals(mapOf("radio.1.freq" to "5200"), c.drafts.value)
        assertEquals(1, c.changes().size)
        assertNull(c.newHost())

        c.applyDrafts(); runCurrent()
        val sa = c.apply.value!!
        assertTrue(sa.state.value is ApplyState.Testing)
        assertEquals(1, backups.size)
        sa.confirmNow(); runCurrent()
        assertTrue(c.drafts.value.isEmpty())
        assertNull(confirmedHost)
        assertEquals("5200", c.config.value!!["radio.1.freq"])
        c.dismissApply()
        assertNull(c.apply.value)
    }

    @Test fun rollback_keeps_drafts_and_reloads() = runTest {
        val d = DemoClient(now = { testScheduler.currentTime })
        val c = SettingsController(d, backgroundScope, backup = {}, clock = { testScheduler.currentTime })
        c.load(); runCurrent()
        c.setDraft("radio.1.freq", "5200")
        c.applyDrafts(); runCurrent()
        advanceTimeBy(181_000); runCurrent()
        assertTrue(c.apply.value!!.state.value is ApplyState.RolledBack)
        assertEquals("5180", c.config.value!!["radio.1.freq"])
        assertEquals(mapOf("radio.1.freq" to "5200"), c.drafts.value)
    }

    @Test fun ip_change_is_detected_and_reported() = runTest {
        val d = DemoClient(now = { testScheduler.currentTime })
        var confirmedHost: String? = null
        var reconnectedTo: String? = null
        val c = SettingsController(d, backgroundScope, host = "192.168.1.20", backup = {},
            reconnect = { h -> { reconnectedTo = h; d } }, onConfirmed = { confirmedHost = it },
            clock = { testScheduler.currentTime })
        c.load(); runCurrent()
        c.setDraft("netconf.1.ip", "192.168.1.30")
        assertEquals("192.168.1.30", c.newHost())
        c.applyDrafts(); runCurrent()
        c.apply.value!!.confirmNow(); runCurrent()
        assertEquals("192.168.1.30", reconnectedTo)
        assertEquals("192.168.1.30", confirmedHost)
    }

    @Test fun no_test_mode_means_read_only() = runTest {
        val d = DemoClient()
        val ro = object : AirOsClient by d { override val hasTestMode get() = false }
        val c = SettingsController(ro, backgroundScope)
        c.load(); runCurrent()
        assertFalse(c.canEdit("snmp.location"))
        c.setDraft("snmp.location", "x")
        assertTrue(c.drafts.value.isEmpty())
    }
}
