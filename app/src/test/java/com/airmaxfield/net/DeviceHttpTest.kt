package com.airmaxfield.net

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import javax.net.ssl.SSLException

class DeviceHttpTest {
    private val server = MockWebServer()

    @Before fun setUp() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val hs = HandshakeCertificates.Builder().heldCertificate(cert).build()
        server.useHttps(hs.sslSocketFactory(), false)
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun self_signed_accepted_for_allowed_host_and_cookies_kept() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "AIROS_SESSIONID=abc; Path=/").setBody("one"))
        server.enqueue(MockResponse().setBody("two"))
        val http = deviceHttpClient(allowLoopback = true)
        http.newCall(Request.Builder().url(server.url("/a")).build()).execute().use { assertEquals("one", it.body!!.string()) }
        http.newCall(Request.Builder().url(server.url("/b")).build()).execute().use { assertEquals("two", it.body!!.string()) }
        server.takeRequest()
        assertEquals("AIROS_SESSIONID=abc", server.takeRequest().getHeader("Cookie"))
    }

    @Test fun self_signed_rejected_for_non_private_host() {
        server.enqueue(MockResponse().setBody("x"))
        val http = deviceHttpClient(allowLoopback = false)
        try {
            http.newCall(Request.Builder().url(server.url("/a")).build()).execute().close()
            fail("expected SSLException")
        } catch (e: SSLException) { /* ok */ }
    }
}
