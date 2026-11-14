package com.airmaxfield.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.apply.ApplyState
import com.airmaxfield.apply.BridgeProfile
import com.airmaxfield.apply.FirstLogin
import com.airmaxfield.apply.LinkParams
import com.airmaxfield.apply.SafeApply
import com.airmaxfield.ui.apply.ApplyProgressDialog
import com.airmaxfield.ui.apply.ApplyRow
import com.airmaxfield.ui.components.AirCard
import com.airmaxfield.ui.theme.LocalAirColors

@Composable
private fun Title(text: String) =
    Text(text, color = LocalAirColors.current.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun Hint(text: String, warn: Boolean = false) {
    val c = LocalAirColors.current
    Text(text, color = if (warn) c.fair else c.muted, fontSize = 14.sp)
}

@Composable
private fun Errors(errors: List<String>) {
    val c = LocalAirColors.current
    errors.forEach { Text("• $it", color = c.bad, fontSize = 14.sp) }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    password: Boolean = false, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(),
) = OutlinedTextField(
    value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
    visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = when {
        password -> KeyboardType.Password
        number -> KeyboardType.Number
        else -> KeyboardType.Text
    }),
)

/** Выбор страны: частые варианты + ручной ввод кода. */
@Composable
fun CountryField(code: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = FirstLogin.COUNTRIES.firstOrNull { it.first == code }?.second
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(code, { v -> onChange(v.filter { it.isDigit() }) }, "Код страны", number = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { open = true }) { Text(name ?: "Выбрать") }
        }
        DropdownMenu(open, { open = false }) {
            FirstLogin.COUNTRIES.forEach { (k, n) ->
                DropdownMenuItem(text = { Text("$n ($k)") }, onClick = { onChange(k); open = false })
            }
        }
    }
}

@Composable
private fun SingleApplyDialog(sa: SafeApply, confirmedText: String, onClose: () -> Unit) {
    val st by sa.state.collectAsState()
    ApplyProgressDialog(
        rows = listOf(ApplyRow("", st)),
        canConfirm = (st as? ApplyState.Testing)?.reachable == true,
        confirmLabel = "Сохранить навсегда",
        onConfirm = sa::confirm, onRollback = sa::rollback, onClose = onClose, confirmedText = confirmedText,
    )
}

/** Первый вход: пушка ещё с заводским паролем ubnt/ubnt. */
@Composable
fun FirstLoginScreen(
    ctrl: FirstLoginController,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    profiles: List<BridgeProfile> = emptyList(),
) {
    val errors by ctrl.errors.collectAsState()
    val apply by ctrl.apply.collectAsState()
    var user by rememberSaveable { mutableStateOf("admin") }
    var pass by rememberSaveable { mutableStateOf("") }
    var pass2 by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("643") }
    var accepted by rememberSaveable { mutableStateOf(false) }
    val c = LocalAirColors.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Title("Первый вход")
        Hint("Пушка работает с заводским паролем ubnt/ubnt. Задайте свой логин и пароль и страну — " +
            "без этого airOS не даст настраивать радио.")
        if (profiles.isNotEmpty()) {
            var pick by remember { mutableStateOf(false) }
            Column {
                OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Взять логин, пароль и страну из шаблона моста…")
                }
                DropdownMenu(pick, { pick = false }) {
                    profiles.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = {
                            user = p.user; pass = p.pass; pass2 = p.pass; country = p.countryCode; pick = false
                        })
                    }
                }
            }
        }
        Field(user, { user = it }, "Новый логин")
        Field(pass, { pass = it }, "Новый пароль (от 8 символов)", password = true)
        Field(pass2, { pass2 = it }, "Пароль ещё раз", password = true)
        CountryField(country) { country = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { accepted = !accepted }) {
            Checkbox(accepted, { accepted = it })
            Text("Принимаю условия использования airOS (Ubiquiti)", color = c.text, fontSize = 14.sp)
        }
        Errors(errors)
        Hint("Запишется в тест-режиме: приложение войдёт уже с новым паролем, и только тогда можно сохранить. " +
            "Пароль запомните — приложение его не подставляет само.")
        Button(onClick = { ctrl.submit(user, pass, pass2, country, accepted) }, modifier = Modifier.fillMaxWidth()) {
            Text("Применить")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Пропустить — настрою позже") }
    }
    apply?.let { SingleApplyDialog(it, "Новый логин и пароль сохранены. Переподключаюсь…", ctrl::dismissApply) }
}

