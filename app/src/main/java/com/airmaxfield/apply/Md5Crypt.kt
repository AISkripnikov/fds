package com.airmaxfield.apply

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * MD5-crypt (`$1$соль$хеш`) — формат users.N.password в system.cfg airOS.
 * Если пушка ждёт другой формат, новый пароль не подойдёт, пушка не ответит
 * в тест-режиме и откатится сама — сохранить такую конфигурацию нельзя.
 */
object Md5Crypt {
    private const val ITOA64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    fun randomSalt(rnd: SecureRandom = SecureRandom()): String =
        (1..8).map { ITOA64[rnd.nextInt(64)] }.joinToString("")

    fun hash(password: String, salt: String = randomSalt()): String {
        val pw = password.toByteArray(Charsets.UTF_8)
        val s = salt.take(8).toByteArray(Charsets.US_ASCII)
        val magic = "\$1\$".toByteArray(Charsets.US_ASCII)

        val alt = md5 { update(pw); update(s); update(pw) }
        val ctx = MessageDigest.getInstance("MD5")
        ctx.update(pw); ctx.update(magic); ctx.update(s)
        var n = pw.size
        while (n > 0) { ctx.update(alt, 0, minOf(16, n)); n -= 16 }
        var i = pw.size
        while (i != 0) {
            if (i and 1 != 0) ctx.update(0.toByte()) else ctx.update(pw[0])
            i = i ushr 1
        }
        var fin = ctx.digest()
        for (r in 0 until 1000) {
            fin = md5 {
                if (r and 1 != 0) update(pw) else update(fin)
                if (r % 3 != 0) update(s)
                if (r % 7 != 0) update(pw)
                if (r and 1 != 0) update(fin) else update(pw)
            }
        }
        val out = StringBuilder("\$1\$").append(String(s, Charsets.US_ASCII)).append('$')
        fun b64(a: Int, b: Int, c: Int, len: Int) {
            var v = ((fin[a].toInt() and 0xff) shl 16) or ((fin[b].toInt() and 0xff) shl 8) or (fin[c].toInt() and 0xff)
            repeat(len) { out.append(ITOA64[v and 0x3f]); v = v ushr 6 }
        }
        b64(0, 6, 12, 4); b64(1, 7, 13, 4); b64(2, 8, 14, 4); b64(3, 9, 15, 4); b64(4, 10, 5, 4)
        var v = fin[11].toInt() and 0xff
        repeat(2) { out.append(ITOA64[v and 0x3f]); v = v ushr 6 }
        return out.toString()
    }

    private inline fun md5(block: MessageDigest.() -> Unit): ByteArray =
        MessageDigest.getInstance("MD5").apply(block).digest()
}
