package com.airmaxfield.airos

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Определяет версию airOS и возвращает уже вошедший клиент.
 * POST /api/auth: 2xx → airOS 8; 401/403 → неверный пароль; 404 → airOS 6.
 * Если https не удался на уровне соединения/TLS — одна попытка по http.
 */
suspend fun connect(
    host: String,
    user: String,
    pass: String,
    http: OkHttpClient,
    scheme: String = "https",
    port: Int? = null,
): AirOsClient {
    return try {
        connectWith(baseUrl(scheme, host, port), user, pass, http)
    } catch (e: AirOsError.Unreachable) {
        if (scheme != "https") throw e
        connectWith(baseUrl("http", host, port), user, pass, http)
    }
}

private fun baseUrl(scheme: String, host: String, port: Int?): HttpUrl =
    HttpUrl.Builder().scheme(scheme).host(host).apply { if (port != null) port(port) }.build()

private suspend fun connectWith(base: HttpUrl, user: String, pass: String, http: OkHttpClient): AirOsClient {
    val probe = http.exec(
        Request.Builder().url(base.resolve("/api/auth")!!)
            .post(FormBody.Builder().add("username", user).add("password", pass).build())
            .build()
    )
    // Редирект с http на https — это не airOS 6, а проблема с https (старый TLS, таймаут).
    if (probe.code in 300..399 && probe.location?.startsWith("https", ignoreCase = true) == true) {
        throw AirOsError.Unreachable(java.io.IOException("пушка требует https, а https-соединение не удалось"))
    }
    return when {
        probe.code in 200..299 -> AirOs8Client(base, user, pass, http).also { it.adoptLogin(probe.csrf) }
        probe.code == 401 || probe.code == 403 -> throw AirOsError.BadCredentials()
        probe.code == 404 || probe.code in 300..399 -> AirOs6Client(base, user, pass, http).also { it.login() }
        else -> throw AirOsError.Protocol("определение версии: HTTP ${probe.code}")
    }
}
