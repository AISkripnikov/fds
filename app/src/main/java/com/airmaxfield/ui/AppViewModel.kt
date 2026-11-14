package com.airmaxfield.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airmaxfield.airos.AirOsClient
import com.airmaxfield.airos.AirOsError
import com.airmaxfield.airos.DemoClient
import com.airmaxfield.airos.connect
import com.airmaxfield.data.AppPrefs
import com.airmaxfield.data.DeviceStore
import com.airmaxfield.data.SavedDevice
import com.airmaxfield.discovery.Discovered
import com.airmaxfield.discovery.UbntDiscovery
import com.airmaxfield.net.AndroidNetworks
import com.airmaxfield.net.BoundSocketFactory
import okhttp3.OkHttpClient
import com.airmaxfield.net.LocalNet
import com.airmaxfield.net.deviceHttpClient
import com.airmaxfield.net.inSameSubnet
import com.airmaxfield.ui.status.StatusPoller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Активное подключение к пушке. */
class Session(
    val title: String,
    val host: String,
    val client: AirOsClient,
    val poller: StatusPoller,
    val demo: Boolean,
    val user: String = "ubnt",
    val http: OkHttpClient? = null,
    /** Пароль держим только в памяти сессии — нужен для переподключения после смены IP. */
    val pass: String = "",
    val tools: com.airmaxfield.ui.tools.ToolsController,
    val settings: com.airmaxfield.ui.settings.SettingsController,
    /** Есть, если вошли с заводским ubnt/ubnt — нужен первый вход. */
    val firstLogin: com.airmaxfield.ui.setup.FirstLoginController? = null,
)

sealed interface ConnectState {
    data object Idle : ConnectState
    data object Busy : ConnectState
    data class Failed(val message: String, val badCredentials: Boolean) : ConnectState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    private val backups = runCatching { com.airmaxfield.data.BackupStore(java.io.File(app.filesDir, "backups")) }.getOrNull()

