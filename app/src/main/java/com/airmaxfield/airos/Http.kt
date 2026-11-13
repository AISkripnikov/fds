package com.airmaxfield.airos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.net.ssl.SSLPeerUnverifiedException

/** Результат HTTP-запроса к пушке, уже прочитанный. */
internal data class Resp(val code: Int, val body: String, val location: String?, val csrf: String?) {
    val redirectsToLogin: Boolean get() = code in 300..399 && location?.contains("login") == true
    val looksLikeLoginPage: Boolean
        get() = body.trimStart().startsWith("<") && body.contains("login", ignoreCase = true)
}

internal suspend fun OkHttpClient.exec(req: Request): Resp = withContext(Dispatchers.IO) {
    try {
        newCall(req).execute().use { r ->
            Resp(r.code, r.body?.string().orEmpty(), r.header("Location"), r.header("X-CSRF-ID"))
        }
    } catch (e: SSLPeerUnverifiedException) {
        throw AirOsError.Protocol("адрес не из частной сети")
    } catch (e: IOException) {
        throw AirOsError.Unreachable(e)
    }
}
