package com.airmaxfield.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.airmaxfield.airos.DangerousKeys
import com.airmaxfield.airos.FieldType
import com.airmaxfield.ui.theme.LocalAirColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.airmaxfield.airos.CfgEntry
import com.airmaxfield.airos.SystemConfig
import com.airmaxfield.help.FieldHelp
import com.airmaxfield.help.HelpContent
import com.airmaxfield.ui.components.AirCard

/**
 * Экран настроек устройства с группировкой по секциям.
 * Нажатие на поле открывает правку; правки копятся в черновике и применяются вместе
 * через тест-режим (кнопка «Применить»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: SystemConfig?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    drafts: Map<String, String> = emptyMap(),
    writable: Boolean = false,
    canEdit: (String) -> Boolean = { false },
    onEdit: (key: String, value: String) -> Unit = { _, _ -> },
    onClearDraft: (String) -> Unit = {},
    onClearAll: () -> Unit = {},
    onApply: () -> Unit = {},
) {
    var selectedSection by remember { mutableStateOf(0) }
    val sections = listOf("Беспроводная", "Сеть", "Дополнительно", "Сервисы", "Система")

    Column(modifier = modifier.fillMaxSize()) {
        // Фильтр по секциям
        ScrollableTabRow(
            selectedTabIndex = selectedSection,
            modifier = Modifier.fillMaxWidth()
        ) {
            sections.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSection == index,
                    onClick = { selectedSection = index },
                    text = { Text(title) }
                )
            }
        }

        // Содержимое
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetry) {
                            Text("Повторить")
                        }
                    }
                }
            }

            config == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Конфигурация не загружена",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val entries = when (selectedSection) {
                    0 -> config.wireless
                    1 -> config.network
                    2 -> config.advanced
                    3 -> config.services
                    4 -> config.system
                    else -> emptyList()
                }

                if (!writable) {
                    Text(
                        "У этой пушки нет тест-режима — без автоотката запись опасна, поэтому настройки только для просмотра.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет настроек в этой секции",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(Modifier.weight(1f)) {
                        SettingsSection(entries = entries, drafts = drafts, canEdit = canEdit, onEdit = onEdit, onClearDraft = onClearDraft)
                    }
                }
                if (drafts.isNotEmpty()) DraftBar(drafts.size, onClearAll, onApply)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSection(
    entries: List<CfgEntry>,
    drafts: Map<String, String>,
    canEdit: (String) -> Boolean,
    onEdit: (String, String) -> Unit,
    onClearDraft: (String) -> Unit,
) {
    var helpEntry by remember { mutableStateOf<Pair<CfgEntry, FieldHelp>?>(null) }
    var editing by remember { mutableStateOf<CfgEntry?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries) { entry ->
            SettingCard(
                entry = entry,
                draft = drafts[entry.key],
                editable = canEdit(entry.key),
                onClick = { editing = entry },
                onShowHelp = { help ->
                    helpEntry = entry to help
                }
            )
        }
    }

    editing?.let { e ->
        EditDialog(
            entry = e,
            draft = drafts[e.key],
            onSave = { v -> onEdit(e.key, v); editing = null },
            onRevert = { onClearDraft(e.key); editing = null },
            onDismiss = { editing = null },
        )
    }

    // Bottom sheet для справки
    helpEntry?.let { (entry, help) ->
        ModalBottomSheet(
            onDismissRequest = { helpEntry = null }
        ) {
            HelpSheet(entry = entry, help = help)
        }
    }
}

@Composable
private fun SettingCard(
    entry: CfgEntry,
    draft: String?,
    editable: Boolean,
    onClick: () -> Unit,
    onShowHelp: (FieldHelp) -> Unit,
) {
    val help = HelpContent.forKey(entry.key)
    val isSensitive = SystemConfig.isSensitive(entry.key)
    val value = draft ?: entry.value
    val displayValue = if (isSensitive) "••••••" else com.airmaxfield.airos.FieldSpec.label(entry.key, value)

    AirCard(
        modifier = Modifier.fillMaxWidth().then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = help?.title ?: entry.key,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (draft != null) {
                        Text(
                            "  черновик",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalAirColors.current.fair,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = if (!isSensitive) FontFamily.Monospace else FontFamily.Default
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (help != null) {
                IconButton(onClick = { onShowHelp(help) }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Справка",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpSheet(
    entry: CfgEntry,
    help: FieldHelp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = help.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = entry.key,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = help.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        help.recommendations?.let { rec ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Рекомендации",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = rec,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        help.warnings?.let { warn ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Внимание",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = warn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DraftBar(count: Int, onClearAll: () -> Unit, onApply: () -> Unit) {
    val c = LocalAirColors.current
    Surface(color = c.card, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Изменений: $count", color = c.text, modifier = Modifier.weight(1f))
            TextButton(onClick = onClearAll) { Text("Сбросить") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApply) { Text("Применить") }
        }
    }
}

@Composable
private fun EditDialog(
    entry: CfgEntry,
    draft: String?,
    onSave: (String) -> Unit,
    onRevert: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAirColors.current
    val help = HelpContent.forKey(entry.key)
    val sensitive = SystemConfig.isSensitive(entry.key)
    val danger = DangerousKeys.kind(entry.key)
    var value by remember(entry.key) { mutableStateOf(draft ?: entry.value) }
    var shown by remember { mutableStateOf(!sensitive) }
    val type = remember(entry.key) { com.airmaxfield.airos.FieldSpec.typeFor(entry.key, entry.value) }
    val error = com.airmaxfield.airos.FieldSpec.validate(type, value)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(help?.title ?: entry.key, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.key, fontFamily = FontFamily.Monospace, color = c.muted, style = MaterialTheme.typography.bodySmall)
                when (type) {
                    is FieldType.Toggle -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (value == type.on) "Включено" else "Выключено", color = c.text, modifier = Modifier.weight(1f))
                        Switch(checked = value == type.on, onCheckedChange = { value = if (it) type.on else type.off })
                    }
                    is FieldType.Choice -> Column {
                        type.options.forEach { (v, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { value = v }) {
                                RadioButton(selected = value == v, onClick = { value = v })
                                Text(label, color = c.text)
                            }
                        }
                    }
                    else -> OutlinedTextField(
                        value = value,
                        onValueChange = { v -> value = v.replace("\n", "") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { e -> { Text(e) } },
                        suffix = (type as? FieldType.Number)?.unit?.takeIf { it.isNotEmpty() }?.let { u -> { Text(u) } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = when (type) {
                            is FieldType.Number -> androidx.compose.ui.text.input.KeyboardType.Number
                            FieldType.Ip, FieldType.Netmask -> androidx.compose.ui.text.input.KeyboardType.Decimal
                            else -> if (sensitive) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text
                        }),
                        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = if (sensitive) {
                            {
                                IconButton(onClick = { shown = !shown }) {
                                    Icon(if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Показать")
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!sensitive) Text("Сейчас на пушке: ${com.airmaxfield.airos.FieldSpec.label(entry.key, entry.value)}",
                    color = c.muted, style = MaterialTheme.typography.bodySmall)
                danger?.let {
                    Text("⚠ Опасное поле (${it.title}): после смены связь может пропасть. Запись пойдёт в тест-режиме с автооткатом.",
                        color = c.fair, style = MaterialTheme.typography.bodySmall)
                }
                help?.let { Text(it.description, color = c.text, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { onSave(value) }, enabled = error == null) { Text("В черновик") } },
        dismissButton = {
            Row {
                if (draft != null) TextButton(onClick = onRevert) { Text("Вернуть как было") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
