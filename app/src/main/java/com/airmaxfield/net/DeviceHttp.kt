package com.airmaxfield.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Хранит cookie сессии пушки в памяти (по хосту). */
class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableMap<String, Cookie>>()

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val m = store.getOrPut(url.host) { mutableMapOf() }
        cookies.forEach { m[it.name] = it }
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host]?.values?.toList().orEmpty()

    @Synchronized fun clear() = store.clear()
}

/**
 * HTTP-клиент для пушек: таймаут 4 с, cookie в памяти, без авто-редиректов.
 * Самоподписанный сертификат принимается, но соединение разрешено только с частными IP
 * (проверка в HostnameVerifier — до отправки запроса).
 */
fun deviceHttpClient(socketFactory: SocketFactory? = null, allowLoopback: Boolean = false): OkHttpClient {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
    val allowed = { host: String -> isPrivateIp(host) || (allowLoopback && host == "127.0.0.1") }
    return OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cookieJar(MemoryCookieJar())
        .sslSocketFactory(ssl.socketFactory, trustAll)
        .hostnameVerifier { host, _ -> allowed(host) }
        .apply { if (socketFactory != null) socketFactory(socketFactory) }
        .build()
}
