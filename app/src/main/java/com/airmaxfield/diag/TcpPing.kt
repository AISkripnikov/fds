package com.airmaxfield.diag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/** Результат одной попытки: время соединения в мс или причина неудачи. */
data class PingResult(val seq: Int, val ms: Long?, val error: String? = null) {
    val ok: Boolean get() = ms != null
}

data class PingStats(val sent: Int, val received: Int, val minMs: Long?, val avgMs: Long?, val maxMs: Long?) {
    val lossPercent: Int get() = if (sent == 0) 0 else ((sent - received) * 100 + sent / 2) / sent
}

/**
 * «Ping» через TCP-соединение к веб-интерфейсу пушки (443, затем 80).
 * ICMP в Android без root недоступен и может уйти в мобильную сеть; TCP-сокет мы создаём
 * через [socketFactory] нужной сети телефона (Wi‑Fi или кабель).
 *
 * Отказ в соединении (RST) — тоже ответ: хост жив, просто порт закрыт.
 */
class TcpPing(
    private val socketFactory: () -> SocketFactory?,
    private val ports: List<Int> = listOf(443, 80),
    private val timeoutMs: Int = 1500,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    fun run(host: String, count: Int = 10, intervalMs: Long = 1000): Flow<PingResult> = flow {
        for (seq in 1..count) {
            emit(once(host, seq))
            if (seq < count) delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun once(host: String, seq: Int = 1): PingResult {
        val sf = socketFactory() ?: return PingResult(seq, null, "нет Wi‑Fi или кабеля к этому адресу")
        var last: String? = null
        for (port in ports) {
            val start = clock()
            try {
                sf.createSocket().use { s: Socket ->
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                }
                return PingResult(seq, (clock() - start).coerceAtLeast(0))
            } catch (e: ConnectException) {
                // RST: хост ответил — значит, жив.
                if (e.message?.contains("refused", ignoreCase = true) == true) return PingResult(seq, (clock() - start).coerceAtLeast(0))
                last = e.message
            } catch (e: IOException) {
                last = if (e is java.net.SocketTimeoutException) "нет ответа" else e.message
            }
        }
        return PingResult(seq, null, last ?: "нет ответа")
    }

    companion object {
        fun stats(results: List<PingResult>): PingStats {
            val ok = results.mapNotNull { it.ms }
            return PingStats(results.size, ok.size, ok.minOrNull(), if (ok.isEmpty()) null else ok.sum() / ok.size, ok.maxOrNull())
        }
    }
}
