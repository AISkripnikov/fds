package com.airmaxfield.diag

import com.airmaxfield.airos.DemoClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import javax.net.SocketFactory

class DiagToolsTest {
    @Test fun ping_open_port_answers() {
        ServerSocket(0).use { srv ->
            val p = TcpPing({ SocketFactory.getDefault() }, ports = listOf(srv.localPort))
            val r = p.once("127.0.0.1")
            assertTrue(r.ok)
        }
    }

    @Test fun ping_closed_port_still_means_host_alive() {
        val port = ServerSocket(0).use { it.localPort } // порт освобождён — соединение получит отказ
        val r = TcpPing({ SocketFactory.getDefault() }, ports = listOf(port)).once("127.0.0.1")
        assertTrue(r.error ?: "", r.ok)
    }

    @Test fun ping_without_network_reports_reason() {
        val r = TcpPing({ null }).once("192.168.1.20")
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("Wi‑Fi"))
    }

    @Test fun ping_flow_counts_and_stats() = runTest {
        ServerSocket(0).use { srv ->
            val res = TcpPing({ SocketFactory.getDefault() }, ports = listOf(srv.localPort)).run("127.0.0.1", count = 3, intervalMs = 0).toList()
            assertEquals(listOf(1, 2, 3), res.map { it.seq })
            val st = TcpPing.stats(res + PingResult(4, null, "нет ответа"))
            assertEquals(4, st.sent); assertEquals(3, st.received); assertEquals(25, st.lossPercent)
        }
        assertNull(TcpPing.stats(listOf(PingResult(1, null, "x"))).avgMs)
    }

    @Test fun report_has_link_numbers_and_no_secrets() = runTest {
        val st = DemoClient().status()
        val text = LinkReport.build(
            LinkReport.Side("Ферма A", "192.168.1.21", st),
            LinkReport.Side("Ферма B", "192.168.1.22", null),
            PingStats(10, 9, 2, 3, 7), at = "14.11.2026 12:00", appVersion = "0.6.0",
        )
        assertTrue(text.contains("■ Ферма A (192.168.1.21)"))
        assertTrue(text.contains("Сигнал: ${st.signalDbm} dBm"))
        assertTrue(text.contains("SNR:"))
        assertTrue(text.contains("5180 МГц"))
        assertTrue(text.contains("■ Ферма B (192.168.1.22)\n  нет данных"))
        assertTrue(text.contains("потери 10%"))
        assertFalse(text.contains("demo_password_123"))
        assertEquals("1 д 2 ч", LinkReport.uptime(93_600))
        assertEquals("точка доступа PtP", LinkReport.modeTitle("ap-ptp"))
    }
}
