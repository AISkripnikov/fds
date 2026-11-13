package com.airmaxfield.net

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Фабрика сокетов, которая на КАЖДОМ новом соединении заново выбирает сеть пушки (Wi‑Fi/кабель).
 * - Пушку перезагрузили или переподключили кабель → Android выдал новую Network → подхватываем её.
 * - Сети пушки нет → ошибка, а не тихий уход в мобильную сеть.
 */
class BoundSocketFactory(private val resolve: () -> SocketFactory?) : SocketFactory() {
    private fun current(): SocketFactory =
        resolve() ?: throw IOException("Нет Wi‑Fi или кабеля к пушке")

    override fun createSocket(): Socket = current().createSocket()
    override fun createSocket(host: String?, port: Int): Socket = current().createSocket(host, port)
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        current().createSocket(host, port, localHost, localPort)
    override fun createSocket(host: InetAddress?, port: Int): Socket = current().createSocket(host, port)
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        current().createSocket(address, port, localAddress, localPort)
}
