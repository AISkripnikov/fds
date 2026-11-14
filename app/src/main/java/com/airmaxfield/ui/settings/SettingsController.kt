package com.airmaxfield.ui.settings

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.SystemConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Контроллер для загрузки и управления конфигурацией устройства.
 * Все настройки только для чтения на этапе 3.
 */
class SettingsController(
    private val client: AirOsClient,
    private val scope: CoroutineScope,
) {
    private val _config = MutableStateFlow<SystemConfig?>(null)
    val config: StateFlow<SystemConfig?> = _config.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Загрузить конфигурацию с устройства.
     */
    fun load() {
        scope.launch {
            _loading.value = true
            _error.value = null
            try {
                val raw = client.downloadConfig()
                val parsed = SystemConfig.parse(raw)
                _config.value = parsed
            } catch (e: Exception) {
                _error.value = "Не удалось загрузить конфигурацию: ${e.message}"
                _config.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Повторить загрузку после ошибки.
     */
    fun retry() {
        load()
    }

    /**
     * Закрыть контроллер (нет фоновых задач для остановки).
     */
    fun close() {
        // Нет polling или фоновых задач
    }
}
