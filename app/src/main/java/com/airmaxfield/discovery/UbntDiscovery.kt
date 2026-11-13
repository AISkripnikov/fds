package com.airmaxfield.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class Discovered(
    val ip: String,
    val mac: String?,
    val hostname: String?,
    val model: String?,
    val firmware: String?,
    val essid: String?,
)

/**
 * Разбор ответа протокола обнаружения Ubiquiti (UDP 10001).
 * Заголовок: версия(1) команда(1) длина(2, BE); затем TLV: тип(1) длина(2, BE) значение.
 */
object UbntDiscoveryParser {
    fun parse(packet: ByteArray, fromIp: String): Discovered? {
        if (packet.size < 4 || packet[0].toInt() != 1) return null
        val len = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (len == 0 || packet.size < 4 + len) return null
        var i = 4
        val end = 4 + len
        var ip: String? = null
        var mac: String? = null
        var host: String? = null
        var shortModel: String? = null
        var fullModel: String? = null
        var fw: String? = null
        var essid: String? = null
        while (i + 3 <= end) {
            val type = packet[i].toInt() and 0xFF
            val l = ((packet[i + 1].toInt() and 0xFF) shl 8) or (packet[i + 2].toInt() and 0xFF)
            val start = i + 3
            if (start + l > end) return null
            val v = packet.copyOfRange(start, start + l)
            when (type) {
                0x01 -> if (l == 6) mac = mac ?: macStr(v)
                0x02 -> if (l == 10) {
                    mac = mac ?: macStr(v.copyOfRange(0, 6))
                    ip = ip ?: v.copyOfRange(6, 10).joinToString(".") { (it.toInt() and 0xFF).toString() }
                }
                0x03 -> fw = String(v)
                0x0B -> host = String(v)
                0x0C -> shortModel = String(v)
                0x0D -> essid = String(v)
                0x14 -> fullModel = String(v)
            }
            i = start + l
        }
        return Discovered(
            ip = if (fromIp.isNotEmpty()) fromIp else ip ?: return null,
            mac = mac, hostname = host, model = fullModel ?: shortModel, firmware = fw, essid = essid,
        )
    }

    private fun macStr(b: ByteArray) = b.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
}

/** Рассылает запрос обнаружения и собирает ответы. [socketBinder] привязывает сокет к сети пушки. */
class UbntDiscovery(private val socketBinder: (DatagramSocket) -> Unit = {}) {
    suspend fun scan(timeoutMs: Long = 2500): List<Discovered> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, Discovered>()
        DatagramSocket().use { s ->
            socketBinder(s)
            s.broadcast = true
            s.soTimeout = 300
            val req = byteArrayOf(1, 0, 0, 0)
            s.send(DatagramPacket(req, req.size, InetAddress.getByName("255.255.255.255"), 10001))
            val buf = ByteArray(2048)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val p = DatagramPacket(buf, buf.size)
                try { s.receive(p) } catch (e: SocketTimeoutException) { continue }
                val from = p.address?.hostAddress ?: continue
                UbntDiscoveryParser.parse(buf.copyOf(p.length), from)?.let { found[it.ip] = it }
            }
        }
        found.values.toList()
    }
}
