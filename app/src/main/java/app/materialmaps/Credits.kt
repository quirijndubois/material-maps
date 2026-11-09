@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.Libs

const val SOURCE_URL = "https://github.com/quirijndubois/material-maps"

/** Sent with every request; Transitous and FOSSGIS ask apps to say who they are and how to reach them. */
val USER_AGENT = "MaterialMaps/${BuildConfig.VERSION_NAME} (Android; +$SOURCE_URL)"

/** The small "i" on the map that opens [CreditsSheet]. Replaces MapLibre's attribution button. */
@Composable
fun CreditsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Credits and licenses",
                modifier = Modifier.padding(4.dp).size(18.dp),
            )
        }
    }
}

private data class Credit(val icon: ImageVector, val title: String, val text: String, val url: String)

// Everything the services and data licenses ask us to show, in one place.
private val credits = listOf(
    Credit(
        Icons.Outlined.Map, "© OpenStreetMap contributors",
        "Map data, places, addresses and routes. Available under the Open Database License.",
        "https://www.openstreetmap.org/copyright",
    ),
    Credit(
        Icons.Outlined.Build, "Improve this map",
        "Something missing or wrong? Fix it on OpenStreetMap.",
        "https://www.openstreetmap.org/fixthemap",
    ),
    Credit(
        Icons.Outlined.Layers, "OpenFreeMap · © OpenMapTiles",
        "Map tiles and the map style.",
        "https://openfreemap.org",
    ),
    Credit(
        Icons.Outlined.SatelliteAlt, "Esri World Imagery",
        "Satellite photos. Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community.",
        "https://www.arcgis.com/home/item.html?id=10df2279f9684e4a9f6a7f08febac2a9",
    ),
    Credit(
        Icons.Outlined.Terrain, "Terrain Tiles · Mapzen",
        "Elevation for terrain shading, from the Terrain Tiles open dataset on AWS.",
        "https://registry.opendata.aws/terrain-tiles/",
    ),
    Credit(
        Icons.Outlined.Hiking, "Waymarked Trails",
        "Hiking and cycling routes, © Sarah Hoffmann, CC BY-SA 3.0.",
        "https://waymarkedtrails.org",
    ),
    Credit(
        Icons.Outlined.Subway, "VersaTiles",
        "Metro and tram lines in the city-wide view.",
        "https://versatiles.org",
    ),
    Credit(
        Icons.Outlined.DownhillSkiing, "OpenSnowMap",
        "Ski slopes, CC BY-SA 2.0.",
        "https://www.opensnowmap.org",
    ),
    Credit(
        Icons.Outlined.Sailing, "OpenSeaMap",
        "Sea marks, CC BY-SA 2.0.",
        "https://www.openseamap.org",
    ),
    Credit(
        Icons.AutoMirrored.Outlined.DirectionsWalk, "Valhalla · FOSSGIS",
        "Driving, cycling and walking routes, served by FOSSGIS e.V.",
        "https://valhalla1.openstreetmap.de",
    ),
    Credit(
        Icons.Outlined.DirectionsBus, "Transitous",
        "Public transport, departures and search. See the data sources and their licenses.",
        "https://transitous.org/sources/",
    ),
    Credit(
        Icons.Outlined.Search, "Photon by komoot",
        "Search when Transitous can't be reached.",
        "https://photon.komoot.io",
    ),
    Credit(
        Icons.Outlined.Schedule, "Nominatim · Overpass API",
        "Opening hours, phone numbers and websites from OpenStreetMap, and kinds of places nearby.",
        "https://nominatim.org",
    ),
    Credit(
        Icons.Outlined.MenuBook, "Wikipedia · Wikimedia Commons",
        "Descriptions and photos of places. Text under CC BY-SA; photos under their own licenses.",
        "https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use",
    ),
)

@Composable
fun CreditsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Material Maps ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "Free software under the GNU GPL v3.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            CreditRow(Credit(Icons.Outlined.Code, "Source code", "github.com/quirijndubois/material-maps", SOURCE_URL)) {
                context.openUrl(SOURCE_URL)
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(
                "Data and services",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            credits.forEach { credit -> CreditRow(credit) { context.openUrl(credit.url) } }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Open source licenses") },
                supportingContent = { Text("The libraries this app is built with") },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showLicenses = true },
            )
        }
    }
    if (showLicenses) LicensesScreen(onBack = { showLicenses = false })
}

@Composable
private fun CreditRow(credit: Credit, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(credit.title) },
        supportingContent = { Text(credit.text) },
        leadingContent = { Icon(credit.icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// The generated BSD text only has a placeholder copyright line; this is MapLibre's own notice.
private const val MAPLIBRE_NOTICE = """MapLibre Native (BSD 2-Clause License)
Copyright (c) 2021 MapLibre contributors
Copyright (c) 2018-2021 MapTiler.com
Copyright (c) 2014-2020 Mapbox"""

/** Every library in the APK with its license, generated at build time by the AboutLibraries plugin. */
@Composable
private fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val libraries = remember {
        runCatching {
            val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
            Libs.Builder().withJson(json).build().libraries.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Open source licenses") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            LazyColumn(Modifier.padding(padding)) {
                item {
                    Text(
                        MAPLIBRE_NOTICE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
                items(libraries, key = { it.uniqueId }) { lib ->
                    val expanded = open == lib.uniqueId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open = if (expanded) null else lib.uniqueId }
                            .animateContentSize()
                    ) {
                        ListItem(
                            headlineContent = { Text(lib.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        lib.artifactVersion,
                                        lib.licenses.joinToString { it.spdxId ?: it.name }.ifBlank { null },
                                    ).joinToString(" · ")
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (expanded) {
                            lib.licenses.forEach { license ->
                                Text(
                                    license.licenseContent ?: license.url ?: license.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser; nothing sensible to do.
    }
}
