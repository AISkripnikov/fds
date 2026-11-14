package com.airmaxfield.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
 * Все настройки только для чтения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: SystemConfig?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
                    SettingsSection(entries = entries)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSection(entries: List<CfgEntry>) {
    var helpEntry by remember { mutableStateOf<Pair<CfgEntry, FieldHelp>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries) { entry ->
            SettingCard(
                entry = entry,
                onShowHelp = { help ->
                    helpEntry = entry to help
                }
            )
        }
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
    onShowHelp: (FieldHelp) -> Unit,
) {
    val help = HelpContent.forKey(entry.key)
    val isSensitive = SystemConfig.isSensitive(entry.key)
    val displayValue = if (isSensitive) "••••••" else entry.value

    AirCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = help?.title ?: entry.key,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
