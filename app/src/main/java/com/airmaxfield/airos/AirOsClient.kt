package com.airmaxfield.airos

sealed class AirOsError(msg: String, cause: Throwable? = null) : Exception(msg, cause) {
    class BadCredentials : AirOsError("Неверный логин или пароль")
    class Unreachable(cause: Throwable?) : AirOsError("Пушка не отвечает", cause)
    class Protocol(detail: String) : AirOsError("Непонятный ответ пушки: $detail")
    /** Пушка в заводском состоянии — нужен первый вход (этап 4). */
    class FactoryDefault : AirOsError("Пушка в заводском состоянии")
}

interface AirOsClient {
    val version: AirOsVersion
    suspend fun login()
    /** Сам делает один повторный вход при истёкшей сессии. */
    suspend fun status(): DeviceStatus
    /** Путь → тело ответа, для диагностики. */
    suspend fun rawDump(): Map<String, String>
}
