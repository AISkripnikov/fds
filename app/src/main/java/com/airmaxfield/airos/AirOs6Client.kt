package com.airmaxfield.airos

import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * airOS 6.x: GET /login.cgi (cookie) → POST /login.cgi multipart (username, password, uri);
 * опрос /status.cgi. Признак неудачного входа — снова страница/редирект логина.
 */
class AirOs6Client(
    private val baseUrl: HttpUrl,
    private val user: String,
    private val pass: String,
    private val http: OkHttpClient,
) : AirOsClient {
    override val version = AirOsVersion.V6

    override suspend fun login() {
        http.exec(Request.Builder().url(baseUrl.resolve("/login.cgi")!!).build())
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("uri", "/status.cgi")
            .addFormDataPart("username", user)
            .addFormDataPart("password", pass)
            .build()
        val r = http.exec(Request.Builder().url(baseUrl.resolve("/login.cgi")!!).post(body).build())
        when {
            r.redirectsToLogin -> throw AirOsError.BadCredentials()
            r.code in 300..399 -> Unit
            r.code in 200..299 && r.looksLikeLoginPage -> throw AirOsError.BadCredentials()
            r.code in 200..299 -> Unit
            else -> throw AirOsError.Protocol("вход: HTTP ${r.code}")
        }
    }

    private fun get(path: String) = Request.Builder().url(baseUrl.resolve(path)!!).build()

    private fun needsLogin(r: Resp) =
        r.code == 401 || r.code == 403 || r.redirectsToLogin || r.looksLikeLoginPage

    private suspend fun fetch(path: String): Resp {
        var r = http.exec(get(path))
        if (needsLogin(r)) {
            login()
            r = http.exec(get(path))
            if (needsLogin(r)) throw AirOsError.Protocol("сессия не принимается")
        }
        return r
    }

    override suspend fun status(): DeviceStatus {
        val r = fetch("/status.cgi")
        if (r.code !in 200..299) throw AirOsError.Protocol("status.cgi: HTTP ${r.code}")
        return StatusParser6.parse(r.body)
    }

    override suspend fun rawDump(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (p in listOf("/status.cgi", "/sta.cgi", "/signal.cgi")) {
            val r = runCatching { fetch(p) }.getOrNull() ?: continue
            if (r.code in 200..299) out[p] = r.body
        }
        return out
    }
}
