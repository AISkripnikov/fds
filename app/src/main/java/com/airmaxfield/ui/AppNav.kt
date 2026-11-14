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
import androidx.compose.runtime.produceState
import com.airmaxfield.ui.status.AlignmentTone
import com.airmaxfield.ui.status.STALE_AFTER_SEC
import com.airmaxfield.ui.status.StatusScreen
import com.airmaxfield.ui.status.staleSeconds
import com.airmaxfield.ui.stations.StationsScreen
import com.airmaxfield.ui.tools.ToolsScreen
import kotlinx.coroutines.delay
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
        val wizard by vm.wizard.collectAsState()
        val s = session
        val w = wizard
        if (w != null) WizardRoot(vm, w) else if (s == null) DevicesRoot(vm) else SessionRoot(vm, s, onSaveDump)
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
private fun WizardRoot(vm: AppViewModel, w: com.airmaxfield.ui.setup.BridgeWizardController) {
    val c = LocalAirColors.current
    val profiles by vm.profiles.collectAsState()
    androidx.activity.compose.BackHandler { vm.closeWizard() }
    Scaffold(containerColor = c.bg, topBar = {
        TopBar("Мастер моста", "Две пушки с нуля",
            nav = { IconButton(onClick = vm::closeWizard) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = c.text) } },
            menu = { ThemeMenu(vm) })
    }) { pad ->
        com.airmaxfield.ui.setup.BridgeWizardScreen(w, profiles, vm::subnetWarning, onClose = vm::closeWizard,
            modifier = Modifier.padding(pad))
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
    var showHelp by rememberSaveable { mutableStateOf(false) }

    if (showHelp) {
        androidx.activity.compose.BackHandler { showHelp = false }
        Scaffold(containerColor = c.bg, topBar = {
            TopBar("Справочник", "Работает без подключения",
                nav = { IconButton(onClick = { showHelp = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = c.text) } },
                menu = { ThemeMenu(vm) })
        }) { pad ->
            com.airmaxfield.ui.help.HelpScreen(modifier = Modifier.padding(pad))
        }
        return
    }

    LaunchedEffect(Unit) { vm.refreshNetworks() }
    val pending by vm.pendingTarget.collectAsState()
    LaunchedEffect(pending) { pending?.let { vm.clearConnectError(); target = it; vm.pendingTargetTaken() } }

    Scaffold(containerColor = c.bg, topBar = {
        TopBar("airMAX Поле", "Выберите пушку", nav = null, menu = {
            ThemeMenu(vm) { close ->
                DropdownMenuItem(text = { Text("Справочник") }, onClick = { close(); showHelp = true })
                DropdownMenuItem(text = { Text("Мастер моста") }, onClick = { close(); vm.openWizard() })
            }
        })
    }) { pad ->
        DevicesScreen(
            networks, devices, discovered, scanning,
            onScan = vm::scan,
            onPick = { vm.clearConnectError(); target = it },
            onDelete = { vm.deleteDevice(it.id) },
            onDemo = vm::demo,
            onBridgeWizard = vm::openWizard,
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

/** Блок «Мост» на вкладке инструментов: смена канала на обеих сторонах. */
@Composable
private fun PairBlock(vm: AppViewModel, s: Session) {
    val c = LocalAirColors.current
    val devices by vm.devices.collectAsState()
    val far = remember(devices, s) { vm.pairOf(s) } ?: return
    val config by s.settings.config.collectAsState()
    var ask by remember { mutableStateOf(false) }
    LaunchedEffect(s) { if (s.settings.config.value == null) s.settings.load() }
    Text("Мост", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    com.airmaxfield.ui.components.AirCard(Modifier.fillMaxWidth()) {
        Text("Вторая сторона: ${far.name} (${far.host})", color = c.text, fontSize = 15.sp)
        Text("Частота, ширина, SSID, ключ и страна должны совпадать на обеих пушках. Меняйте их только здесь — " +
            "сначала дальняя, потом ближняя, обе в тест-режиме.", color = c.muted, fontSize = 13.sp)
        androidx.compose.material3.Button(onClick = { ask = true }, enabled = config != null && s.settings.writable,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Изменить канал на обеих сторонах") }
    }
    val cfg = config
    if (ask && cfg != null) {
        com.airmaxfield.ui.setup.RelinkDialog(
            initial = com.airmaxfield.apply.BridgePlanner.linkOf(cfg),
            onSubmit = { link -> ask = false; vm.startRelink(s, link) },
            onDismiss = { ask = false },
        )
    }
}

@Composable
private fun BothEndsDialog(vm: AppViewModel, o: com.airmaxfield.apply.BothEndsOrchestrator) {
    val far by o.farApply.state.collectAsState()
    val near by o.nearApply.state.collectAsState()
    val problem by o.problem.collectAsState()
    val busy by o.busy.collectAsState()
    val active = listOf(far, near).any {
        it !is com.airmaxfield.apply.ApplyState.Idle && !it.finished
    }
    if (!busy && !active && problem != null && listOf(far, near).none { it is com.airmaxfield.apply.ApplyState.Confirmed }) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::closeRelink,
            containerColor = LocalAirColors.current.card,
            title = { Text("Изменение не выполнено") },
            text = { Text(problem!!) },
            confirmButton = { androidx.compose.material3.Button(onClick = vm::closeRelink) { Text("Понятно") } },
        )
        return
    }
    val farDone = far is com.airmaxfield.apply.ApplyState.Confirmed
    com.airmaxfield.ui.apply.ApplyProgressDialog(
        rows = listOf(com.airmaxfield.ui.apply.ApplyRow("Дальняя пушка", far), com.airmaxfield.ui.apply.ApplyRow("Ближняя пушка", near)),
        canConfirm = if (farDone) (near as? com.airmaxfield.apply.ApplyState.Testing)?.reachable == true else o.canConfirm(),
        confirmLabel = if (farDone) "Сохранить ближнюю" else "Сохранить на обеих",
        onConfirm = o::confirm, onRollback = o::rollback, onClose = vm::closeRelink,
        confirmedText = "Обе пушки сохранены с новыми параметрами линка.",
        note = problem,
    )
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
    // Тикер: «нет данных N с» считается каждую секунду, даже пока запрос висит в таймауте.
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(500); value = System.currentTimeMillis() }
    }
    val stale = staleSeconds(ui.lastOkAt, now)
    val fresh = stale != null && stale <= STALE_AFTER_SEC
    LaunchedEffect(ui.status?.signalDbm, fresh) {
        tone.update(if (fresh) ui.status?.signalDbm else null)
    }

    val bothEnds by vm.bothEnds.collectAsState()
    bothEnds?.let { BothEndsDialog(vm, it) }

    val connected = fresh && ui.status != null
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
                        Text(if (connected) " на связи" else if (ui.status == null) " подключение" else " нет данных ${stale ?: 0} с",
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
        var firstLoginSkipped by rememberSaveable(s) { mutableStateOf(false) }
        val fl = s.firstLogin
        if (fl != null && !firstLoginSkipped) {
            com.airmaxfield.ui.setup.FirstLoginScreen(fl, onSkip = { firstLoginSkipped = true }, modifier = Modifier.padding(pad))
        } else if (tab == 0) {
            StatusScreen(ui, stale, toneOn, { toneOn = it }, onEditPassword = vm::editCredentials, onSaveDump = { onSaveDump(s) },
                modifier = Modifier.padding(pad))
        } else if (tab == 1) {
            val st by s.tools.stations.collectAsState()
            DisposableEffect(s) { s.tools.startStations(); onDispose { s.tools.stopStations() } }
            StationsScreen(st, Modifier.padding(pad))
        } else if (tab == 3) {
            val survey by s.tools.survey.collectAsState()
            val toolsUi by s.tools.tools.collectAsState()
            var exportFile by remember { mutableStateOf<java.io.File?>(null) }
            val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                val f = exportFile
                if (uri != null && f != null) vm.exportBackup(uri, f)
                exportFile = null
            }
            LaunchedEffect(toolsUi.message) {
                toolsUi.message?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show(); s.tools.message(null) }
            }
            ToolsScreen(
                survey, toolsUi, deviceName = ui.status?.hostname ?: s.title,
                onSurvey = s.tools::runSurvey, onSaveConfig = s.tools::saveConfig,
                onExport = { f -> exportFile = f; exporter.launch(f.name) },
                onDelete = s.tools::deleteBackup, onReboot = s.tools::reboot,
                onRefreshBackups = s.tools::refreshBackups, modifier = Modifier.padding(pad),
                extra = { PairBlock(vm, s) },
            )
        } else if (tab == 2) {
            val config by s.settings.config.collectAsState()
            val loading by s.settings.loading.collectAsState()
            val error by s.settings.error.collectAsState()
            val drafts by s.settings.drafts.collectAsState()
            val apply by s.settings.apply.collectAsState()
            var review by remember { mutableStateOf(false) }
            DisposableEffect(s) { if (s.settings.config.value == null) s.settings.load(); onDispose { } }
            com.airmaxfield.ui.settings.SettingsScreen(
                config, loading, error, onRetry = s.settings::retry,
                modifier = Modifier.padding(pad),
                drafts = drafts, writable = s.settings.writable, canEdit = s.settings::canEdit,
                onEdit = s.settings::setDraft, onClearDraft = s.settings::clearDraft,
                onClearAll = s.settings::clearDrafts, onApply = { review = true },
            )
            if (review) {
                val changes = remember(drafts, config) { s.settings.changes() }
                val newHost = s.settings.newHost()
                val extra = newHost?.let { h ->
                    "Новый адрес управления: $h. " + (vm.subnetWarning(h)
                        ?: "После применения приложение будет искать пушку по новому адресу.")
                }
                com.airmaxfield.ui.apply.ChangesDialog(
                    title = "Применить изменения?", changes = changes,
                    warnings = com.airmaxfield.apply.Management.warnings(changes), extra = extra,
                    testSeconds = s.client.testDurationFor(com.airmaxfield.apply.SafeApply.DEFAULT_TEST_SECONDS),
                    onConfirm = { review = false; s.settings.applyDrafts() },
                    onDismiss = { review = false },
                )
            }
            apply?.let { sa ->
                val st by sa.state.collectAsState()
                com.airmaxfield.ui.apply.ApplyProgressDialog(
                    rows = listOf(com.airmaxfield.ui.apply.ApplyRow("", st)),
                    canConfirm = (st as? com.airmaxfield.apply.ApplyState.Testing)?.reachable == true,
                    confirmLabel = "Сохранить навсегда",
                    onConfirm = sa::confirm, onRollback = sa::rollback, onClose = s.settings::dismissApply,
                )
            }
        } else if (tab == 4) {
            com.airmaxfield.ui.help.HelpScreen(modifier = Modifier.padding(pad))
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
