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
class BridgeTest {
    private val profile = BridgeProfile(
        name = "Ферма Север", ssid = "FERMA-LINK", psk = "secret-key-1", countryCode = "643",
        freqMhz = 5500, chanbwMhz = 20, txPowerDbm = 18, ipA = "192.168.1.21", ipB = "192.168.1.22",
        gateway = "192.168.1.1", user = "admin", pass = "StrongPass1",
    )
    private val base = SystemConfig.parse("# заводская\nnetconf.1.devname=eth0\nnetconf.1.ip=0.0.0.0\n" +
        "netconf.3.devname=br0\nnetconf.3.ip=192.168.1.20\nnetconf.3.netmask=255.255.255.0\nradio.1.freq=5180\n" +
        "radio.1.mode=managed\nusers.1.name=ubnt\nusers.1.password=\$1\$x\$y\nmystery.key=keep\n")

    @Test fun md5crypt_matches_openssl() {
        assertEquals("\$1\$saltsalt\$qjXMvbEw8oaL.CzflDtaK/", Md5Crypt.hash("password", "saltsalt"))
        assertEquals("\$1\$ab\$KY7SAxTWy6yQPBzXleuJm1", Md5Crypt.hash("ubnt", "ab"))
        assertEquals("\$1\$Xy7.z/Qw\$pc2uLgQN10b1gYeSrDjTM.", Md5Crypt.hash("Пароль-1", "Xy7.z/Qw"))
        assertEquals(8, Md5Crypt.randomSalt().length)
    }

    @Test fun ap_and_station_configs_match_on_link_params() {
        val a = BridgePlanner.plan(profile, BridgeRole.AP, base, "saltsalt")
        val b = BridgePlanner.plan(profile, BridgeRole.STATION, base, "saltsalt")
        assertEquals("master", a["radio.1.mode"]); assertEquals("managed", b["radio.1.mode"])
        for (k in listOf("wireless.1.ssid", "radio.1.chanbw", "radio.countrycode", "radio.1.countrycode")) {
            assertEquals(k, a[k], b[k])
        }
        assertEquals("secret-key-1", a["aaa.1.wpa.psk"])
        assertEquals("secret-key-1", b["wpasupplicant.profile.1.network.1.psk"])
        assertEquals("5500", a["radio.1.freq"])
        assertEquals("5180", b["radio.1.freq"]) // станция частоту не задаёт — сканирует
        // адрес — на интерфейсе br0, не на eth0
        assertEquals("192.168.1.21", a["netconf.3.ip"]); assertEquals("192.168.1.22", b["netconf.3.ip"])
        assertEquals("0.0.0.0", a["netconf.1.ip"])
        assertEquals("192.168.1.1", a["route.1.gateway"])
        assertEquals("admin", a["users.1.name"])
        assertEquals(Md5Crypt.hash("StrongPass1", "saltsalt"), a["users.1.password"])
        assertEquals("keep", a["mystery.key"])
        assertTrue(a.raw.startsWith("# заводская\n"))
    }

    @Test fun hostname_is_ascii_and_has_side_suffix() {
        assertEquals("bridge-A", BridgePlanner.hostname("Ферма", BridgeRole.AP))
        assertEquals("Sever-1-B", BridgePlanner.hostname("Sever 1", BridgeRole.STATION))
    }

    @Test fun validation() {
        assertTrue(BridgePlanner.validate(profile).isEmpty())
        val bad = profile.copy(psk = "short", ipB = "10.0.0.1", pass = "ubnt", user = "ubnt", mgmtVlan = 5000)
        val errs = BridgePlanner.validate(bad)
        assertTrue(errs.any { it.contains("WPA2") })
        assertTrue(errs.any { it.contains("одной подсети") })
        assertTrue(errs.any { it.contains("VLAN") })
        assertTrue(errs.any { it.contains("Заводской") })
    }

    @Test fun first_login_plan_and_validation() {
        val cfg = FirstLogin.plan(base, "admin", "StrongPass1", "643", "saltsalt")
        assertEquals("admin", cfg["users.1.name"]); assertEquals("643", cfg["radio.countrycode"])
        assertTrue(FirstLogin.isFactoryLogin("ubnt", "ubnt"))
        assertTrue(FirstLogin.validate("admin", "StrongPass1", "StrongPass1", "643", true).isEmpty())
        assertTrue(FirstLogin.validate("admin", "StrongPass1", "StrongPass1", "643", false).any { it.contains("условия") })
        assertTrue(FirstLogin.validate("admin", "StrongPass1", "other", "643", true).any { it.contains("совпадают") })
    }

