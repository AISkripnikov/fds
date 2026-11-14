package com.airmaxfield.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.ui.ConnectState
import com.airmaxfield.ui.theme.LocalAirColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSheet(
    target: ConnectTarget,
    state: ConnectState,
    subnetWarning: (String) -> String?,
    canSave: Boolean,
    onConnect: (host: String, user: String, pass: String, save: Boolean, name: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAirColors.current
    var host by remember { mutableStateOf(target.host) }
    var name by remember { mutableStateOf(target.name ?: "") }
    var user by remember { mutableStateOf(target.user) }
    var pass by remember { mutableStateOf(target.pass) }
    var save by remember { mutableStateOf(canSave) }
    val busy = state == ConnectState.Busy

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Подключение к пушке", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(host, { host = it }, label = { Text("IP-адрес") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            subnetWarning(host.trim()).takeIf { host.isNotBlank() }?.let { Text("⚠ $it", color = c.fair, fontSize = 14.sp) }
            OutlinedTextField(user, { user = it }, label = { Text("Логин") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            com.airmaxfield.ui.components.PasswordField(pass, { pass = it }, "Пароль")
            if (canSave) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(save, { save = it }); Text("Сохранить", color = c.text, fontSize = 15.sp)
                }
                if (save) OutlinedTextField(name, { name = it }, label = { Text("Название (например, «Север — A»)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (state is ConnectState.Failed) Text(state.message, color = c.bad, fontSize = 15.sp)
            Button(onClick = { onConnect(host, user, pass, save, name) }, enabled = !busy && host.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Подключиться", fontSize = 16.sp)
            }
        }
    }
}
