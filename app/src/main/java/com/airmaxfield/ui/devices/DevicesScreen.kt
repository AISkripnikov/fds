package com.airmaxfield.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmaxfield.data.SavedDevice
import com.airmaxfield.discovery.Discovered
import com.airmaxfield.net.LocalNet
import com.airmaxfield.ui.components.AirCard
import com.airmaxfield.ui.theme.LocalAirColors

/** Что открыть в окне подключения. */
data class ConnectTarget(val host: String, val name: String? = null, val user: String = "ubnt", val pass: String = "")

@Composable
fun DevicesScreen(
    networks: List<LocalNet>,
    saved: List<SavedDevice>,
    discovered: List<Discovered>,
    scanning: Boolean,
    onScan: () -> Unit,
    onPick: (ConnectTarget) -> Unit,
    onDelete: (SavedDevice) -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
    onBridgeWizard: () -> Unit = {},
) {
    val c = LocalAirColors.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Section("Сеть телефона")
        AirCard(Modifier.fillMaxWidth()) {
            val shown = networks.filter { it.kind != LocalNet.Kind.OTHER }
            if (shown.isEmpty()) Text("Нет активных сетей", color = c.muted, fontSize = 15.sp)
            shown.forEach { n ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (n.kind) {
                            LocalNet.Kind.WIFI -> Icons.Filled.Wifi
                            LocalNet.Kind.ETHERNET -> Icons.Filled.Cable
                            else -> Icons.Filled.SignalCellularAlt
                        }, null, tint = if (n.kind == LocalNet.Kind.CELLULAR) c.muted else c.accent,
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            when (n.kind) {
                                LocalNet.Kind.WIFI -> "Wi‑Fi"; LocalNet.Kind.ETHERNET -> "Кабель (Ethernet)"
                                LocalNet.Kind.CELLULAR -> "Мобильная сеть (не используется)"; else -> "Другая"
                            }, color = c.text, fontSize = 15.sp,
                        )
                        Text(
                            (n.ipv4?.let { "$it/${n.prefix}" } ?: "нет адреса") + if (n.hasInternet) " · интернет есть" else " · без интернета",
                            color = c.muted, fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        Section("Быстрое подключение")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton("192.168.1.20", "заводской", Modifier.weight(1f)) { onPick(ConnectTarget("192.168.1.20")) }
            QuickButton("192.168.172.1", "служебный Wi‑Fi AC", Modifier.weight(1f)) { onPick(ConnectTarget("192.168.172.1")) }
        }
        OutlinedButton(onClick = { onPick(ConnectTarget("")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Другой адрес…", fontSize = 15.sp)
        }
        OutlinedButton(onClick = onBridgeWizard, modifier = Modifier.fillMaxWidth()) {
            Text("Мастер моста: две пушки с нуля", fontSize = 15.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Section("Найденные в сети", Modifier.weight(1f))
            if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onScan) { Icon(Icons.Filled.Search, "Найти", tint = c.accent) }
        }
        if (discovered.isEmpty()) {
            Text(if (scanning) "Ищу пушки…" else "Нажмите 🔍, чтобы найти пушки Ubiquiti в сети телефона.",
                color = c.muted, fontSize = 14.sp)
        }
        discovered.forEach { d ->
            DeviceRow(
                title = d.model ?: "Ubiquiti",
                subtitle = listOfNotNull(d.hostname, d.ip, d.firmware).joinToString(" · "),
                onClick = { onPick(ConnectTarget(d.ip, d.hostname)) },
            )
        }

        Section("Сохранённые")
        if (saved.isEmpty()) Text("Пока пусто. Отметьте «Сохранить» при подключении.", color = c.muted, fontSize = 14.sp)
        saved.forEach { d ->
            val partner = d.pairId?.let { pid -> saved.firstOrNull { it.pairId == pid && it.id != d.id } }
            DeviceRow(d.name, "${d.host} · ${d.user}" + (partner?.let { " · мост ↔ ${it.name}" } ?: ""), onClick = { onPick(ConnectTarget(d.host, d.name, d.user, d.pass)) },
                trailing = { IconButton(onClick = { onDelete(d) }) { Icon(Icons.Filled.Delete, "Удалить", tint = c.muted) } })
        }

        Button(
            onClick = onDemo, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.line, contentColor = c.text),
        ) {
            Icon(Icons.Filled.PlayArrow, null); Text("  Демо-режим (без пушки)", fontSize = 15.sp)
        }
    }
}

@Composable
private fun Section(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier.padding(top = 4.dp), color = LocalAirColors.current.text,
        fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun QuickButton(ip: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAirColors.current
    AirCard(modifier.clickable(onClick = onClick)) {
        Text(ip, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = c.muted, fontSize = 13.sp)
    }
}

@Composable
private fun DeviceRow(title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = LocalAirColors.current
    AirCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Router, null, tint = c.accent)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = c.muted, fontSize = 13.sp)
            }
            trailing()
        }
    }
}
