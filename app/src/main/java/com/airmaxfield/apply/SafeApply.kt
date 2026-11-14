package com.airmaxfield.apply

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.AirOsError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

sealed interface ApplyState {
    data object Idle : ApplyState
    data object BackingUp : ApplyState
    data object Uploading : ApplyState
    /** Пушка в тест-режиме. [reachable] — пушка снова отвечает; только тогда можно сохранить. */
    data class Testing(val remainingSec: Int, val totalSec: Int, val reachable: Boolean, val error: String? = null) : ApplyState
    data object Confirming : ApplyState
    /** Команда отката отправляется; таймер пушки продолжает идти. */
    data class RollingBack(val remainingSec: Int, val totalSec: Int) : ApplyState
    data object Confirmed : ApplyState
    data class RolledBack(val reason: String) : ApplyState
    /** Ошибка до тест-режима: на пушке ничего не поменялось. */
    data class Failed(val message: String) : ApplyState

    val finished: Boolean get() = this is Confirmed || this is RolledBack || this is Failed
}

/**
 * Безопасная запись одной пушки: резервная копия → загрузка в тест-режиме → ожидание связи →
 * «Сохранить навсегда» (только при связи) или автооткат по таймеру.
 *
 * @param target клиент, через который проверять связь и подтверждать после применения
 *   (другой, если сменились IP или пароль). По умолчанию — тот же [client].
 * @param clock монотонные миллисекунды (в тестах — виртуальное время).
 */
class SafeApply(
    private val client: AirOsClient,
    private val backup: suspend (String) -> Unit,
    scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pollMs: Long = 2000,
    private val target: suspend () -> AirOsClient = { client },
) {
    private val _state = MutableStateFlow<ApplyState>(ApplyState.Idle)
    val state: StateFlow<ApplyState> = _state.asStateFlow()

    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val cs = scope + job
    private var monitor: Job? = null
    @Volatile private var deadline = 0L
    @Volatile private var lastGood: AirOsClient? = null

    /** Запуск в фоне (для UI). */
    fun start(newConfig: String, testSeconds: Int = DEFAULT_TEST_SECONDS) {
        cs.launch { prepare(newConfig, testSeconds) }
    }

    /**
     * Резервная копия и загрузка. true — пушка в тест-режиме, идёт отсчёт.
     * Возвращается сразу после включения теста; отсчёт и проверка связи идут в фоне.
     */
    suspend fun prepare(newConfig: String, testSeconds: Int = DEFAULT_TEST_SECONDS): Boolean {
        if (_state.value !is ApplyState.Idle) return false
        if (!client.hasTestMode) {
            _state.value = ApplyState.Failed("У пушки нет тест-режима — запись без автоотката запрещена")
            return false
        }
        _state.value = ApplyState.BackingUp
        try {
            backup(client.downloadConfig())
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.value = ApplyState.Failed("Не удалось снять резервную копию — запись отменена: ${e.message}")
            return false
        }
        _state.value = ApplyState.Uploading
        val total = client.testDurationFor(testSeconds)
        try {
            client.uploadConfig(newConfig, testSeconds)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.value = ApplyState.Failed("Пушка не приняла конфигурацию, ничего не изменилось: ${e.message}")
            return false
        }
        deadline = clock() + total * 1000L
        _state.value = ApplyState.Testing(total, total, reachable = false)
        monitor = cs.launch {
            launch { tick() }
            launch { probeLoop() }
        }
        return true
    }

    private fun remaining(): Int = ((deadline - clock() + 999) / 1000).toInt().coerceAtLeast(0)

    private suspend fun tick() {
        while (true) {
            val left = remaining()
            if (left <= 0) {
                _state.update { if (it is ApplyState.Testing || it is ApplyState.RollingBack) ApplyState.RolledBack(EXPIRED) else it }
                if (_state.value.finished) { monitor?.cancel(); return }
            } else {
                _state.update {
                    when (it) {
                        is ApplyState.Testing -> it.copy(remainingSec = left)
                        is ApplyState.RollingBack -> it.copy(remainingSec = left)
                        else -> it
                    }
                }
            }
            delay(1000)
        }
    }

    private suspend fun probeLoop() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            val ok = try {
                val c = target()
                c.status()
                lastGood = c
                true
            } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
            _state.update { if (it is ApplyState.Testing) it.copy(reachable = ok) else it }
            delay(pollMs)
        }
    }

    /** «Сохранить навсегда». Доступно только в тесте и при связи с пушкой. */
    suspend fun confirmNow(minRemainingSec: Int = MIN_CONFIRM_SEC): Boolean {
        val s = _state.value as? ApplyState.Testing ?: return false
        if (!s.reachable) return false
        if (remaining() < minRemainingSec) {
            _state.update { if (it is ApplyState.Testing) it.copy(error = "Слишком поздно — пушка вот-вот откатится") else it }
            return false
        }
        // Атомарный переход: если параллельно начался откат, подтверждать нельзя.
        var won = false
        _state.update { cur -> if (cur is ApplyState.Testing && cur.reachable) { won = true; ApplyState.Confirming } else { won = false; cur } }
        if (!won) return false
        return try {
            (lastGood ?: client).confirmTest()
            monitor?.cancel()
            _state.value = ApplyState.Confirmed
            true
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.update {
                if (it is ApplyState.Confirming) ApplyState.Testing(remaining(), s.totalSec, reachable = false,
                    error = "Не удалось сохранить: ${e.message}. Пушка откатится по таймеру, если не повторить.") else it
            }
            false
        }
    }

    /** Секунд до автоотката (0 — не в тесте). */
    val remainingSec: Int get() = if (_state.value is ApplyState.Testing) remaining() else 0

    fun confirm() { cs.launch { confirmNow() } }

    /** «Откатить сейчас». Если команда не дошла — откат всё равно произойдёт по таймеру. */
    suspend fun rollbackNow() {
        // Пока идёт команда отката, «Сохранить» недоступна (состояние уже не Testing).
        var won = false
        _state.update { cur ->
            if (cur is ApplyState.Testing) { won = true; ApplyState.RollingBack(cur.remainingSec, cur.totalSec) } else { won = false; cur }
        }
        if (!won) return
        fun backToTesting(msg: String) = _state.update {
            if (it is ApplyState.RollingBack) ApplyState.Testing(it.remainingSec, it.totalSec, reachable = false, error = msg) else it
        }
        try {
            (lastGood ?: client).cancelTest()
            monitor?.cancel()
            _state.update { if (it is ApplyState.RollingBack) ApplyState.RolledBack(MANUAL) else it }
        } catch (e: CancellationException) { throw e } catch (e: AirOsError.Unreachable) {
            backToTesting("Команда отката не дошла — пушка вернёт прежние настройки сама по таймеру")
        } catch (e: Exception) {
            backToTesting("Не удалось откатить сразу (${e.message}) — откат произойдёт по таймеру")
        }
    }

    fun rollback() { cs.launch { rollbackNow() } }

    /** Клиент, через который пушка ответила последней (после смены IP — новый). */
    val reachedVia: AirOsClient? get() = lastGood

    fun close() = job.cancel()

    companion object {
        const val DEFAULT_TEST_SECONDS = 180
        const val MIN_CONFIRM_SEC = 3
        const val EXPIRED = "Время вышло — пушка вернула прежние настройки сама"
        const val MANUAL = "Откат по команде: пушка перезагружается с прежними настройками (около минуты)"
    }
}
