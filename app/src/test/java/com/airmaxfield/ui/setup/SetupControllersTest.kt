package com.airmaxfield.ui.setup

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.DemoClient
import com.airmaxfield.airos.SystemConfig
import com.airmaxfield.apply.ApplyState
import com.airmaxfield.apply.BridgeProfile
import com.airmaxfield.data.SavedDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupControllersTest {
    private val profile = BridgeProfile(
        name = "Sever", ssid = "SEVER", psk = "secret-key-1", countryCode = "643", ipA = "192.168.1.21",
        ipB = "192.168.1.22", user = "admin", pass = "StrongPass1",
    )

    @Test fun first_login_validates_and_reconnects_with_new_password() = runTest {
        val d = DemoClient(now = { testScheduler.currentTime })
        val connects = mutableListOf<String>()
        var done: Pair<String, String>? = null
        val c = FirstLoginController(d, "192.168.1.20", { h, u, p -> connects += "$h $u $p"; d }, {}, backgroundScope,
            onDone = { u, p -> done = u to p }, clock = { testScheduler.currentTime })
        c.submit("admin", "short", "short", "643", false)
        assertTrue(c.errors.value.size >= 2)
        assertNull(c.apply.value)

        c.submit("admin", "StrongPass1", "StrongPass1", "643", true); runCurrent()
        val sa = c.apply.value!!
        assertTrue(sa.state.value is ApplyState.Testing)
        assertEquals(listOf("192.168.1.20 admin StrongPass1"), connects)
        sa.confirmNow(); runCurrent()
        assertEquals("admin" to "StrongPass1", done)
        assertEquals("admin", SystemConfig.parse(d.downloadConfig())["users.1.name"])
    }

    @Test fun wizard_configures_a_then_b_and_saves_pair() = runTest {
        val a = DemoClient(now = { testScheduler.currentTime })
        val b = DemoClient(now = { testScheduler.currentTime })
        var current: AirOsClient = a
        val saved = mutableListOf<SavedDevice>()
        val w = BridgeWizardController(backgroundScope, { _, _, _ -> current }, { _, _ -> }, { saved += it }, {},
            clock = { testScheduler.currentTime })

        w.setProfile(profile.copy(psk = "x"))
        assertEquals(WizardStep.PROFILE, w.ui.value.step)
        w.setProfile(profile)
        assertEquals(WizardStep.SIDE_A, w.ui.value.step)

        w.applySide("192.168.1.20", "ubnt", "ubnt"); runCurrent()
        w.ui.value.apply!!.confirmNow(); runCurrent()
        w.dismissApply()
        assertEquals(WizardStep.SIDE_B, w.ui.value.step)
        assertEquals("master", SystemConfig.parse(a.downloadConfig())["radio.1.mode"])

        current = b
        w.applySide("192.168.1.20", "ubnt", "ubnt"); runCurrent()
        w.ui.value.apply!!.confirmNow(); runCurrent()
        w.dismissApply()
        assertEquals(WizardStep.DONE, w.ui.value.step)
        assertEquals("managed", SystemConfig.parse(b.downloadConfig())["radio.1.mode"])

        assertEquals(listOf("192.168.1.21", "192.168.1.22"), saved.map { it.host })
        assertEquals(1, saved.map { it.pairId }.distinct().size)
        assertEquals(listOf("Sever A", "Sever B"), saved.map { it.name })
    }

    @Test fun wizard_stays_on_side_after_rollback() = runTest {
        val a = DemoClient(now = { testScheduler.currentTime })
        val saved = mutableListOf<SavedDevice>()
        val w = BridgeWizardController(backgroundScope, { _, _, _ -> a }, { _, _ -> }, { saved += it }, {},
            clock = { testScheduler.currentTime })
        w.setProfile(profile)
        w.applySide("192.168.1.20", "ubnt", "ubnt"); runCurrent()
        advanceTimeBy(181_000); runCurrent()
        w.dismissApply()
        assertEquals(WizardStep.SIDE_A, w.ui.value.step)
        assertTrue(saved.isEmpty())
    }
}
