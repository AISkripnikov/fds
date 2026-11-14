package com.airmaxfield.airos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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

    private suspend fun fetch(path: String, client: OkHttpClient = http, post: RequestBody? = null): Resp {
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

    /** Последний ответ обзора эфира — попадает в диагностический дамп (без повторного обзора). */
    @Volatile private var lastSurveyRaw: String? = null

    override suspend fun survey(): List<SurveyEntry> {
        val body = fetchOk("/survey.json.cgi?iface=ath0", slowHttp)
        lastSurveyRaw = body
        return withContext(Dispatchers.Default) { SurveyParser.parse(body) }
    }

    override suspend fun downloadConfig(): String {
        var r = fetch("/getcfg.sh")
        if (r.code == 404) r = fetch("/cfg.cgi")
        if (r.code !in 200..299) throw AirOsError.Protocol("конфигурация: HTTP ${r.code}")
        if (!ConfigBackup.looksLikeConfig(r.body)) throw AirOsError.Protocol("это не конфигурация")
        return r.body
    }

    override val hasTestMode get() = true

    override suspend fun uploadConfig(cfg: String, testSeconds: Int) {
        val up = fetch(WriteEndpoints.UPLOAD, post = WriteEndpoints.uploadBody(cfg))
        if (up.code !in 200..399) throw AirOsError.Protocol("загрузка конфигурации: HTTP ${up.code}")
        val form = FormBody.Builder().add("testmode", "on").add("testtime", testSeconds.toString()).build()
        try {
            val r = fetch(WriteEndpoints.APPLY, post = form)
            if (r.code !in 200..399) throw AirOsError.Protocol("тест-режим: HTTP ${r.code}")
        } catch (e: AirOsError.Unreachable) {
            // Команда ушла, а ответа нет: пушка применила конфигурацию и пропала (смена IP/канала) —
            // для теста это нормально. Если же команда не ушла вовсе — тест не начался.
            if (e.beforeSend) throw AirOsError.Protocol(TEST_NOT_STARTED)
        }
    }

    override suspend fun confirmTest() {
        val r = fetch(WriteEndpoints.APPLY, post = FormBody.Builder().add("testmode", "off").build())
        if (r.code !in 200..399) throw AirOsError.Protocol("сохранение: HTTP ${r.code}")
    }

    override suspend fun cancelTest() = reboot()

    override suspend fun reboot() {
        // Без повтора по «странице логина»: после команды пушка может отдать любую HTML-страницу.
        suspend fun once() = http.exec(req(WriteEndpoints.REBOOT).post(FormBody.Builder().add("reboot", "yes").build()).build())
        var r = once()
        if (r.code == 401 || r.code == 403) { login(); r = once() }
        if (r.code !in 200..399) throw AirOsError.Protocol("перезагрузка: HTTP ${r.code}")
    }

    override suspend fun rawDump(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        // Конфигурацию (пароли, ключи) в дамп не включаем.
        for (p in listOf("/status.cgi")) {
            val r = runCatching { fetch(p) }.getOrNull() ?: continue
            if (r.code in 200..299) out[p] = r.body
        }
        lastSurveyRaw?.let { out["/survey.json.cgi (последний обзор)"] = it }
        return out
    }
}
