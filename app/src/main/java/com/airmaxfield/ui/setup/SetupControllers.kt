package com.airmaxfield.ui.setup

import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.SerializedClient
import com.airmaxfield.airos.SystemConfig
import com.airmaxfield.apply.ApplyState
import com.airmaxfield.apply.BridgePlanner
import com.airmaxfield.apply.BridgeProfile
import com.airmaxfield.apply.BridgeRole
import com.airmaxfield.apply.FirstLogin
import com.airmaxfield.apply.SafeApply
import com.airmaxfield.data.SavedDevice
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
import java.util.UUID

/** Подключение к пушке: (адрес, логин, пароль) → вошедший клиент. */
typealias Connector = suspend (host: String, user: String, pass: String) -> AirOsClient

/** Подключается при первой удачной попытке и запоминает клиента (для проверки связи в тесте). */
fun lazyTarget(connect: Connector, host: String, user: String, pass: String): suspend () -> AirOsClient {
    var cached: AirOsClient? = null
    return { cached ?: connect(host, user, pass).also { cached = it } }
}

/**
 * Первый вход в пушку в заводском состоянии: новый логин и пароль, страна, принятие условий.
 * Пишется через тест-режим; связь проверяется уже с новым паролем — если пушка его не приняла,
 * сохранить нельзя, и она откатится сама.
 */
class FirstLoginController(
    private val client: AirOsClient,
    private val host: String,
    private val connect: Connector,
    private val backup: suspend (String) -> Unit,
    scope: CoroutineScope,
    private val onDone: (user: String, pass: String) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val cs = scope + job

    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors: StateFlow<List<String>> = _errors.asStateFlow()
    private val _apply = MutableStateFlow<SafeApply?>(null)
    val apply: StateFlow<SafeApply?> = _apply.asStateFlow()

    fun submit(user: String, pass: String, pass2: String, country: String, accepted: Boolean) {
        val errs = FirstLogin.validate(user.trim(), pass, pass2, country.trim(), accepted)
        _errors.value = errs
        if (errs.isNotEmpty() || _apply.value != null) return
        val u = user.trim()
        val sa = SafeApply(client, backup, cs, clock = clock, target = lazyTarget(connect, host, u, pass))
        _apply.value = sa
        cs.launch {
            try {
                val next = FirstLogin.plan(SystemConfig.parse(client.downloadConfig()), u, pass, country.trim())
                sa.prepare(next.raw)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _errors.value = listOf("Не удалось прочитать конфигурацию: ${e.message}")
                sa.close()
                _apply.value = null
                return@launch
            }
            if (sa.state.first { it.finished } is ApplyState.Confirmed) onDone(u, pass)
        }
    }

    fun dismissApply() {
        val sa = _apply.value ?: return
        if (sa.state.value.finished) { sa.close(); _apply.value = null }
    }

    fun close() = job.cancel()
}

enum class WizardStep { PROFILE, SIDE_A, SIDE_B, DONE }

data class WizardUi(
    val step: WizardStep = WizardStep.PROFILE,
    val profile: BridgeProfile? = null,
    val pairId: String = UUID.randomUUID().toString(),
    val errors: List<String> = emptyList(),
    val connecting: Boolean = false,
    val apply: SafeApply? = null,
)

/**
 * Мастер моста (спецификация 5.9): параметры один раз → пушка A станет AP PtP, пушка B — Station.
 * Стороны настраиваются по очереди, каждая через тест-режим; после подтверждения сторона
 * сохраняется в списке пушек со связью пары (pairId).
 */
class BridgeWizardController(
    scope: CoroutineScope,
    private val connect: Connector,
    private val backup: suspend (host: String, text: String) -> Unit,
    private val saveDevice: (SavedDevice) -> Unit,
    private val saveProfile: (BridgeProfile) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val cs = scope + job
    private val _ui = MutableStateFlow(WizardUi())
    val ui: StateFlow<WizardUi> = _ui.asStateFlow()

    fun setProfile(p: BridgeProfile) {
        val errs = BridgePlanner.validate(p)
        if (errs.isNotEmpty()) { _ui.update { it.copy(errors = errs) }; return }
        saveProfile(p)
        _ui.update { it.copy(profile = p, errors = emptyList(), step = WizardStep.SIDE_A) }
    }

    fun back() = _ui.update {
        if (it.apply != null || it.connecting) it
        else it.copy(errors = emptyList(), step = when (it.step) {
            WizardStep.SIDE_A -> WizardStep.PROFILE
            WizardStep.SIDE_B -> WizardStep.SIDE_A
            else -> it.step
        })
    }

    /** Текущая сторона: подключиться к пушке по [host] и записать её часть моста. */
    fun applySide(host: String, user: String, pass: String) {
        val st = _ui.value
        val p = st.profile ?: return
        val role = when (st.step) { WizardStep.SIDE_A -> BridgeRole.AP; WizardStep.SIDE_B -> BridgeRole.STATION; else -> return }
        if (st.connecting || st.apply != null) return
        _ui.update { it.copy(connecting = true, errors = emptyList()) }
        cs.launch {
            val sa: SafeApply
            val next: SystemConfig
            val client: AirOsClient
            try {
                client = SerializedClient(connect(host.trim(), user.trim(), pass))
                next = BridgePlanner.plan(p, role, SystemConfig.parse(client.downloadConfig()))
                sa = SafeApply(client, { backup(host.trim(), it) }, cs, clock = clock,
                    target = lazyTarget(connect, p.ipFor(role), p.user, p.pass))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _ui.update { it.copy(connecting = false, errors = listOf("Не удалось подключиться: ${e.message}")) }
                return@launch
            }
            _ui.update { it.copy(connecting = false, apply = sa) }
            sa.prepare(next.raw)
            if (sa.state.first { it.finished } is ApplyState.Confirmed) {
                val side = if (role == BridgeRole.AP) "A" else "B"
                saveDevice(SavedDevice(UUID.randomUUID().toString(), "${p.name} $side", p.ipFor(role), p.user, p.pass, st.pairId))
            }
        }
    }

    /** Закрыть окно записи; после успеха — следующий шаг. */
    fun dismissApply() {
        val st = _ui.value
        val sa = st.apply ?: return
        val end = sa.state.value
        if (!end.finished) return
        sa.close()
        _ui.update {
            it.copy(apply = null, step = if (end is ApplyState.Confirmed) when (it.step) {
                WizardStep.SIDE_A -> WizardStep.SIDE_B
                WizardStep.SIDE_B -> WizardStep.DONE
                else -> it.step
            } else it.step)
        }
    }

    fun close() = job.cancel()
}
