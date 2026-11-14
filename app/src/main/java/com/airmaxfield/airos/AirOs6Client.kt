package com.airmaxfield.airos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

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
    private val slowHttp by lazy { http.newBuilder().readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build() }

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

    private fun needsLogin(r: Resp) =
        r.code == 401 || r.code == 403 || r.redirectsToLogin || r.looksLikeLoginPage

    private suspend fun fetch(path: String, client: OkHttpClient = http, post: RequestBody? = null): Resp {
        suspend fun once() = client.exec(
            Request.Builder().url(baseUrl.resolve(path)!!).apply { if (post != null) post(post) }.build()
        )
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
        return withContext(Dispatchers.Default) { StatusParser6.parse(body) }
    }

    override suspend fun stations(): List<StationInfo> {
        val body = fetchOk("/sta.cgi")
        return withContext(Dispatchers.Default) { StationsParser.fromSta6(body) }
    }

    override suspend fun survey(): List<SurveyEntry> {
        val body = fetchOk("/survey.json.cgi?iface=ath0", slowHttp)
        return withContext(Dispatchers.Default) { SurveyParser.parse(body) }
    }

    override suspend fun downloadConfig(): String {
        val body = fetchOk("/cfg.cgi")
        if (!ConfigBackup.looksLikeConfig(body)) throw AirOsError.Protocol("это не конфигурация")
        return body
    }

    override val hasTestMode get() = true

    override fun testDurationFor(requested: Int) = WriteEndpoints.V6_TEST_SECONDS

    override suspend fun uploadConfig(cfg: String, testSeconds: Int) {
        val up = fetch(WriteEndpoints.UPLOAD, post = WriteEndpoints.uploadBody(cfg))
        if (up.code !in 200..399) throw AirOsError.Protocol("загрузка конфигурации: HTTP ${up.code}")
        try {
            val r = fetch("${WriteEndpoints.APPLY}?testmode=on")
            if (r.code !in 200..399) throw AirOsError.Protocol("тест-режим: HTTP ${r.code}")
        } catch (e: AirOsError.Unreachable) {
            // Команда ушла, а ответа нет: пушка применила конфигурацию и пропала (смена IP/канала) —
            // для теста это нормально. Если же команда не ушла вовсе — тест не начался.
            if (e.beforeSend) throw AirOsError.Protocol(TEST_NOT_STARTED)
        }
    }

    override suspend fun confirmTest() {
        val r = fetch(WriteEndpoints.APPLY)
        if (r.code !in 200..399) throw AirOsError.Protocol("сохранение: HTTP ${r.code}")
    }

    override suspend fun cancelTest() = reboot()

    override suspend fun reboot() {
        suspend fun once() = http.exec(
            Request.Builder().url(baseUrl.resolve(WriteEndpoints.REBOOT)!!)
                .post(FormBody.Builder().add("reboot", "yes").build()).build()
        )
        var r = once()
        if (r.code == 401 || r.code == 403) { login(); r = once() }
        if (r.code !in 200..399) throw AirOsError.Protocol("перезагрузка: HTTP ${r.code}")
    }

    override suspend fun rawDump(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        // Конфигурацию (пароли, ключи) в дамп не включаем.
        for (p in listOf("/status.cgi", "/sta.cgi", "/signal.cgi")) {
            val r = runCatching { fetch(p) }.getOrNull() ?: continue
            if (r.code in 200..299) out[p] = r.body
        }
        return out
    }
}