    // --- обе стороны ---

    private fun TestScope.demo() = DemoClient(now = { testScheduler.currentTime })
    private fun TestScope.orch(near: AirOsClient, far: AirOsClient) =
        BothEndsOrchestrator(near, far, {}, {}, backgroundScope, clock = { testScheduler.currentTime })
    private suspend fun relinked(c: AirOsClient, role: BridgeRole) =
        BridgePlanner.relink(SystemConfig.parse(c.downloadConfig()), role, profile.link).raw

    @Test fun far_first_with_longer_timer_then_near() = runTest {
        val order = mutableListOf<String>()
        val nd = demo(); val fd = demo()
        val near = object : AirOsClient by nd {
            override suspend fun uploadConfig(cfg: String, testSeconds: Int) { order += "near:$testSeconds"; nd.uploadConfig(cfg, testSeconds) }
            override suspend fun confirmTest() { order += "near:ok"; nd.confirmTest() }
        }
        val far = object : AirOsClient by fd {
            override suspend fun uploadConfig(cfg: String, testSeconds: Int) { order += "far:$testSeconds"; fd.uploadConfig(cfg, testSeconds) }
            override suspend fun confirmTest() { order += "far:ok"; fd.confirmTest() }
        }
        val o = orch(near, far)
        assertTrue(o.prepare(relinked(near, BridgeRole.STATION), relinked(far, BridgeRole.AP)))
        runCurrent()
        assertTrue(o.canConfirm())
        assertTrue(o.confirmBoth())
        assertEquals(listOf("far:300", "near:180", "far:ok", "near:ok"), order)
        assertEquals("FERMA-LINK", SystemConfig.parse(nd.downloadConfig())["wireless.1.ssid"])
    }

    @Test fun refused_without_test_mode_on_either_side() = runTest {
        val nd = demo()
        val noTest = object : AirOsClient by demo() { override val hasTestMode get() = false }
        val o = orch(nd, noTest)
        assertFalse(o.prepare("x", "y"))
        assertTrue(o.problem.value!!.contains("дальней"))
        assertFalse(nd.inTest)
    }

    @Test fun refused_when_far_timer_is_not_longer() = runTest {
        // airOS 6 на дальней: таймер фиксирован 180 с — не больше ближней
        val fixed = object : AirOsClient by demo() { override fun testDurationFor(requested: Int) = 180 }
        val o = orch(demo(), fixed)
        assertFalse(o.prepare("x", "y"))
        assertTrue(o.problem.value!!.contains("Таймер"))
    }

    @Test fun refused_when_far_unreachable() = runTest {
        val dead = object : AirOsClient by demo() { override suspend fun status(): DeviceStatus = throw AirOsError.Unreachable(null) }
        val o = orch(demo(), dead)
        assertFalse(o.prepare("x", "y"))
        assertTrue(o.problem.value!!.contains("Дальняя"))
    }

    @Test fun link_not_restored_means_both_roll_back_by_timers() = runTest {
        val nd = demo(); val fd = demo()
        var linkUp = true
        val far = object : AirOsClient by fd {
            override suspend fun status(): DeviceStatus = if (linkUp) fd.status() else throw AirOsError.Unreachable(null)
        }
        val o = orch(nd, far)
        assertTrue(o.prepare(relinked(nd, BridgeRole.STATION), relinked(fd, BridgeRole.AP)))
        linkUp = false
        advanceTimeBy(2_100); runCurrent()
        assertFalse(o.canConfirm())
        assertFalse(o.confirmBoth())
        advanceTimeBy(180_000); runCurrent()
        assertTrue(o.nearApply.state.value is ApplyState.RolledBack)
        assertTrue(o.farApply.state.value is ApplyState.Testing)
        advanceTimeBy(120_000); runCurrent()
        assertTrue(o.farApply.state.value is ApplyState.RolledBack)
        assertFalse(nd.inTest); assertFalse(fd.inTest)
    }

    @Test fun near_upload_failure_rolls_far_back() = runTest {
        val nd = demo(); val fd = demo()
        val near = object : AirOsClient by nd {
            override suspend fun uploadConfig(cfg: String, testSeconds: Int) = throw AirOsError.Protocol("HTTP 500")
        }
        val o = orch(near, fd)
        assertFalse(o.prepare(relinked(nd, BridgeRole.STATION), relinked(fd, BridgeRole.AP)))
        assertTrue(o.problem.value!!.contains("Ближняя"))
        assertFalse(fd.inTest)
    }
}
