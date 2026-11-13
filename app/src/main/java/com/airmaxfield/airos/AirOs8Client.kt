package com.airmaxfield.airos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** airOS 8.x: POST /api/auth → cookie + X-CSRF-ID; опрос /status.cgi. */
class AirOs8Client(
    private val baseUrl: HttpUrl,
    private val user: String,
    private val pass: String,
    private val http: OkHttpClient,
) : AirOsClient {
    override val version = AirOsVersion.V8
    @Volatile private var csrf: String? = null
    /** Обзор эфира идёт несколько секунд — отдельный клиент с длинным таймаутом (тот же пул и cookie). */
    private val slowHttp by lazy { http.newBuilder().readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build() }

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

    /** Вход уже выполнен коннектором при определении версии — запомнить токен. */
    internal fun adoptLogin(csrfToken: String?) { csrf = csrfToken }

    private fun req(path: String) = Request.Builder().url(baseUrl.resolve(path)!!)
        .apply { csrf?.let { header("X-CSRF-ID", it) } }

    private fun needsLogin(r: Resp) =
        r.code == 401 || r.code == 403 || r.redirectsToLogin || r.looksLikeLoginPage

    private suspend fun fetch(path: String, client: OkHttpClient = http, post: FormBody? = null): Resp {
        suspend fun once() = client.exec(req(path).apply { if (post != null) post(post) }.build())
        var r = once()
        if (needsLogin(r)) {
            login()
            r = once()
            if (needsLogin(r)) throw AirOsError.Protocol("сессия не принимается")
        }
        return r
    }

    private suspend fun fetchOk(path: String, client: OkHttpClient = http): String {
        val r = fetch(path, client)
        if (r.code !in 200..299) throw AirOsError.Protocol("$path: HTTP ${r.code}")
        return r.body
    }

    override suspend fun status(): DeviceStatus {
        val body = fetchOk("/status.cgi")
        return withContext(Dispatchers.Default) { StatusParser8.parse(body) }
    }

    override suspend fun stations(): List<StationInfo> {
        val body = fetchOk("/status.cgi")
        return withContext(Dispatchers.Default) { StationsParser.fromStatus8(body) }
    }

    override suspend fun survey(): List<SurveyEntry> {
        val body = fetchOk("/survey.json.cgi?iface=ath0", slowHttp)
        return withContext(Dispatchers.Default) { SurveyParser.parse(body) }
    }

    override suspend fun downloadConfig(): String {
        var r = fetch("/getcfg.sh")
        if (r.code == 404) r = fetch("/cfg.cgi")
        if (r.code !in 200..299) throw AirOsError.Protocol("конфигурация: HTTP ${r.code}")
        if (!ConfigBackup.looksLikeConfig(r.body)) throw AirOsError.Protocol("это не конфигурация")
        return r.body
    }

    override suspend fun reboot() {
        val r = fetch("/reboot.cgi", post = FormBody.Builder().add("reboot", "yes").build())
        if (r.code !in 200..399) throw AirOsError.Protocol("перезагрузка: HTTP ${r.code}")
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
