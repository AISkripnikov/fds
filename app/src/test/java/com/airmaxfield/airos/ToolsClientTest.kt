package com.airmaxfield.airos

import com.airmaxfield.net.deviceHttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ToolsClientTest {
    private val server = MockWebServer().apply { start() }
    private val http = deviceHttpClient(allowLoopback = true)
    private val cfg = (1..12).joinToString("\n") { "radio.1.opt$it=v$it" } + "\nwireless.1.ssid=LINK\n"

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

    @Test fun v8_reboot_posts_with_csrf() = runTest {
        val c = v8(); server.enqueue(MockResponse().setBody("{}"))
        c.reboot()
        val r = server.takeRequest()
        assertEquals("POST", r.method); assertEquals("/reboot.cgi", r.path)
        assertEquals("abc", r.getHeader("X-CSRF-ID")); assertTrue(r.body.readUtf8().contains("reboot=yes"))
    }

    @Test fun v6_reboot_posts() = runTest {
        val c = v6(); server.enqueue(MockResponse().setBody("ok"))
        c.reboot()
        val r = server.takeRequest()
        assertEquals("POST", r.method); assertEquals("/reboot.cgi", r.path); assertTrue(r.body.readUtf8().contains("reboot=yes"))
    }

    @Test fun v8_config_falls_back_to_cfg_cgi() = runTest {
        val c = v8()
        server.enqueue(MockResponse().setResponseCode(404)); server.enqueue(MockResponse().setBody(cfg))
        assertEquals(cfg, c.downloadConfig())
        assertEquals("/getcfg.sh", server.takeRequest().path); assertEquals("/cfg.cgi", server.takeRequest().path)
    }

    @Test fun config_that_is_not_config_is_rejected() = runTest {
        val c = v6(); server.enqueue(MockResponse().setBody("{\"error\":1}"))
        try { c.downloadConfig(); fail() } catch (e: AirOsError.Protocol) { }
    }

    @Test fun v6_stations_and_survey() = runTest {
        val c = v6()
        server.enqueue(MockResponse().setBody(fixture("airos6/sta.json")))
        assertEquals(2, c.stations().size)
        assertEquals("/sta.cgi", server.takeRequest().path)
        server.enqueue(MockResponse().setBody(fixture("survey.json")))
        assertEquals(3, c.survey().size)
        assertEquals("/survey.json.cgi?iface=ath0", server.takeRequest().path)
    }

    @Test fun v8_stations_from_status() = runTest {
        val c = v8(); server.enqueue(MockResponse().setBody(fixture("airos8/status_full.json")))
        assertEquals("Sever-B", c.stations().single().name)
    }

    @Test fun serialized_client_never_overlaps() = runTest {
        val active = AtomicInteger(); val maxActive = AtomicInteger()
        val slow = object : AirOsClient by DemoClient() {
            override suspend fun status(): DeviceStatus {
                maxActive.set(maxOf(maxActive.get(), active.incrementAndGet()))
                delay(100); active.decrementAndGet()
                return DeviceStatus(AirOsVersion.V8)
            }
        }
        val c = SerializedClient(slow)
        val a = async { c.status() }; val b = async { c.status() }; val d = async { c.stations() }
        a.await(); b.await(); d.await()
        assertEquals(1, maxActive.get())
    }
}
