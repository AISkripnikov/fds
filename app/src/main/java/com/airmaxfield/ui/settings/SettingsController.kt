package com.airmaxfield.ui.settings

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.CfgChange
import com.airmaxfield.airos.SystemConfig
import com.airmaxfield.airos.isEditableKey
import com.airmaxfield.apply.ApplyState
import com.airmaxfield.apply.Management
import com.airmaxfield.apply.SafeApply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Конфигурация пушки: чтение, черновик правок и безопасное применение через [SafeApply].
 *
 * @param backup сохраняет текущую конфигурацию перед записью; бросает, если сохранить нельзя.
 * @param reconnect клиент для нового адреса управления (после смены IP); null — не умеем.
 * @param onConfirmed изменения сохранены навсегда; аргумент — новый адрес, если он сменился.
 */
class SettingsController(
    private val client: AirOsClient,
    private val scope: CoroutineScope,
    private val host: String = "",
    private val backup: suspend (String) -> Unit = { error("резервные копии недоступны") },
    private val reconnect: ((String) -> (suspend () -> AirOsClient))? = null,
    private val onConfirmed: (newHost: String?) -> Unit = {},
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val _config = MutableStateFlow<SystemConfig?>(null)
    val config: StateFlow<SystemConfig?> = _config.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Черновик: ключ → новое значение. */
    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = _drafts.asStateFlow()

    /** Текущая запись (null — записи нет). */
    private val _apply = MutableStateFlow<SafeApply?>(null)
    val apply: StateFlow<SafeApply?> = _apply.asStateFlow()

    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val cs = scope + job

    /** Запись возможна только с тест-режимом (без него отката нет — спецификация 5.7). */
    val writable: Boolean get() = client.hasTestMode

    fun canEdit(key: String): Boolean = writable && isEditableKey(key)

    fun load() {
        cs.launch {
            _loading.value = true
            _error.value = null
            try {
                _config.value = SystemConfig.parse(client.downloadConfig())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _error.value = "Не удалось загрузить конфигурацию: ${e.message}"
                _config.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    fun retry() = load()

    fun setDraft(key: String, value: String) {
        if (!canEdit(key) || value.contains('\n')) return
        val orig = _config.value?.get(key)
        _drafts.update { if (value == orig) it - key else it + (key to value) }
    }

    fun clearDraft(key: String) = _drafts.update { it - key }
    fun clearDrafts() { _drafts.value = emptyMap() }

    /** Конфигурация с правками черновика. */
    fun pending(): SystemConfig? = _config.value?.with(_drafts.value)

    fun changes(): List<CfgChange> {
        val c = _config.value ?: return emptyList()
        return c.diff(pending()!!)
    }

    /** Новый адрес управления, если черновик его меняет. */
    fun newHost(): String? {
        val c = _config.value ?: return null
        return Management.newHost(c, pending()!!, host)
    }

    /** Запустить запись черновика в тест-режиме. */
    fun applyDrafts(testSeconds: Int = SafeApply.DEFAULT_TEST_SECONDS) {
        if (_apply.value != null || _drafts.value.isEmpty()) return
        val next = pending() ?: return
        val moved = newHost()
        val target: suspend () -> AirOsClient =
            if (moved != null && reconnect != null) reconnect.invoke(moved) else ({ client })
        val sa = SafeApply(client, backup, cs, clock = clock, target = target)
        _apply.value = sa
        cs.launch {
            sa.prepare(next.raw, testSeconds)
            val end = sa.state.first { it.finished }
            when (end) {
                is ApplyState.Confirmed -> { clearDrafts(); if (moved == null) load(); onConfirmed(moved) }
                is ApplyState.RolledBack -> load()
                else -> Unit
            }
        }
    }

    /** Закрыть окно записи после завершения. */
    fun dismissApply() {
        val sa = _apply.value ?: return
        if (sa.state.value.finished) { sa.close(); _apply.value = null }
    }

    fun close() { _apply.value?.close(); job.cancel() }
}
