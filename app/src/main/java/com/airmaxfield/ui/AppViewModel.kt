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
)

sealed interface ConnectState {
    data object Idle : ConnectState
    data object Busy : ConnectState
    data class Failed(val message: String, val badCredentials: Boolean) : ConnectState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    private val store = runCatching { DeviceStore(app) }.getOrNull()
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
        override fun onLost(network: Network) = refreshNetworks()
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
            val local = nets.list().filter { it.first.kind == LocalNet.Kind.WIFI || it.first.kind == LocalNet.Kind.ETHERNET }
            val result = mutableMapOf<String, Discovered>()
            for ((_, network) in local) {
                runCatching { UbntDiscovery { network.bindSocket(it) }.scan() }.getOrNull()?.forEach { result[it.ip] = it }
            }
            _discovered.value = result.values.sortedBy { it.ip }
            _scanning.value = false
        }
    }

    fun connect(host: String, user: String, pass: String, save: Boolean, name: String?) {
        if (_connect.value == ConnectState.Busy) return
        _connect.value = ConnectState.Busy
        viewModelScope.launch {
            try {
                val net = nets.networkFor(host)?.second
                val http = deviceHttpClient(net?.socketFactory)
                val client = connect(host.trim(), user.trim(), pass, http)
                if (save) {
                    val existing = _devices.value.firstOrNull { it.host == host.trim() }
                    store?.save(SavedDevice(existing?.id ?: UUID.randomUUID().toString(),
                        name?.takeIf { it.isNotBlank() } ?: host.trim(), host.trim(), user.trim(), pass))
                    _devices.value = store?.all().orEmpty()
                }
                startSession(name?.takeIf { it.isNotBlank() } ?: host.trim(), host.trim(), client, demo = false)
                _connect.value = ConnectState.Idle
            } catch (e: AirOsError) {
                _connect.value = ConnectState.Failed(e.message ?: "Ошибка", e is AirOsError.BadCredentials)
            } catch (e: Exception) {
                _connect.value = ConnectState.Failed("Ошибка: ${e.message ?: e.javaClass.simpleName}", false)
            }
        }
    }

    fun demo() = startSession("Демо-режим", "демо", DemoClient(), demo = true)

    private fun startSession(title: String, host: String, client: AirOsClient, demo: Boolean) {
        _session.value?.poller?.stop()
        val poller = StatusPoller(client, viewModelScope)
        _session.value = Session(title, host, client, poller, demo)
        poller.start()
    }

    fun resumePolling() = _session.value?.poller?.start()

    fun disconnect() { _session.value?.poller?.stop(); _session.value = null }

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

    fun deleteDevice(id: String) { store?.delete(id); _devices.value = store?.all().orEmpty() }

    override fun onCleared() {
        runCatching { cm.unregisterNetworkCallback(netCallback) }
        _session.value?.poller?.stop()
    }
}
