package com.airmaxfield.airos

import com.airmaxfield.net.deviceHttpClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AirOs8ClientTest {
    private val server = MockWebServer().apply { start() }
    private fun client() = AirOs8Client(server.url("/"), "ubnt", "pw", deviceHttpClient(allowLoopback = true))
    private fun authOk() = MockResponse().setResponseCode(200)
        .addHeader("Set-Cookie", "AIROS_SESSIONID=s1; Path=/").addHeader("X-CSRF-ID", "abc").setBody("{}")
    private fun statusOk() = MockResponse().setBody(fixture("airos8/status_full.json"))

    @After fun tearDown() = server.shutdown()

    @Test fun login_sends_form_and_stores_csrf() = runTest {
        server.enqueue(authOk()); server.enqueue(statusOk())
        val c = client(); c.login(); val s = c.status()
        assertEquals(-58, s.signalDbm)
        val auth = server.takeRequest()
        assertEquals("POST", auth.method); assertEquals("/api/auth", auth.path)
        val body = auth.body.readUtf8()
        assertTrue(body.contains("username=ubnt")); assertTrue(body.contains("password=pw"))
        val st = server.takeRequest()
        assertEquals("/status.cgi", st.path)
        assertEquals("abc", st.getHeader("X-CSRF-ID")); assertTrue(st.getHeader("Cookie")!!.contains("AIROS_SESSIONID=s1"))
    }

    @Test fun wrong_password() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        try { client().login(); fail() } catch (e: AirOsError.BadCredentials) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun relogin_on_401() = runTest {
        server.enqueue(authOk()); server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(authOk()); server.enqueue(statusOk())
        val c = client(); c.login()
        assertEquals(-58, c.status().signalDbm)
        assertEquals(4, server.requestCount)
    }

    @Test fun second_failure_propagates() = runTest {
        server.enqueue(authOk()); server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(authOk()); server.enqueue(MockResponse().setResponseCode(401))
        val c = client(); c.login()
        try { c.status(); fail() } catch (e: AirOsError) { }
    }

    @Test fun unreachable() = runTest {
        val url = server.url("/"); server.shutdown()
        val c = AirOs8Client(url, "ubnt", "pw", deviceHttpClient(allowLoopback = true))
        try { c.login(); fail() } catch (e: AirOsError.Unreachable) { }
    }
}
