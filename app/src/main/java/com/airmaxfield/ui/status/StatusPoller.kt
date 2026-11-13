package com.airmaxfield.ui.status

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.AirOsError
import com.airmaxfield.airos.DeviceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StatusUi(
    /** Последний успешный статус. */
    val status: DeviceStatus? = null,
    /** Сигнал dBm, последние 60 точек. */
    val history: List<Int> = emptyList(),
    /** 0, если последний опрос успешен. */
    val secondsSinceUpdate: Int = 0,
    val error: AirOsError? = null,
    /** true после BadCredentials — опрос остановлен. */
    val stopped: Boolean = false,
    /** Время (clock) последнего успешного опроса — для расчёта «нет данных N с» в UI по тикеру. */
    val lastOkAt: Long? = null,
)

/** Сколько целых секунд нет свежих данных; null — данных ещё не было. */
fun staleSeconds(lastOkAt: Long?, now: Long): Int? = lastOkAt?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) }

/** Опрашивает пушку раз в [periodMs]; при ошибке сохраняет последний статус. */
class StatusPoller(
    private val client: AirOsClient,
    private val scope: CoroutineScope,
    private val periodMs: Long = 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(StatusUi())
    val state: StateFlow<StatusUi> = _state.asStateFlow()
    private var job: Job? = null
    private var lastOkAt: Long? = null

    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(stopped = false) }
        job = scope.launch {
            while (isActive) {
                val started = clock()
                if (!pollOnce()) break
                delay((periodMs - (clock() - started)).coerceAtLeast(0))
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /** false — опрос надо прекратить. */
    private suspend fun pollOnce(): Boolean {
        try {
            val s = client.status()
            lastOkAt = clock()
            _state.update { ui ->
                ui.copy(
                    status = s,
                    history = s.signalDbm?.let { (ui.history + it).takeLast(HISTORY) } ?: ui.history,
                    secondsSinceUpdate = 0, error = null, lastOkAt = lastOkAt,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AirOsError) {
            val since = lastOkAt?.let { ((clock() - it) / 1000).toInt() } ?: 0
            val stop = e is AirOsError.BadCredentials
            _state.update { it.copy(error = e, secondsSinceUpdate = since, stopped = stop) }
            return !stop
        } catch (e: Exception) {
            val since = lastOkAt?.let { ((clock() - it) / 1000).toInt() } ?: 0
            _state.update { it.copy(error = AirOsError.Protocol(e.message ?: e.javaClass.simpleName), secondsSinceUpdate = since) }
        }
        return true
    }

    companion object { const val HISTORY = 60 }
}
