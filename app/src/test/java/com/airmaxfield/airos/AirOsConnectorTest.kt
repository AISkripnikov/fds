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

class AirOsConnectorTest {
    private val server = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
    private val http = deviceHttpClient(allowLoopback = true)

    @After fun tearDown() = server.shutdown()

    private suspend fun go(scheme: String = "http") =
        connect("127.0.0.1", "ubnt", "pw", http, scheme = scheme, port = server.port)

    @Test fun detects_v8() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).addHeader("X-CSRF-ID", "t").setBody("{}"))
        val c = go()
        assertTrue(c is AirOs8Client); assertEquals(AirOsVersion.V8, c.version)
        assertEquals(1, server.requestCount)
    }

    @Test fun detects_v6_on_404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().addHeader("Set-Cookie", "AIROS_SESSIONID=s; Path=/").setBody("<html>login</html>"))
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/status.cgi"))
        val c = go()
        assertTrue(c is AirOs6Client)
        assertEquals(3, server.requestCount)
    }

    @Test fun bad_credentials_v8() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        try { go(); fail() } catch (e: AirOsError.BadCredentials) { }
    }

    @Test fun redirect_to_https_is_not_airos6() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://127.0.0.1/login.cgi"))
        try { go(); fail("expected transport error") } catch (e: AirOsError.Unreachable) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun falls_back_to_http() = runTest {
        // Сервер без TLS: https-рукопожатие падает, коннектор пробует http.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val c = go(scheme = "https")
        assertTrue(c is AirOs8Client)
    }
}
