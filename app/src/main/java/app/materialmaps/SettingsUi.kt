@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSheet(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onClearHistory: (() -> Unit)?,
    shortcuts: Map<Shortcut, Place>,
    onSetShortcut: (Shortcut) -> Unit,
    onClearShortcut: (Shortcut) -> Unit,
    offlineAreas: List<OfflineArea>,
    onDownloadArea: () -> Unit,
    onDeleteArea: (OfflineArea) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editingQuickButtons by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (editingQuickButtons) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { editingQuickButtons = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Quick buttons", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Buttons under the search bar, in this order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                QuickButtonsEditor(settings.quickButtons) { onChange(settings.copy(quickButtons = it)) }
            }
            BackHandler { editingQuickButtons = false }
            return@ModalBottomSheet
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Heading("Map")
            Choice("Appearance", Appearance.entries, settings.appearance, { it.label }) { onChange(settings.copy(appearance = it)) }
            Choice("Map colors", MapColors.entries, settings.mapColors, { it.label }) { onChange(settings.copy(mapColors = it)) }
            Choice("Style", MapStyle.entries, settings.mapStyle, { it.label }) { onChange(settings.copy(mapStyle = it)) }
            Toggle("3D buildings", "When the map is tilted", settings.buildings3d) { onChange(settings.copy(buildings3d = it)) }
            Toggle("Show saved places", "Your lists as dots on the map", settings.showSaved) { onChange(settings.copy(showSaved = it)) }
            Choice("Distances", Units.entries, settings.units, { it.label }) { onChange(settings.copy(units = it)) }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Heading("Directions")
            Toggle("Voice guidance", "Speak turns while navigating", settings.voiceGuidance) { onChange(settings.copy(voiceGuidance = it)) }
            Toggle("Avoid highways", "Driving", settings.avoidHighways) { onChange(settings.copy(avoidHighways = it)) }
            Toggle("Avoid tolls", "Driving", settings.avoidTolls) { onChange(settings.copy(avoidTolls = it)) }
            Toggle("Avoid ferries", null, settings.avoidFerries) { onChange(settings.copy(avoidFerries = it)) }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Heading("Quick buttons")
            ListItem(
                headlineContent = { Text("Buttons under the search bar") },
                supportingContent = {
                    Text(
                        settings.quickButtons.mapNotNull { QuickButton.byId(it)?.label }.joinToString(", ").ifEmpty { "None" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { editingQuickButtons = true },
            )

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Heading("Places")
            Shortcut.entries.forEach { shortcut ->
                val place = shortcuts[shortcut]
                ListItem(
                    headlineContent = { Text(shortcut.label) },
                    supportingContent = { Text(place?.let { listOf(it.name, it.subtitle).filter(String::isNotBlank).joinToString(", ") } ?: "Not set") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { onSetShortcut(shortcut) }) { Text(if (place == null) "Set" else "Change") }
                            if (place != null) TextButton(onClick = { onClearShortcut(shortcut) }) { Text("Remove") }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = onExport) { Text("Export saved places") }
                TextButton(onClick = onImport) { Text("Import") }
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Heading("Offline maps")
            Text(
                "Keeps the map of an area on this phone. Search and directions still need a connection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            offlineAreas.forEach { area ->
                ListItem(
                    headlineContent = { Text(area.name) },
                    supportingContent = {
                        Column {
                            Text(
                                area.error ?: when {
                                    area.complete -> Formatter.formatShortFileSize(LocalContext.current, area.bytes)
                                    else -> "Downloading… ${(area.progress * 100).toInt()}%"
                                }
                            )
                            if (!area.complete && area.error == null) {
                                LinearProgressIndicator(progress = { area.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                    },
                    trailingContent = { TextButton(onClick = { onDeleteArea(area) }) { Text("Delete") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            TextButton(onClick = onDownloadArea, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Download the area on screen")
            }

            if (onClearHistory != null) {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Heading("Privacy")
                TextButton(onClick = onClearHistory, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Clear search history")
                }
            }
        }
    }
}

/** Which buttons show under the search bar, and in what order: shown ones first. */
@Composable
private fun QuickButtonsEditor(ids: List<String>, onChange: (List<String>) -> Unit) {
    val shown = ids.mapNotNull { QuickButton.byId(it) }
    val hidden = QuickButton.all.filterNot { it.id in ids }
    (shown + hidden).forEach { button ->
        val index = ids.indexOf(button.id)
        val on = index >= 0
        ListItem(
            headlineContent = { Text(button.label) },
            leadingContent = {
                Checkbox(
                    checked = on,
                    onCheckedChange = { onChange(if (on) ids - button.id else ids + button.id) },
                )
            },
            trailingContent = if (on) {
                {
                    Row {
                        IconButton(onClick = { onChange(ids.moved(index, index - 1)) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { onChange(ids.moved(index, index + 1)) }, enabled = index < ids.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> Choice(title: String, entries: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, entries.size),
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