    /** Экспорт резервной копии в выбранный пользователем файл. */
    fun exportBackup(uri: android.net.Uri, f: java.io.File) {
        viewModelScope.launch {
            val ok = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(f.readBytes()) }
                        ?: error("не удалось открыть файл")
                }
            }
            _toast.value = if (ok.isSuccess) "Копия экспортирована" else "Не удалось экспортировать: ${ok.exceptionOrNull()?.message}"
        }
    }
    // Если ключ шифрования потерян (восстановление/перенос телефона) — файл не расшифровать:
    // удаляем его и создаём заново, иначе сохранение пушек пропало бы навсегда.
    private val store = runCatching { DeviceStore(app) }.getOrNull()
        ?: runCatching { app.deleteSharedPreferences("devices"); DeviceStore(app) }.getOrNull()
    private val nets = AndroidNetworks(app)

    private val _devices = MutableStateFlow(store?.all().orEmpty())
    val devices: StateFlow<List<SavedDevice>> = _devices.asStateFlow()

    private val _networks = MutableStateFlow<List<LocalNet>>(emptyList())
    val networks: StateFlow<List<LocalNet>> = _networks.asStateFlow()

    private val _discovered = MutableStateFlow<List<Discovered>>(emptyList())
    val discovered: StateFlow<List<Discovered>> = _discovered.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _connect = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val connectState: StateFlow<ConnectState> = _connect.asStateFlow()

    private val _light = MutableStateFlow(prefs.lightTheme)
    val lightTheme: StateFlow<Boolean> = _light.asStateFlow()

    val storeAvailable: Boolean get() = store != null

    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworks()
        override fun onLost(network: Network) { refreshNetworks(); dropStaleConnections() }
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = refreshNetworks()
        override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) = refreshNetworks()
    }

    init {
        refreshNetworks()
        runCatching { cm.registerNetworkCallback(NetworkRequest.Builder().build(), netCallback) }
    }

    fun refreshNetworks() { _networks.value = runCatching { nets.list().map { it.first } }.getOrDefault(emptyList()) }

    /** Предупреждение, если телефон не в подсети пушки (null — всё в порядке). */
    fun subnetWarning(host: String): String? {
        val local = _networks.value.filter { it.kind == LocalNet.Kind.WIFI || it.kind == LocalNet.Kind.ETHERNET }
        if (local.isEmpty()) return "Телефон не подключён ни к Wi‑Fi, ни к кабелю. Подключитесь к сети пушки."
        val ok = local.any { n -> n.ipv4 != null && n.prefix != null && inSameSubnet(n.ipv4, n.prefix, host) }
        return if (ok) null else
            "Телефон не в сети пушки. Для сброшенной пушки (192.168.1.20) задайте телефону адрес 192.168.1.100, маска 255.255.255.0."
    }

    fun toggleTheme() { val v = !_light.value; prefs.lightTheme = v; _light.value = v }

    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch {
            try {
                val local = runCatching { nets.list() }.getOrDefault(emptyList())
                    .filter { it.first.kind == LocalNet.Kind.WIFI || it.first.kind == LocalNet.Kind.ETHERNET }
                val result = mutableMapOf<String, Discovered>()
                for ((_, network) in local) {
                    runCatching { UbntDiscovery { network.bindSocket(it) }.scan() }.getOrNull()?.forEach { result[it.ip] = it }
                }
                _discovered.value = result.values.sortedBy { it.ip }
            } finally {
                _scanning.value = false
            }
        }
    }

    fun connect(host: String, user: String, pass: String, save: Boolean, name: String?) {
        if (_connect.value == ConnectState.Busy) return
        _connect.value = ConnectState.Busy
        viewModelScope.launch {
            try {
                val h = host.trim()
                if (nets.networkFor(h) == null) {
                    _connect.value = ConnectState.Failed("Нет Wi‑Fi или кабеля к пушке. Через мобильную сеть приложение не подключается.", false)
                    return@launch
                }
                // Сеть выбирается заново на каждом соединении: переживает перезагрузку пушки и
                // переподключение кабеля, и никогда не уходит в мобильную сеть.
                val http = httpFor(h)
                val client = connect(h, user.trim(), pass, http)
                if (save) {
                    val existing = _devices.value.firstOrNull { it.host == host.trim() }
                    store?.save(SavedDevice(existing?.id ?: UUID.randomUUID().toString(),
                        name?.takeIf { it.isNotBlank() } ?: host.trim(), host.trim(), user.trim(), pass))
                    _devices.value = store?.all().orEmpty()
                }
                startSession(name?.takeIf { it.isNotBlank() } ?: h, h, client, demo = false, user = user.trim(), http = http, pass = pass,
                    factory = com.airmaxfield.apply.FirstLogin.isFactoryLogin(user.trim(), pass))
                _connect.value = ConnectState.Idle
            } catch (e: AirOsError) {
                _connect.value = ConnectState.Failed(e.message ?: "Ошибка", e is AirOsError.BadCredentials)
            } catch (e: Exception) {
                _connect.value = ConnectState.Failed("Ошибка: ${e.message ?: e.javaClass.simpleName}", false)
            }
        }
    }

    fun demo() = startSession("Демо-режим", "демо", DemoClient(), demo = true)

    /** HTTP-клиент, привязанный к сети телефона, в которой лежит [host]. */
    private fun httpFor(host: String) = deviceHttpClient(BoundSocketFactory { nets.networkFor(host)?.second?.socketFactory })

    /** Сохраняет конфигурацию в резервные копии перед записью; бросает, если сохранить нельзя. */
    private fun backupFn(host: String, hostname: () -> String?): suspend (String) -> Unit = { text ->
        val store = backups ?: error("хранилище резервных копий недоступно")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store.save(com.airmaxfield.airos.ConfigBackup.fileName(hostname(), host, java.util.Date()), text)
        }
    }

    /** Клиент для нового адреса: подключается при первой удачной попытке и запоминается. */
    private fun reconnectFn(user: String, pass: String): (String) -> (suspend () -> AirOsClient) = { h ->
        var cached: AirOsClient? = null
        val fn: suspend () -> AirOsClient = { cached ?: connect(h, user, pass, httpFor(h)).also { cached = it } }
        fn
    }

    private val connector: com.airmaxfield.ui.setup.Connector = { h, u, p -> connect(h, u, p, httpFor(h)) }

    /** Первый вход сохранён: запоминаем новые логин и пароль и переподключаемся с ними. */
    private fun afterFirstLogin(s: Session, user: String, pass: String) {
        val saved = _devices.value.firstOrNull { it.host == s.host }
        if (saved != null) {
            store?.save(saved.copy(user = user, pass = pass))
            _devices.value = store?.all().orEmpty()
        }
        _toast.value = "Новый логин и пароль сохранены на пушке"
        connect(s.host, user, pass, save = false, name = s.title)
    }

    // --- Мастер моста ---

    private val _profiles = MutableStateFlow(store?.profiles().orEmpty())
    val profiles: StateFlow<List<com.airmaxfield.apply.BridgeProfile>> = _profiles.asStateFlow()

    private val _wizard = MutableStateFlow<com.airmaxfield.ui.setup.BridgeWizardController?>(null)
    val wizard: StateFlow<com.airmaxfield.ui.setup.BridgeWizardController?> = _wizard.asStateFlow()

    fun openWizard() {
        closeSession()
        _wizard.value?.close()
        _wizard.value = com.airmaxfield.ui.setup.BridgeWizardController(
            viewModelScope, connector,
            backup = { host, text -> backupFn(host) { null }(text) },
            saveDevice = { d -> store?.save(d); _devices.value = store?.all().orEmpty() },
            saveProfile = { p -> store?.saveProfile(p); _profiles.value = store?.profiles().orEmpty() },
        )
    }

    fun closeWizard() { _wizard.value?.close(); _wizard.value = null }

    // --- Изменения на обеих сторонах моста ---

    private val _bothEnds = MutableStateFlow<com.airmaxfield.apply.BothEndsOrchestrator?>(null)
    val bothEnds: StateFlow<com.airmaxfield.apply.BothEndsOrchestrator?> = _bothEnds.asStateFlow()

    /** Вторая пушка моста для текущей сессии (по pairId сохранённых пушек). */
    fun pairOf(s: Session): SavedDevice? {
        val me = _devices.value.firstOrNull { it.host == s.host } ?: return null
        val pid = me.pairId ?: return null
        return _devices.value.firstOrNull { it.pairId == pid && it.id != me.id }
    }

    /** Подготовка изменения на обеих сторонах уже идёт (до появления окна) — второй запуск запрещён. */
    private var relinkStarting = false

    fun startRelink(s: Session, link: com.airmaxfield.apply.LinkParams) {
        if (_bothEnds.value != null || relinkStarting) return
        val far = pairOf(s) ?: run { _toast.value = "Эта пушка не связана в пару"; return }
        relinkStarting = true
        _toast.value = "Подключаюсь к дальней пушке ${far.host}…"
        viewModelScope.launch {
            try {
                val farClient = com.airmaxfield.airos.SerializedClient(connector(far.host, far.user, far.pass))
                val nearCfg = com.airmaxfield.airos.SystemConfig.parse(s.client.downloadConfig())
                val farCfg = com.airmaxfield.airos.SystemConfig.parse(farClient.downloadConfig())
                val P = com.airmaxfield.apply.BridgePlanner
                val o = com.airmaxfield.apply.BothEndsOrchestrator(
                    s.client, farClient,
                    backupNear = { text -> backupFn(s.host) { s.poller.state.value.status?.hostname }(text) },
                    backupFar = { text -> backupFn(far.host) { far.name }(text) },
                    scope = viewModelScope,
                )
                _bothEnds.value = o
                o.start(P.relink(nearCfg, P.roleOf(nearCfg), link).raw, P.relink(farCfg, P.roleOf(farCfg), link).raw)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                _toast.value = "Дальняя пушка (${far.host}) не отвечает через мост: ${e.message}"
            } finally {
                relinkStarting = false
            }
        }
    }

    fun closeRelink() {
        _bothEnds.value?.close(); _bothEnds.value = null
        _session.value?.settings?.load()
    }

    /** После сохранения новой адресации переходим на новый адрес и обновляем сохранённую пушку. */
    private fun moveSession(s: Session, newHost: String) {
        val saved = _devices.value.firstOrNull { it.host == s.host }
        if (saved != null) {
            store?.save(saved.copy(host = newHost))
            _devices.value = store?.all().orEmpty()
        }
        _toast.value = "Пушка теперь доступна по адресу $newHost"
        connect(newHost, s.user, s.pass, save = false, name = s.title.takeIf { it != s.host } ?: newHost)
    }

    private fun startSession(
        title: String, host: String, client: AirOsClient, demo: Boolean,
        user: String = "ubnt", http: OkHttpClient? = null, pass: String = "", factory: Boolean = false,
    ) {
        closeSession()
        val serial = com.airmaxfield.airos.SerializedClient(client)
        val poller = StatusPoller(serial, viewModelScope)
        val tools = com.airmaxfield.ui.tools.ToolsController(
            serial, host, { poller.state.value.status?.hostname }, backups, viewModelScope,
        )
        val hostname = { poller.state.value.status?.hostname }
        lateinit var session: Session
        val settings = com.airmaxfield.ui.settings.SettingsController(
            serial, viewModelScope, host = host,
            backup = if (demo) ({ _ -> }) else { text -> backupFn(host, hostname)(text); tools.refreshBackups() },
            reconnect = if (demo) null else reconnectFn(user, pass),
            onConfirmed = { newHost -> if (newHost != null) moveSession(session, newHost) },
        )
        val firstLogin = if (!factory) null else com.airmaxfield.ui.setup.FirstLoginController(
            serial, host, connector, { text -> backupFn(host, hostname)(text); tools.refreshBackups() }, viewModelScope,
            onDone = { u, p -> afterFirstLogin(session, u, p) },
        )
        session = Session(title, host, serial, poller, demo, user, http, pass, tools, settings, firstLogin)
        _session.value = session
        poller.start()
    }

    fun resumePolling() = _session.value?.poller?.start()

    private fun closeSession() {
        _session.value?.let { s ->
            s.poller.stop()
            s.tools.close()
            s.settings.close()
            s.firstLogin?.close()
            s.http?.let { runCatching { it.connectionPool.evictAll(); it.dispatcher.executorService.shutdown() } }
        }
        _session.value = null
    }

    fun disconnect() = closeSession()

    /** Цель, которую экран устройств должен открыть в окне подключения (после неверного пароля). */
    private val _pendingTarget = MutableStateFlow<com.airmaxfield.ui.devices.ConnectTarget?>(null)
    val pendingTarget: StateFlow<com.airmaxfield.ui.devices.ConnectTarget?> = _pendingTarget.asStateFlow()
    fun pendingTargetTaken() { _pendingTarget.value = null }

    /** Неверный пароль: отключиться и открыть окно подключения с пустым паролем. */
    fun editCredentials() {
        val s = _session.value ?: return
        _pendingTarget.value = com.airmaxfield.ui.devices.ConnectTarget(s.host, s.title, s.user, "")
        closeSession()
    }

    /** Сеть пропала — старые соединения больше не годятся. */
    private fun dropStaleConnections() { _session.value?.http?.connectionPool?.evictAll() }

    fun clearConnectError() { _connect.value = ConnectState.Idle }

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun toastShown() { _toast.value = null }

    /** Снимает ответы пушки и пишет zip в выбранный пользователем файл. */
    fun writeDump(uri: android.net.Uri, s: Session) {
        viewModelScope.launch {
            try {
                val dump = s.client.rawDump()
                val st = s.poller.state.value.status
                val meta = linkedMapOf(
                    "app" to "airMAX Поле ${com.airmaxfield.BuildConfig.VERSION_NAME}",
                    "time" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                    "host" to s.host,
                    "airos" to s.client.version.name,
                    "model" to (st?.model ?: "?"),
                    "firmware" to (st?.firmware ?: "?"),
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        com.airmaxfield.diag.DiagDump.buildZip(dump, meta, it)
                    } ?: error("не удалось открыть файл")
                }
                _toast.value = "Дамп сохранён (${dump.size} ответов)"
            } catch (e: Exception) {
                _toast.value = "Не удалось сохранить дамп: ${e.message}"
            }
        }
    }

    /** Импорт копии из файла телефона в список копий этой пушки. */
    fun importBackup(uri: android.net.Uri, s: Session) {
        viewModelScope.launch {
            val r = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        // Конфигурация — десятки килобайт; больше мегабайта — точно не она.
                        val bytes = it.readBytes(); require(bytes.size < 1_000_000) { "файл слишком большой" }; String(bytes)
                    } ?: error("не удалось открыть файл")
                    require(com.airmaxfield.airos.ConfigBackup.looksLikeConfig(text)) { "это не конфигурация airOS" }
                    val store = backups ?: error("хранилище недоступно")
                    store.save(com.airmaxfield.airos.ConfigBackup.fileName(
                        s.poller.state.value.status?.hostname, s.host, java.util.Date()), text)
                }
            }
            s.tools.refreshBackups()
            _toast.value = if (r.isSuccess) "Копия загружена — её можно восстановить" else "Не удалось загрузить: ${r.exceptionOrNull()?.message}"
        }
    }

    fun deleteDevice(id: String) { store?.delete(id); _devices.value = store?.all().orEmpty() }

    override fun onCleared() {
        runCatching { cm.unregisterNetworkCallback(netCallback) }
        closeSession()
    }
}
