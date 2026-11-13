package com.airmaxfield.airos

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** airOS 8.x: POST /api/auth → cookie + X-CSRF-ID; опрос /status.cgi. */
class AirOs8Client(
    private val baseUrl: HttpUrl,
    private val user: String,
    private val pass: String,
    private val http: OkHttpClient,
) : AirOsClient {
    override val version = AirOsVersion.V8
    private var csrf: String? = null

    override suspend fun login() {
        val r = http.exec(
            Request.Builder().url(baseUrl.resolve("/api/auth")!!)
                .post(FormBody.Builder().add("username", user).add("password", pass).build())
                .build()
        )
        when {
            r.code == 401 || r.code == 403 -> throw AirOsError.BadCredentials()
            r.code !in 200..299 -> throw AirOsError.Protocol("вход: HTTP ${r.code}")
        }
        csrf = r.csrf
    }

    private fun get(path: String) = Request.Builder().url(baseUrl.resolve(path)!!)
        .apply { csrf?.let { header("X-CSRF-ID", it) } }.build()

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
        return StatusParser8.parse(r.body)
    }

    override suspend fun rawDump(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (p in listOf("/status.cgi", "/getcfg.sh")) {
            val r = runCatching { fetch(p) }.getOrNull() ?: continue
            if (r.code in 200..299) out[p] = r.body
        }
        return out
    }
}
