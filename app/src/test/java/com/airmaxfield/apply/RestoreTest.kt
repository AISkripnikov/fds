package com.airmaxfield.apply

import com.airmaxfield.airos.DemoClient
import com.airmaxfield.airos.SystemConfig
import com.airmaxfield.ui.settings.SettingsController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreTest {
    private val current = SystemConfig.parse("system.hostname=sever-a\nnetconf.1.ip=192.168.1.20\nradio.1.freq=5500\n" +
        "radio.1.mode=master\nusers.1.name=admin\nusers.1.password=\$1\$new\$hash\n")

    @Test fun users_are_kept_from_current_config() {
        val backup = SystemConfig.parse("system.hostname=sever-a\nnetconf.1.ip=192.168.1.20\nradio.1.freq=5180\n" +
            "radio.1.mode=master\nusers.1.name=ubnt\nusers.1.password=\$1\$old\$hash\nusers.2.name=extra\n")
        val p = RestorePlanner.plan(current, backup, "192.168.1.20")
        assertEquals("admin", p.config["users.1.name"])
        assertEquals("\$1\$new\$hash", p.config["users.1.password"])
        assertNull(p.config["users.2.name"])
        assertEquals("5180", p.config["radio.1.freq"])
        assertTrue(p.warnings.any { it.contains("Логин") })
    }

    @Test fun warnings_for_other_unit_mode_and_address() {
        val backup = SystemConfig.parse("system.hostname=yug-b\nnetconf.1.ip=192.168.1.30\nradio.1.mode=managed\n" +
            "users.1.name=admin\nusers.1.password=\$1\$new\$hash\n")
        val w = RestorePlanner.plan(current, backup, "192.168.1.20").warnings
        assertTrue(w.any { it.contains("другой пушки") })
        assertTrue(w.any { it.contains("Режим") })
        assertTrue(w.any { it.contains("192.168.1.30") })
        assertTrue(w.none { it.contains("Логин") })
    }

    @Test fun restore_goes_through_test_mode() = runTest {
        val d = DemoClient(now = { testScheduler.currentTime })
        val c = SettingsController(d, backgroundScope, host = "192.168.1.20", backup = {}, clock = { testScheduler.currentTime })
        c.load(); runCurrent()
        val backup = SystemConfig.parse(d.downloadConfig()).with("radio.1.freq", "5745").raw

        assertNotNull(c.restorePreview("hello")!!.error)
        val prev = c.restorePreview(backup)!!
        assertNull(prev.error)
        assertEquals(listOf("radio.1.freq"), prev.changes.map { it.key })

        c.restore(backup); runCurrent()
        val sa = c.apply.value!!
        assertTrue(sa.state.value is ApplyState.Testing)
        assertTrue(d.inTest)
        sa.confirmNow(); runCurrent()
        assertEquals("5745", c.config.value!!["radio.1.freq"])
    }
}
