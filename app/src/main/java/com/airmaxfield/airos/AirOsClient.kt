package com.airmaxfield.airos

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    suspend fun stations(): List<StationInfo> = throw AirOsError.Protocol("станции не поддерживаются")
    /** Обзор эфира. На время обзора связь с пушкой может прерываться. */
    suspend fun survey(): List<SurveyEntry> = throw AirOsError.Protocol("обзор эфира не поддерживается")
    /** Полный system.cfg. Бросает Protocol, если пришла не конфигурация. */
    suspend fun downloadConfig(): String = throw AirOsError.Protocol("скачивание конфигурации не поддерживается")
    suspend fun reboot(): Unit = throw AirOsError.Protocol("перезагрузка не поддерживается")
}

/** Все обращения к пушке по очереди: опрос и инструменты не мешают друг другу (одна сессия). */
class SerializedClient(private val inner: AirOsClient) : AirOsClient {
    private val mutex = Mutex()
    override val version get() = inner.version
    override suspend fun login() = mutex.withLock { inner.login() }
    override suspend fun status() = mutex.withLock { inner.status() }
    override suspend fun rawDump() = mutex.withLock { inner.rawDump() }
    override suspend fun stations() = mutex.withLock { inner.stations() }
    override suspend fun survey() = mutex.withLock { inner.survey() }
    override suspend fun downloadConfig() = mutex.withLock { inner.downloadConfig() }
    override suspend fun reboot() = mutex.withLock { inner.reboot() }
}
