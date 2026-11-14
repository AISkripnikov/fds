package com.airmaxfield.apply

import com.airmaxfield.airos.AirOsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Изменение линка на обеих сторонах моста (спецификация 5.10).
 * Телефон подключён к ближней пушке, дальняя — через мост.
 *
 * 1. Обе пушки должны уметь тест-режим и отвечать.
 * 2. Сначала дальняя ([FAR_SECONDS] с), потом ближняя ([NEAR_SECONDS] с): дальняя
 *    откатывается позже, с запасом на восстановление связи.
 * 3. «Сохранить на обеих» — только когда обе снова отвечают; порядок дальняя → ближняя.
 * 4. Линк не поднялся — ничего не делаем: обе откатятся по своим таймерам.
 */
class BothEndsOrchestrator(
    private val near: AirOsClient,
    private val far: AirOsClient,
    backupNear: suspend (String) -> Unit,
    backupFar: suspend (String) -> Unit,
    scope: CoroutineScope,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    pollMs: Long = 2000,
    nearTarget: suspend () -> AirOsClient = { near },
    farTarget: suspend () -> AirOsClient = { far },
) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val cs = scope + job

    val nearApply = SafeApply(near, backupNear, cs, clock, pollMs, nearTarget)
    val farApply = SafeApply(far, backupFar, cs, clock, pollMs, farTarget)

    /** Почему изменение не начато или прервано (null — всё идёт по плану). */
    private val _problem = MutableStateFlow<String?>(null)
    val problem: StateFlow<String?> = _problem.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun canConfirm(): Boolean {
        val f = farApply.state.value as? ApplyState.Testing ?: return false
        val n = nearApply.state.value as? ApplyState.Testing ?: return false
        return f.reachable && n.reachable
    }

    fun start(nearCfg: String, farCfg: String) { cs.launch { prepare(nearCfg, farCfg) } }

    /** true — обе пушки в тест-режиме. */
    suspend fun prepare(nearCfg: String, farCfg: String): Boolean {
        _busy.value = true
        try {
            if (!near.hasTestMode || !far.hasTestMode) {
                return refuse("Тест-режим недоступен на " + (if (!far.hasTestMode) "дальней" else "ближней") +
                    " пушке — менять линк на обеих сторонах без автоотката запрещено")
            }
            val farSec = far.testDurationFor(FAR_SECONDS)
            val nearSec = near.testDurationFor(NEAR_SECONDS)
            if (farSec < nearSec + RECOVERY_SECONDS) {
                return refuse("Таймер дальней пушки ($farSec с) не больше таймера ближней ($nearSec с) с запасом " +
                    "$RECOVERY_SECONDS с — дальняя может откатиться раньше и линк не восстановится")
            }
            if (!reachable(near)) return refuse("Ближняя пушка не отвечает")
            if (!reachable(far)) return refuse("Дальняя пушка не отвечает через мост — сначала поднимите линк")

            if (!farApply.prepare(farCfg, FAR_SECONDS)) {
                return refuse("Дальняя пушка: " + ((farApply.state.value as? ApplyState.Failed)?.message ?: "запись не началась"))
            }
            if (!nearApply.prepare(nearCfg, NEAR_SECONDS)) {
                // Дальняя уже в тесте. Линк, скорее всего, уже разошёлся — откат дальней дойдёт не всегда,
                // но таймер дальней вернёт её сама.
                farApply.rollbackNow()
                return refuse("Ближняя пушка не приняла изменения: " +
                    ((nearApply.state.value as? ApplyState.Failed)?.message ?: "ошибка") +
                    ". Дальняя вернёт прежние настройки сама по таймеру.")
            }
            return true
        } finally {
            _busy.value = false
        }
    }

    private suspend fun reachable(c: AirOsClient) = try { c.status(); true } catch (e: CancellationException) { throw e } catch (e: Exception) { false }

    private fun refuse(msg: String): Boolean { _problem.value = msg; return false }

    /** «Сохранить на обеих»: дальняя, затем ближняя. */
    suspend fun confirmBoth(): Boolean {
        if (!canConfirm()) return false
        // Дальнюю сохраняем, только если ближняя точно успеет следом: иначе мост разойдётся навсегда.
        if (nearApply.remainingSec < NEAR_CONFIRM_MARGIN_SEC) {
            _problem.value = "У ближней пушки осталось меньше $NEAR_CONFIRM_MARGIN_SEC с — сохранять поздно. " +
                "Обе откатятся сами; повторите изменение."
            return false
        }
        if (!farApply.confirmNow()) {
            _problem.value = "Дальняя пушка не подтвердила — ничего не сохранено, обе откатятся по таймерам"
            return false
        }
        if (!nearApply.confirmNow()) {
            _problem.value = "Дальняя сохранена, а ближняя — нет. Нажмите «Сохранить» ещё раз, пока идёт отсчёт ближней, " +
                "иначе ближняя откатится и линк пропадёт."
            return false
        }
        _problem.value = null
        return true
    }

    /** Повтор подтверждения ближней, если дальняя уже сохранена. */
    suspend fun retryNear(): Boolean {
        if (farApply.state.value !is ApplyState.Confirmed || !nearApply.confirmNow()) return false
        _problem.value = null
        return true
    }

    /** После сохранения дальней откатывать ближнюю нельзя — мост разойдётся. */
    fun canRollback(): Boolean = farApply.state.value !is ApplyState.Confirmed

    fun confirm() {
        cs.launch {
            if (farApply.state.value is ApplyState.Confirmed) retryNear() else confirmBoth()
        }
    }

    /** «Откатить сейчас»: сначала дальняя (пока есть линк), потом ближняя. */
    suspend fun rollbackBoth() {
        if (!canRollback()) return
        farApply.rollbackNow()
        nearApply.rollbackNow()
    }

    fun rollback() { cs.launch { rollbackBoth() } }

    fun close() = job.cancel()

    companion object {
        const val FAR_SECONDS = 300
        const val NEAR_SECONDS = 180
        const val RECOVERY_SECONDS = 60
        /** Сколько секунд должно оставаться у ближней, чтобы начать «Сохранить на обеих». */
        const val NEAR_CONFIRM_MARGIN_SEC = 15
    }
}