/** Мастер моста: профиль → сторона A → сторона B. */
@Composable
fun BridgeWizardScreen(
    ctrl: BridgeWizardController,
    profiles: List<BridgeProfile>,
    subnetWarning: (String) -> String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by ctrl.ui.collectAsState()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (ui.step) {
            WizardStep.PROFILE -> ProfileStep(ui.profile, profiles, ui.errors, ctrl::setProfile)
            WizardStep.SIDE_A, WizardStep.SIDE_B -> SideStep(ui, subnetWarning, ctrl::applySide, ctrl::back)
            WizardStep.DONE -> {
                val p = ui.profile!!
                Title("Мост «${p.name}» настроен")
                Hint("A: ${p.ipA} (точка доступа), B: ${p.ipB} (станция). Обе пушки сохранены в списке как пара — " +
                    "подключитесь к любой, чтобы смотреть сигнал или менять канал на обеих сторонах.")
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            }
        }
    }
    ui.apply?.let { SingleApplyDialog(it, "Сторона сохранена.", ctrl::dismissApply) }
}

@Composable
private fun ProfileStep(
    current: BridgeProfile?, profiles: List<BridgeProfile>, errors: List<String>, onNext: (BridgeProfile) -> Unit,
) {
    val init = current ?: profiles.lastOrNull()
    var name by rememberSaveable { mutableStateOf(init?.name ?: "") }
    var ssid by rememberSaveable { mutableStateOf(init?.ssid ?: "") }
    var psk by rememberSaveable { mutableStateOf(init?.psk ?: "") }
    var country by rememberSaveable { mutableStateOf(init?.countryCode ?: "643") }
    var freq by rememberSaveable { mutableStateOf(init?.freqMhz?.toString() ?: "") }
    var width by rememberSaveable { mutableStateOf(init?.chanbwMhz?.toString() ?: "40") }
    var power by rememberSaveable { mutableStateOf(init?.txPowerDbm?.toString() ?: "") }
    var ipA by rememberSaveable { mutableStateOf(init?.ipA ?: "192.168.1.21") }
    var ipB by rememberSaveable { mutableStateOf(init?.ipB ?: "192.168.1.22") }
    var mask by rememberSaveable { mutableStateOf(init?.netmask ?: "255.255.255.0") }
    var gw by rememberSaveable { mutableStateOf(init?.gateway ?: "") }
    var vlan by rememberSaveable { mutableStateOf(init?.mgmtVlan?.toString() ?: "") }
    var user by rememberSaveable { mutableStateOf(init?.user ?: "admin") }
    var pass by rememberSaveable { mutableStateOf(init?.pass ?: "") }
    var pick by remember { mutableStateOf(false) }

    Title("Мастер моста: параметры")
    Hint("Задаются один раз и сохраняются как шаблон. Мастер сам рассчитает настройки обеих сторон, чтобы они совпадали.")
    if (profiles.isNotEmpty()) {
        Column {
            OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) { Text("Взять из шаблона…") }
            DropdownMenu(pick, { pick = false }) {
                profiles.forEach { p ->
                    DropdownMenuItem(text = { Text(p.name) }, onClick = {
                        name = p.name; ssid = p.ssid; psk = p.psk; country = p.countryCode
                        freq = p.freqMhz?.toString() ?: ""; width = p.chanbwMhz.toString(); power = p.txPowerDbm?.toString() ?: ""
                        ipA = p.ipA; ipB = p.ipB; mask = p.netmask; gw = p.gateway; vlan = p.mgmtVlan?.toString() ?: ""
                        user = p.user; pass = p.pass; pick = false
                    })
                }
            }
        }
    }
    Field(name, { name = it }, "Имя моста (например, Ферма-Север)")
    Field(ssid, { ssid = it }, "SSID")
    Field(psk, { psk = it }, "Ключ WPA2 (8–63 символа)", password = true)
    CountryField(country) { country = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(freq, { v -> freq = v.filter { it.isDigit() } }, "Частота, МГц (пусто — авто)", number = true, modifier = Modifier.weight(1f))
        Field(width, { v -> width = v.filter { it.isDigit() } }, "Ширина, МГц", number = true, modifier = Modifier.weight(1f))
    }
    Field(power, { v -> power = v.filter { it.isDigit() } }, "Мощность, dBm (пусто — не менять)", number = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(ipA, { ipA = it.trim() }, "IP стороны A", modifier = Modifier.weight(1f))
        Field(ipB, { ipB = it.trim() }, "IP стороны B", modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(mask, { mask = it.trim() }, "Маска", modifier = Modifier.weight(1f))
        Field(gw, { gw = it.trim() }, "Шлюз (необязательно)", modifier = Modifier.weight(1f))
    }
    Field(vlan, { v -> vlan = v.filter { it.isDigit() } }, "VLAN управления (необязательно)", number = true)
    Field(user, { user = it.trim() }, "Логин для обеих пушек")
    Field(pass, { pass = it }, "Пароль для обеих пушек (от 8 символов)", password = true)
    Errors(errors)
    Button(onClick = {
        onNext(BridgeProfile(
            name = name.trim(), ssid = ssid, psk = psk, countryCode = country,
            freqMhz = freq.toIntOrNull(), chanbwMhz = width.toIntOrNull() ?: 0, txPowerDbm = power.toIntOrNull(),
            ipA = ipA, ipB = ipB, netmask = mask, gateway = gw, mgmtVlan = vlan.toIntOrNull(), user = user, pass = pass,
        ))
    }, modifier = Modifier.fillMaxWidth()) { Text("Дальше: пушка A") }
}

