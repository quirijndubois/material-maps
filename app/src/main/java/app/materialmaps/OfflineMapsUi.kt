@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Offline maps: what's on the phone, and the world to download from, by continent, country
 * and province, or searched by name. Each region comes with its routing data, for directions
 * without a connection.
 */
@Composable
fun OfflineMapsPage(maps: OfflineMaps, styleUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var regions by remember { mutableStateOf<List<Region>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { RegionCatalog.all(context) }
            .onSuccess { regions = it }
            .onFailure { failed = true }
    }
    val saved by OfflineRegions.saved.collectAsState()
    val transfers by OfflineRegions.transfers.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    // The regions opened, from the continent down.
    var path by rememberSaveable { mutableStateOf(listOf<String>()) }
    var confirming by remember { mutableStateOf<Region?>(null) }
    var deleting by remember { mutableStateOf<SavedRegion?>(null) }

    val all = regions.orEmpty()
    val byId = remember(regions) { all.associateBy { it.id } }
    val children = remember(regions) { all.groupBy { it.parent } }
    val savedIds = saved.map { it.id }.toSet()

    fun back() {
        when {
            query.isNotEmpty() -> query = ""
            path.isNotEmpty() -> path = path.dropLast(1)
            else -> onBack()
        }
    }

    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Offline maps") },
                    navigationIcon = {
                        IconButton(onClick = { back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search countries and regions") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") } }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (query.isBlank()) {
                    val looseAreas = maps.areas.filterNot { it.region }
                    if (saved.isNotEmpty() || looseAreas.isNotEmpty()) {
                        item { SectionTitle("On this phone") }
                        items(saved, key = { "saved-" + it.id }) { region ->
                            SavedRegionRow(region, maps.areas.firstOrNull { it.id == region.mapRegion }, transfers) { deleting = region }
                        }
                        items(looseAreas, key = { "area-" + it.id }) { area ->
                            ListItem(
                                headlineContent = { Text(area.name) },
                                supportingContent = { Text("Map only · " + Formatter.formatShortFileSize(context, area.bytes)) },
                                leadingContent = { Icon(Icons.Outlined.Map, contentDescription = null) },
                                trailingContent = {
                                    IconButton(onClick = { maps.delete(area.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }

                when {
                    regions == null && failed -> item {
                        Text(
                            "Couldn't load the list of regions. Check your connection.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    regions == null -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    query.isNotBlank() -> {
                        val q = RegionCatalog.searchable(query.trim())
                        val found = all
                            .filter { q in RegionCatalog.searchable(it.name) }
                            .sortedWith(compareBy({ !RegionCatalog.searchable(it.name).startsWith(q) }, { it.name }))
                            .take(60)
                        if (found.isEmpty()) item {
                            Text("Nothing found", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                        }
                        items(found, key = { "found-" + it.id }) { region ->
                            RegionRow(
                                region = region,
                                within = region.parent?.let { byId[it]?.name },
                                hasParts = children[region.id].orEmpty().isNotEmpty(),
                                saved = region.id in savedIds,
                                onOpen = {
                                    query = ""
                                    path = generateSequence(region) { r -> r.parent?.let { byId[it] } }.map { it.id }.toList().reversed()
                                },
                                onDownload = { confirming = region },
                            )
                        }
                    }
                    else -> {
                        val current = path.lastOrNull()?.let { byId[it] }
                        item {
                            if (current == null) {
                                SectionTitle("Download")
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { path = path.dropLast(1) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                                    }
                                    Text(
                                        path.mapNotNull { byId[it]?.name }.joinToString("  ›  "),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (current != null) item {
                            RegionRow(
                                region = current,
                                within = null,
                                hasParts = false,
                                saved = current.id in savedIds,
                                whole = true,
                                onOpen = { confirming = current },
                                onDownload = { confirming = current },
                            )
                        }
                        val shown = children[current?.id].orEmpty().sortedBy { it.name }
                        items(shown, key = { "child-" + it.id }) { region ->
                            val hasParts = children[region.id].orEmpty().isNotEmpty()
                            RegionRow(
                                region = region,
                                within = null,
                                hasParts = hasParts,
                                saved = region.id in savedIds,
                                onOpen = { if (hasParts) path = path + region.id else confirming = region },
                                onDownload = { confirming = region },
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Maps by OpenFreeMap and routing data by BRouter, both from OpenStreetMap. Directions work without a connection " +
                            "where the routing data is downloaded, in English. Regions by Geofabrik.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    confirming?.let { region ->
        DownloadDialog(
            region = region,
            onDownload = {
                OfflineRegions.download(context, region, maps, styleUrl, context.resources.displayMetrics.density)
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
    deleting?.let { region ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${region.name}?") },
            text = { Text("Its map and directions won't work without a connection any more.") },
            confirmButton = {
                TextButton(onClick = {
                    OfflineRegions.delete(context, region, maps)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * A region to download: tapping opens its parts, if it has any; the button downloads it all.
 * [whole] is the row for all of the region that's open.
 */
@Composable
private fun RegionRow(
    region: Region,
    within: String?,
    hasParts: Boolean,
    saved: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    whole: Boolean = false,
) {
    val context = LocalContext.current
    val size = if (region.downloadable) "About " + Formatter.formatShortFileSize(context, region.mapBytes) else "Too large; pick a part"
    ListItem(
        headlineContent = { Text(if (whole) "All of ${region.name}" else region.name) },
        supportingContent = { Text(listOfNotNull(within, size).joinToString(" · ")) },
        leadingContent = { Icon(if (whole) Icons.Outlined.Download else Icons.Outlined.Public, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    saved -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "On this phone",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp),
                    )
                    region.downloadable && !whole -> IconButton(onClick = onDownload) {
                        Icon(Icons.Outlined.Download, contentDescription = "Download ${region.name}")
                    }
                }
                if (hasParts) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = hasParts || (region.downloadable && !saved), onClick = onOpen),
    )
}

/** A downloaded region: how far along it is, or how much room it takes. */
@Composable
private fun SavedRegionRow(
    region: SavedRegion,
    map: OfflineArea?,
    transfers: Map<String, OfflineRegions.Transfer>,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val routing = region.segments.map { OfflineRouting.segmentFile(context, it) }
    val routingBytes = routing.filter { it.exists() }.sumOf { it.length() }
    val pending = region.segments.mapNotNull { transfers[it] }
    val routingDone = pending.isEmpty() && routing.all { it.exists() }
    val mapDone = map?.complete == true
    val stopped = pending.any { it.error != null }
    // Progress by size, as the map is usually much bigger than its routing data. The map's
    // final size shows from how far along it is, once there's enough to go on; until then,
    // it's the estimate from before the download.
    val mapBytes = map?.bytes ?: 0L
    val mapTotal = when {
        mapDone -> mapBytes
        map != null && map.progress > 0.05f -> (mapBytes / map.progress).toLong()
        else -> maxOf(region.mapEstimate, mapBytes)
    }
    // A file just started has no size yet: count it as an average one meanwhile.
    val routingTotal = routingBytes + pending.sumOf { if (it.total > 0) it.total else 100_000_000L }
    val routingSoFar = routingBytes + pending.sumOf { it.done }
    val total = (mapTotal + routingTotal).coerceAtLeast(1)
    val progress = ((mapBytes + routingSoFar).toFloat() / total).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(region.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            map?.error != null -> map.error
                            mapDone && routingDone -> "Map " + Formatter.formatShortFileSize(context, mapBytes) +
                                " · Directions " + Formatter.formatShortFileSize(context, routingBytes)
                            stopped -> "Download stopped"
                            else -> "Downloading · " + Formatter.formatShortFileSize(context, mapBytes + routingSoFar) +
                                " of about " + Formatter.formatShortFileSize(context, total)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (stopped) TextButton(onClick = { OfflineRegions.retry(context) }) { Text("Retry") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete ${region.name}") }
            }
            if (!(mapDone && routingDone) && map?.error == null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
                )
            }
        }
    }
}

/** What downloading [region] takes, before it's started. */
@Composable
private fun DownloadDialog(region: Region, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var routing by remember(region.id) { mutableStateOf<Map<String, Long>?>(null) }
    LaunchedEffect(region.id) { routing = OfflineRegions.missingSegments(context, region) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
        title = { Text(region.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SizeLine("Map", "About " + Formatter.formatShortFileSize(context, region.mapBytes))
                SizeLine(
                    "Directions",
                    routing?.let { r ->
                        if (r.isEmpty()) "Already on this phone" else Formatter.formatShortFileSize(context, r.values.sum())
                    } ?: "…",
                )
                Text(
                    "Directions data comes in large squares, so it can cover more than the region. " +
                        "Downloads carry on in the background; Wi-Fi is best.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SizeLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
