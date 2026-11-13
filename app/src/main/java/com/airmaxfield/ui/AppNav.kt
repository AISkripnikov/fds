package com.airmaxfield.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airmaxfield.ui.components.StatusDot
import com.airmaxfield.ui.devices.ConnectSheet
import com.airmaxfield.ui.devices.ConnectTarget
import com.airmaxfield.ui.devices.DevicesScreen
import com.airmaxfield.ui.status.AlignmentTone
import com.airmaxfield.ui.status.StatusScreen
import com.airmaxfield.ui.theme.AirmaxTheme
import com.airmaxfield.ui.theme.LocalAirColors

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Статус", Icons.Filled.SignalCellularAlt),
    Tab("Станции", Icons.Filled.Devices),
    Tab("Настройки", Icons.Filled.Settings),
    Tab("Инструм.", Icons.Filled.Build),
    Tab("Справка", Icons.AutoMirrored.Filled.MenuBook),
)

@Composable
fun AirmaxApp(vm: AppViewModel = viewModel()) {
    val light by vm.lightTheme.collectAsState()
    val ctx = LocalContext.current
    var dumpFor by remember { mutableStateOf<Session?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val s = dumpFor
        if (uri != null && s != null) vm.writeDump(uri, s)
        dumpFor = null
    }
    val onSaveDump: (Session) -> Unit = { s ->
        dumpFor = s
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        saver.launch("airmax-dump-${s.host.replace('.', '-')}-$stamp.zip")
    }
    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show(); vm.toastShown() }
    }
    AirmaxTheme(dark = !light) {
        val session by vm.session.collectAsState()
        val s = session
        if (s == null) DevicesRoot(vm) else SessionRoot(vm, s, onSaveDump)
    }
}

@Composable
private fun TopBar(title: String, subtitle: String?, nav: (@Composable () -> Unit)?, menu: @Composable () -> Unit, dot: Boolean? = null) {
    val c = LocalAirColors.current
    Row(
        Modifier.fillMaxWidth().background(c.card).statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nav != null) nav() else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        menu()
    }
}

@Composable
private fun ThemeMenu(vm: AppViewModel, extra: @Composable (close: () -> Unit) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    val light by vm.lightTheme.collectAsState()
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Меню", tint = LocalAirColors.current.text) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(if (light) "Тёмная тема" else "Светлая тема") }, onClick = { vm.toggleTheme(); open = false })
            extra { open = false }
        }
    }
}

@Composable
private fun DevicesRoot(vm: AppViewModel) {
    val c = LocalAirColors.current
    val networks by vm.networks.collectAsState()
    val devices by vm.devices.collectAsState()
    val discovered by vm.discovered.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val connectState by vm.connectState.collectAsState()
    var target by remember { mutableStateOf<ConnectTarget?>(null) }

    LaunchedEffect(Unit) { vm.refreshNetworks() }

    Scaffold(containerColor = c.bg, topBar = {
        TopBar("airMAX Поле", "Выберите пушку", nav = null, menu = { ThemeMenu(vm) })
    }) { pad ->
        DevicesScreen(
            networks, devices, discovered, scanning,
            onScan = vm::scan,
            onPick = { vm.clearConnectError(); target = it },
            onDelete = { vm.deleteDevice(it.id) },
            onDemo = vm::demo,
            modifier = Modifier.padding(pad),
        )
    }
    target?.let { t ->
        ConnectSheet(
            target = t, state = connectState, subnetWarning = vm::subnetWarning, canSave = vm.storeAvailable,
            onConnect = { h, u, p, save, name -> vm.connect(h, u, p, save, name) },
            onDismiss = { target = null; vm.clearConnectError() },
        )
    }
    // Закрыть окно после успешного подключения.
    val session by vm.session.collectAsState()
    LaunchedEffect(session) { if (session != null) target = null }
}

@Composable
private fun SessionRoot(vm: AppViewModel, s: Session, onSaveDump: (Session) -> Unit) {
    val c = LocalAirColors.current
    val ui by s.poller.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var toneOn by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val tone = remember { AlignmentTone(ctx) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    LaunchedEffect(toneOn) { tone.setEnabled(toneOn) }
    LaunchedEffect(ui.status?.signalDbm, ui.secondsSinceUpdate) {
        tone.update(if (ui.secondsSinceUpdate > 2) null else ui.status?.signalDbm)
    }

    val connected = ui.error == null && ui.status != null
    val subtitle = listOfNotNull(ui.status?.model, s.host.takeIf { !s.demo }).joinToString(" · ").ifEmpty { s.host }
    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopBar(
                title = ui.status?.hostname?.let { if (s.demo) s.title else "$it" } ?: s.title,
                subtitle = subtitle,
                nav = { IconButton(onClick = vm::disconnect) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Отключиться", tint = c.text) } },
                menu = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(connected)
                        Text(if (connected) " на связи" else if (ui.status == null) " подключение" else " нет данных ${ui.secondsSinceUpdate} с",
                            color = c.muted, fontSize = 13.sp)
                        ThemeMenu(vm) { close ->
                            DropdownMenuItem(text = { Text("Сохранить диагностический дамп") }, onClick = { close(); onSaveDump(s) })
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = c.card) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, null) }, label = { Text(t.title, fontSize = 12.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accent, selectedTextColor = c.accent, indicatorColor = c.line,
                            unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        if (tab == 0) {
            StatusScreen(ui, toneOn, { toneOn = it }, onRetry = vm::resumePolling, onSaveDump = { onSaveDump(s) },
                modifier = Modifier.padding(pad))
        } else {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(TABS[tab].icon, null, tint = c.muted)
                    Text("«${TABS[tab].title}» — будет в следующей версии", color = c.muted, fontSize = 15.sp)
                }
            }
        }
    }
}
