package com.airmaxfield.ui.tools

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.AirOsError
import com.airmaxfield.airos.ConfigBackup
import com.airmaxfield.airos.StationInfo
import com.airmaxfield.airos.SurveyEntry
import com.airmaxfield.data.BackupStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

data class StationsUi(val list: List<StationInfo>? = null, val error: String? = null)

data class SurveyUi(
    val running: Boolean = false,
    val entries: List<SurveyEntry>? = null,
    val error: String? = null,
    /** Связь прервалась на время обзора — можно попробовать забрать результаты ещё раз. */
    val canRetry: Boolean = false,
)

data class ToolsUi(
    val backups: List<File> = emptyList(),
    val busy: String? = null,
    val message: String? = null,
)

/** Станции и инструменты одной сессии. */
class ToolsController(
    private val client: AirOsClient,
    private val host: String,
    private val hostname: () -> String?,
    private val store: BackupStore?,
    private val scope: CoroutineScope,
) {
    private val _stations = MutableStateFlow(StationsUi())
    val stations: StateFlow<StationsUi> = _stations.asStateFlow()
    private val _survey = MutableStateFlow(SurveyUi())
    val survey: StateFlow<SurveyUi> = _survey.asStateFlow()
    private val _tools = MutableStateFlow(ToolsUi())
    val tools: StateFlow<ToolsUi> = _tools.asStateFlow()
    private var stationsJob: Job? = null
    /** Все задачи сессии — дочерние; close() отменяет и отложенные (перезагрузку, обзор). */
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val cs = scope + job

    fun startStations() {
        if (stationsJob?.isActive == true) return
        stationsJob = cs.launch {
            while (isActive) {
                try {
                    _stations.value = StationsUi(client.stations(), null)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    _stations.update { it.copy(error = e.message ?: "Ошибка") }
                }
                delay(2000)
            }
        }
    }

    fun stopStations() { stationsJob?.cancel(); stationsJob = null }

    fun runSurvey() {
        if (_survey.value.running) return
        _survey.value = _survey.value.copy(running = true, error = null, canRetry = false)
        cs.launch {
            try {
                _survey.value = SurveyUi(entries = client.survey())
            } catch (e: CancellationException) { throw e } catch (e: AirOsError.Unreachable) {
                _survey.update {
                    it.copy(running = false, canRetry = true,
                        error = "Связь прервалась на время обзора. Подождите несколько секунд и нажмите «Получить результаты».")
                }
            } catch (e: Exception) {
                _survey.update { it.copy(running = false, error = e.message ?: "Ошибка") }
            }
        }
    }

    fun refreshBackups() {
        _tools.update { t -> t.copy(backups = store?.list().orEmpty().filter { ConfigBackup.belongsTo(it.name, host) }) }
    }

    fun saveConfig() {
        if (store == null) { message("Хранилище недоступно"); return }
        _tools.update { it.copy(busy = "Скачиваю конфигурацию…") }
        cs.launch {
            try {
                val text = client.downloadConfig()
                val f = withContext(Dispatchers.IO) { store.save(ConfigBackup.fileName(hostname(), host, Date()), text) }
                refreshBackups()
                message("Копия сохранена: ${f.name}")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                message("Не удалось: ${e.message}")
            } finally {
                _tools.update { it.copy(busy = null) }
            }
        }
    }

    fun deleteBackup(f: File) { store?.delete(f); refreshBackups() }

    fun reboot() {
        _tools.update { it.copy(busy = "Отправляю команду перезагрузки…") }
        cs.launch {
            try {
                client.reboot()
                message("Пушка перезагружается. Связь вернётся примерно через минуту.")
            } catch (e: CancellationException) { throw e } catch (e: AirOsError.Unreachable) {
                // Пушка могла уйти в перезагрузку, не успев ответить.
                message("Команда отправлена, пушка не ответила — вероятно, уже перезагружается.")
            } catch (e: Exception) {
                message("Не удалось перезагрузить: ${e.message}")
            } finally {
                _tools.update { it.copy(busy = null) }
            }
        }
    }

    fun message(text: String?) = _tools.update { it.copy(message = text) }

    fun close() { stopStations(); job.cancel() }
}
