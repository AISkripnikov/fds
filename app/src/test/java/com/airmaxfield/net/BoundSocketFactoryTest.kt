package com.airmaxfield.net

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class BoundSocketFactoryTest {
    private class Counting : SocketFactory() {
        var n = 0
        override fun createSocket(): Socket { n++; return Socket() }
        override fun createSocket(h: String?, p: Int) = throw UnsupportedOperationException()
        override fun createSocket(h: String?, p: Int, la: InetAddress?, lp: Int) = throw UnsupportedOperationException()
        override fun createSocket(h: InetAddress?, p: Int) = throw UnsupportedOperationException()
        override fun createSocket(a: InetAddress?, p: Int, la: InetAddress?, lp: Int) = throw UnsupportedOperationException()
    }

    @Test fun no_local_network_fails_instead_of_default_route() {
        val f = BoundSocketFactory { null }
        try { f.createSocket(); fail("must not fall back to the default (mobile) network") } catch (e: IOException) { }
    }

    @Test fun resolves_current_network_on_every_socket() {
        val a = Counting(); val b = Counting()
        var current: SocketFactory? = a
        val f = BoundSocketFactory { current }
        f.createSocket().close()
        current = b // сеть сменилась (пушку перезагрузили, переподключили кабель)
        f.createSocket().close(); f.createSocket().close()
        assertEquals(1, a.n); assertEquals(2, b.n)
    }
}