@Composable
private fun SideStep(
    ui: WizardUi, subnetWarning: (String) -> String?, onApply: (String, String, String) -> Unit, onBack: () -> Unit,
) {
    val p = ui.profile!!
    val a = ui.step == WizardStep.SIDE_A
    var host by remember(ui.step) { mutableStateOf(FirstLogin.FACTORY_IP) }
    var user by remember(ui.step) { mutableStateOf(FirstLogin.FACTORY_USER) }
    var pass by remember(ui.step) { mutableStateOf("") }
    Title(if (a) "Шаг 2 из 3: пушка A — точка доступа" else "Шаг 3 из 3: пушка B — станция")
    AirCard(Modifier.fillMaxWidth()) {
        Hint(if (a) "Подключите телефон к пушке, которая станет точкой доступа (AP PtP). Она получит адрес ${p.ipA}."
            else "Теперь подключите телефон к второй пушке. Она станет станцией и получит адрес ${p.ipB}.")
        Hint("Сброшенная пушка: адрес ${FirstLogin.FACTORY_IP}, логин и пароль ubnt/ubnt.")
    }
    Field(host, { host = it.trim() }, "Текущий адрес пушки")
    subnetWarning(host).takeIf { host.isNotBlank() }?.let { Hint("⚠ $it", warn = true) }
    Field(user, { user = it }, "Текущий логин")
    Field(pass, { pass = it }, "Текущий пароль", password = true)
    Hint("После применения пушка уйдёт на ${p.ipFor(if (a) com.airmaxfield.apply.BridgeRole.AP else com.airmaxfield.apply.BridgeRole.STATION)}" +
        " с логином ${p.user}. " + (subnetWarning(if (a) p.ipA else p.ipB)?.let { "⚠ $it" }
        ?: "Телефон в той же подсети — приложение найдёт её по новому адресу."), warn = true)
    Errors(ui.errors)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack, enabled = !ui.connecting, modifier = Modifier.weight(1f)) { Text("Назад") }
        Button(onClick = { onApply(host, user, pass) }, enabled = !ui.connecting, modifier = Modifier.weight(1f)) {
            if (ui.connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Применить")
        }
    }
}

/** Новые параметры линка для изменения на обеих сторонах моста. */
@Composable
fun RelinkDialog(initial: LinkParams, onSubmit: (LinkParams) -> Unit, onDismiss: () -> Unit) {
    var ssid by remember { mutableStateOf(initial.ssid) }
    var psk by remember { mutableStateOf(initial.psk) }
    var country by remember { mutableStateOf(initial.countryCode) }
    var freq by remember { mutableStateOf(initial.freqMhz?.toString() ?: "") }
    var width by remember { mutableStateOf(initial.chanbwMhz.toString()) }
    var err by remember { mutableStateOf<String?>(null) }
    val c = LocalAirColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text("Канал моста на обеих сторонах", color = c.text) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(freq, { v -> freq = v.filter { it.isDigit() } }, "Частота, МГц", number = true)
                Field(width, { v -> width = v.filter { it.isDigit() } }, "Ширина, МГц", number = true)
                Field(ssid, { ssid = it }, "SSID")
                Field(psk, { psk = it }, "Ключ WPA2", password = true)
                CountryField(country) { country = it }
                err?.let { Text(it, color = c.bad, fontSize = 14.sp) }
                Hint("Сначала дальняя пушка (тест 300 с), потом ближняя (180 с). Сохранить можно, только когда обе снова на связи. " +
                    "Если линк не поднимется — ничего не делайте, обе откатятся сами.")
            }
        },
        confirmButton = {
            Button(onClick = {
                val f = freq.toIntOrNull(); val w = width.toIntOrNull()
                err = when {
                    f == null || f !in 2400..6000 -> "Частота: 2400…6000 МГц"
                    w == null || w !in 5..80 -> "Ширина: 5…80 МГц"
                    ssid.isBlank() -> "Укажите SSID"
                    psk.length !in 8..63 -> "Ключ WPA2: 8–63 символа"
                    country.isBlank() -> "Укажите страну"
                    else -> null
                }
                if (err == null) onSubmit(LinkParams(ssid, psk, country, f, w!!))
            }) { Text("Дальше") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
