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

class AirOs6ClientTest {
    private val server = MockWebServer().apply { start() }
    private fun client() = AirOs6Client(server.url("/"), "ubnt", "pw", deviceHttpClient(allowLoopback = true))
    private fun loginPage() = MockResponse().addHeader("Set-Cookie", "AIROS_SESSIONID=s6; Path=/")
        .setBody("<html><form action=\"/login.cgi\"></form></html>")
    private fun loginOk() = MockResponse().setResponseCode(302).addHeader("Location", "/status.cgi")
    private fun statusOk() = MockResponse().setBody(fixture("airos6/status_full.json"))

    @After fun tearDown() = server.shutdown()

    @Test fun login_follows_cookie_flow() = runTest {
        server.enqueue(loginPage()); server.enqueue(loginOk()); server.enqueue(statusOk())
        val c = client(); c.login()
        assertEquals(-62, c.status().signalDbm)
        val get = server.takeRequest()
        assertEquals("GET", get.method); assertEquals("/login.cgi", get.path)
        val post = server.takeRequest()
        assertEquals("POST", post.method); assertEquals("/login.cgi", post.path)
        assertTrue(post.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = post.body.readUtf8()
        assertTrue(body.contains("name=\"username\"")); assertTrue(body.contains("ubnt"))
        assertTrue(body.contains("name=\"password\"")); assertTrue(body.contains("name=\"uri\""))
        assertTrue(post.getHeader("Cookie")!!.contains("AIROS_SESSIONID=s6"))
        assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("AIROS_SESSIONID=s6"))
    }

    @Test fun wrong_password() = runTest {
        server.enqueue(loginPage())
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/login.cgi?uri=/status.cgi"))
        try { client().login(); fail() } catch (e: AirOsError.BadCredentials) { }
        assertEquals(2, server.requestCount)
    }

    @Test fun wrong_password_as_200_login_page() = runTest {
        server.enqueue(loginPage()); server.enqueue(loginPage())
        try { client().login(); fail() } catch (e: AirOsError.BadCredentials) { }
    }

    @Test fun relogin_on_login_redirect() = runTest {
        server.enqueue(loginPage()); server.enqueue(loginOk())
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/login.cgi?uri=/status.cgi"))
        server.enqueue(loginPage()); server.enqueue(loginOk()); server.enqueue(statusOk())
        val c = client(); c.login()
        assertEquals(-62, c.status().signalDbm)
        assertEquals(6, server.requestCount)
    }
}
