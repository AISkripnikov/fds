package com.airmaxfield.airos

import com.airmaxfield.net.deviceHttpClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TestModeClientTest {
    private val server = MockWebServer().apply { start() }
    private val http = deviceHttpClient(allowLoopback = true)
    private val cfg = (1..12).joinToString("\n") { "radio.1.opt$it=v$it" } + "\nwireless.1.ssid=NEW\n"

    @After fun tearDown() = server.shutdown()

    private suspend fun v8(): AirOs8Client {
        server.enqueue(MockResponse().addHeader("X-CSRF-ID", "abc").setBody("{}"))
        return AirOs8Client(server.url("/"), "ubnt", "pw", http).also { it.login(); server.takeRequest() }
    }

    private suspend fun v6(): AirOs6Client {
        server.enqueue(MockResponse().setBody("<html>login</html>"))
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/status.cgi"))
        return AirOs6Client(server.url("/"), "ubnt", "pw", http).also { it.login(); server.takeRequest(); server.takeRequest() }
    }

    @Test fun v8_upload_then_testmode_with_timer() = runTest {
        val c = v8()
        assertTrue(c.hasTestMode)
        server.enqueue(MockResponse().setBody("{}")); server.enqueue(MockResponse().setBody("{}"))
        c.uploadConfig(cfg, 300)
        val up = server.takeRequest()
        assertEquals("POST", up.method); assertEquals(WriteEndpoints.UPLOAD, up.path)
        assertEquals("abc", up.getHeader("X-CSRF-ID"))
        val body = up.body.readUtf8()
        assertTrue(body.contains("name=\"cfgfile\"")); assertTrue(body.contains("wireless.1.ssid=NEW"))
        val test = server.takeRequest()
        assertEquals(WriteEndpoints.APPLY, test.path)
        assertEquals("testmode=on&testtime=300", test.body.readUtf8())
    }

    @Test fun v8_upload_failure_does_not_start_test() = runTest {
        val c = v8()
        server.enqueue(MockResponse().setResponseCode(500))
        try { c.uploadConfig(cfg, 180); fail() } catch (e: AirOsError.Protocol) { }
        assertEquals(1, server.requestCount - 1) // только загрузка, без apply
    }

    @Test fun v8_link_drop_after_testmode_is_not_an_error() = runTest {
        val c = v8()
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        c.uploadConfig(cfg, 180) // не бросает
    }

    @Test fun connect_failure_on_testmode_is_not_a_started_test() = runTest {
        // Первый запрос (загрузка) проходит, затем сервер исчезает — команда теста не уходит вовсе.
        val c = AirOs8Client(server.url("/"), "ubnt", "pw", deviceHttpClient(allowLoopback = true).newBuilder()
            .addInterceptor { chain ->
                if (chain.request().url.encodedPath == WriteEndpoints.APPLY) throw java.net.ConnectException("refused")
                chain.proceed(chain.request())
            }.build())
        server.enqueue(MockResponse().setBody("{}"))
        try { c.uploadConfig(cfg, 180); fail() } catch (e: AirOsError.Protocol) {
            assertTrue(e.message!!.contains("не включился"))
        }
    }

    @Test fun unreachable_classification() {
        assertTrue(AirOsError.Unreachable(java.net.ConnectException()).beforeSend)
        assertTrue(AirOsError.Unreachable(java.net.SocketTimeoutException("connect timed out")).beforeSend)
        assertFalse(AirOsError.Unreachable(java.net.SocketTimeoutException("timeout")).beforeSend)
        assertFalse(AirOsError.Unreachable(java.io.IOException("unexpected end of stream")).beforeSend)
    }

    @Test fun v8_confirm_and_cancel() = runTest {
        val c = v8()
        server.enqueue(MockResponse().setBody("{}")); server.enqueue(MockResponse().setBody("{}"))
        c.confirmTest(); c.cancelTest()
        assertEquals("testmode=off", server.takeRequest().body.readUtf8())
        val rb = server.takeRequest()
        assertEquals(WriteEndpoints.REBOOT, rb.path); assertEquals("reboot=yes", rb.body.readUtf8())
    }

    @Test fun v6_fixed_timer_and_get_apply() = runTest {
        val c = v6()
        assertEquals(180, c.testDurationFor(300))
        server.enqueue(MockResponse().setBody("ok")); server.enqueue(MockResponse().setBody("ok")); server.enqueue(MockResponse().setBody("ok"))
        c.uploadConfig(cfg, 300); c.confirmTest()
        assertEquals(WriteEndpoints.UPLOAD, server.takeRequest().path)
        val t = server.takeRequest(); assertEquals("GET", t.method); assertEquals("/apply.cgi?testmode=on", t.path)
        val ok = server.takeRequest(); assertEquals("GET", ok.method); assertEquals("/apply.cgi", ok.path)
    }

    @Test fun serialized_client_delegates_write_api() = runTest {
        val demo = DemoClient()
        val s = SerializedClient(demo)
        assertTrue(s.hasTestMode)
        val cfg = SystemConfig.parse(s.downloadConfig()).with("radio.1.freq", "5200").raw
        s.uploadConfig(cfg, 180)
        assertTrue(demo.inTest)
        s.cancelTest()
        assertFalse(demo.inTest)
    }

    @Test fun demo_auto_rollback_after_timer() = runTest {
        var now = 0L
        val d = DemoClient(now = { now })
        val orig = d.downloadConfig()
        d.uploadConfig(SystemConfig.parse(orig).with("radio.1.freq", "5200").raw, 180)
        assertEquals(5200, d.status().frequencyMhz)
        now = 179_000; assertTrue(d.inTest)
        now = 180_000
        assertEquals(orig, d.downloadConfig())
        assertEquals(5180, d.status().frequencyMhz)
        try { d.confirmTest(); fail() } catch (e: AirOsError.Protocol) { }
    }

    @Test fun demo_confirm_keeps_config() = runTest {
        var now = 0L
        val d = DemoClient(now = { now })
        val next = SystemConfig.parse(d.downloadConfig()).with("radio.1.freq", "5200").raw
        d.uploadConfig(next, 180)
        d.confirmTest()
        now = 1_000_000
        assertEquals(next, d.downloadConfig())
    }
}
