@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Slider
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PedalBike
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Tornado
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Co2
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Storm
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.fillMaxHeight
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.io.File
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Badge
import androidx.compose.material.icons.outlined.Place
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Map types and what to show on the map, from the layers button. */
@Composable
fun LayersSheet(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onDismiss: () -> Unit,
    /** For the map type previews: [previewStyle] gives a type's style (null: satellite). */
    dark: Boolean,
    previewStyle: suspend (MapStyle?) -> Style.Builder?,
) {
    var category by rememberSaveable { mutableStateOf(LayerCategory.Map) }
    val colors = MaterialTheme.colorScheme
    // Its content is always the same height, and only the layers scroll, so nothing done in it
    // makes it grow or shrink. It opens half way and can be pulled up all the way, staying at
    // either.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.8f)
                .navigationBarsPadding()
        ) {
            // The map underneath everything, each type shown as it looks.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                (MapStyle.entries.map { it to it.label } + (null to "Satellite")).forEach { (style, label) ->
                    val selected = if (style == null) settings.satellite else !settings.satellite && settings.mapStyle == style
                    MapTypeCard(
                        label = label,
                        style = style,
                        selected = selected,
                        preview = rememberMapPreview(style, dark, previewStyle),
                        dark = dark,
                        onClick = {
                            onChange(if (style == null) settings.copy(satellite = true) else settings.copy(mapStyle = style, satellite = false))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AnimatedVisibility(settings.satellite) { AerialYears(settings, onChange) }

            // What goes on top of it, a kind at a time, picked like in a navigation bar.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                LayerCategory.entries.forEach { c ->
                    val count = settings.overlays.count { it.group in c.groups }
                    val selected = category == c
                    val pill by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, label = "category")
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(selected = selected, role = Role.Tab, indication = null, interactionSource = null) { category = c }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
                            Box(
                                Modifier
                                    .size(width = 56.dp, height = 32.dp)
                                    .background(pill, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    c.icon,
                                    contentDescription = null,
                                    tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Text(
                            c.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // Turns every layer off; there whenever the sheet is, so nothing moves.
                IconButton(
                    onClick = { onChange(settings.copy(overlays = emptySet())) },
                    enabled = settings.overlays.isNotEmpty(),
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Icon(Icons.Outlined.LayersClear, contentDescription = "Turn off all layers")
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                for (group in category.groups) {
                    if (category.groups.size > 1) {
                        Text(
                            group.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                    val details = if (group != OverlayGroup.Details) emptyList() else listOf(
                        LayerTile("Labels", "Names on the map", Icons.Outlined.TextFields, settings.labels) { onChange(settings.copy(labels = it)) },
                        LayerTile("3D buildings", "Tilt to see", Icons.Outlined.Apartment, settings.buildings3d) { onChange(settings.copy(buildings3d = it)) },
                        LayerTile("Saved places", "Your lists", Icons.Outlined.Bookmarks, settings.showSaved) { onChange(settings.copy(showSaved = it)) },
                    )
                    val overlays = Overlay.entries.filter { it.group == group }.map { overlay ->
                        LayerTile(overlay.label, overlay.note, overlay.icon, overlay in settings.overlays) { on ->
                            onChange(settings.copy(overlays = if (on) settings.overlays + overlay else settings.overlays - overlay))
                        }
                    }
                    LayerTiles(details + overlays)
                }
            }
        }
    }
}

/** A map type: a picture of the map in it and its name, outlined when it's the one shown. */
@Composable
private fun MapTypeCard(
    label: String,
    style: MapStyle?,
    selected: Boolean,
    preview: ImageBitmap?,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val border by animateDpAsState(if (selected) 3.dp else 1.dp, label = "map type border")
    val borderColor by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "map type border color")
    Column(
        modifier.selectable(selected = selected, role = Role.RadioButton, indication = null, interactionSource = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_ASPECT)
                .clip(RoundedCornerShape(20.dp))
                .border(border, borderColor, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
        ) {
            Crossfade(preview, label = "map type preview") { image ->
                if (image != null) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    MapSwatch(style, dark, Modifier.fillMaxSize())
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A sketch of a map type's colors (land, water, roads), until the real picture is ready. */
@Composable
private fun MapSwatch(style: MapStyle?, dark: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val (land, water, road) = when {
        style == null -> Triple(Color(0xFF55603F), Color(0xFF243848), Color(0xFF9A9A88))
        dark -> Triple(colors.surfaceContainer, colors.surfaceContainerHighest, colors.outline)
        style == MapStyle.Minimal -> Triple(Color(0xFFF2F3F0), Color(0xFFC8D0D4), Color(0xFFFFFFFF))
        else -> Triple(Color(0xFFF2EFE9), Color(0xFFAAD3DF), Color(0xFFF7D98B))
    }
    Canvas(modifier.background(land)) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.3f, h * 0.55f, w * 0.45f, h * 0.8f, w * 0.7f, h)
                lineTo(0f, h)
                close()
            },
            water,
        )
        drawLine(road, Offset(w * 0.1f, 0f), Offset(w * 0.75f, h * 0.7f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
        drawLine(road, Offset(w, h * 0.2f), Offset(w * 0.3f, h * 0.45f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
    }
}

/** Pictures of the map in each type, kept so the sheet opens with them, by type and look. */
private val mapPreviews = HashMap<String, ImageBitmap>()

/**
 * Where every preview is of: central Amsterdam, from the IJ over the canals to the Vondelpark,
 * where the types differ most, with water, a big park and main roads.
 */
private val PREVIEW_CAMERA = CameraPosition.Builder().target(LatLng(52.3615, 4.8800)).zoom(13.3).build()
/** Wider than tall. */
private const val PREVIEW_ASPECT = 1.5f
/** Bump when the previews change, so older ones on the phone are made again. */
private const val PREVIEW_VERSION = 6
/** Extra drawn below the picture, where MapLibre puts the map's credits, and cut off. */
private const val PREVIEW_CREDITS = 32

/**
 * A picture of the map in [style] (null: satellite), always of the same place, made by
 * MapLibre off screen the first time and kept on the phone after that; null until it's ready,
 * or if it can't be made. In the dark, it's made again for new wallpaper colors.
 */
@Composable
private fun rememberMapPreview(style: MapStyle?, dark: Boolean, styleFor: suspend (MapStyle?) -> Style.Builder?): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val colors = MaterialTheme.colorScheme
    val look = if (dark && style != null) "dark-%08x-%08x".format(colors.surface.toArgb(), colors.tertiary.toArgb()) else "light"
    val key = "v$PREVIEW_VERSION-${style?.name?.lowercase() ?: "satellite"}-$look"
    var image by remember(key) { mutableStateOf(mapPreviews[key]) }
    LaunchedEffect(key) {
        if (image != null) return@LaunchedEffect
        val file = File(context.cacheDir, "map-previews/$key.png")
        val kept = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() }
        val bitmap = kept ?: run {
            // Drawn twice the size at half the scale, so names come out small, as on a big map.
            val builder = styleFor(style) ?: return@LaunchedEffect
            val options = MapSnapshotter.Options(270, (270 / PREVIEW_ASPECT).toInt() + PREVIEW_CREDITS)
                .withPixelRatio(density / 2)
                .withStyleBuilder(builder)
                .withCameraPosition(PREVIEW_CAMERA)
                .withLogo(false)
            suspendCancellableCoroutine<Bitmap?> { continuation ->
                val snapshotter = MapSnapshotter(context, options)
                continuation.invokeOnCancellation { snapshotter.cancel() }
                snapshotter.start(
                    { snapshot ->
                        val full = snapshot.bitmap
                        val height = (full.height * (270 / PREVIEW_ASPECT) / (270 / PREVIEW_ASPECT + PREVIEW_CREDITS)).toInt()
                        if (continuation.isActive) continuation.resume(Bitmap.createBitmap(full, 0, 0, full.width, height))
                    },
                    { if (continuation.isActive) continuation.resume(null) },
                )
            }?.also { made ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { made.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        } ?: return@LaunchedEffect
        bitmap.asImageBitmap().let {
            mapPreviews[key] = it
            image = it
        }
    }
    return image
}

/** The kinds of layers, each with the groups of overlays in it. */
private enum class LayerCategory(val label: String, val icon: ImageVector, val groups: List<OverlayGroup>) {
    Map("Map", Icons.Outlined.Map, listOf(OverlayGroup.Details, OverlayGroup.Netherlands)),
    Transport("Transport", Icons.Outlined.Train, listOf(OverlayGroup.Transport)),
    Outdoors("Outdoors", Icons.Outlined.Hiking, listOf(OverlayGroup.Outdoors)),
    Weather("Weather", Icons.Outlined.WbCloudy, listOf(OverlayGroup.Weather, OverlayGroup.Air)),
    Places("Places", Icons.Outlined.Place, listOf(OverlayGroup.Places)),
}

private class LayerTile(val label: String, val note: String?, val icon: ImageVector, val on: Boolean, val onChange: (Boolean) -> Unit)

/** Two to a row, like the phone's quick settings. */
@Composable
private fun LayerTiles(tiles: List<LayerTile>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { LayerTileButton(it, Modifier.weight(1f)) }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LayerTileButton(tile: LayerTile, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (tile.on) colors.primaryContainer else colors.surfaceContainerHighest, label = "tile")
    val content by animateColorAsState(if (tile.on) colors.onPrimaryContainer else colors.onSurface, label = "tile content")
    val badge by animateColorAsState(if (tile.on) colors.primary else colors.surfaceContainerLow, label = "tile icon")
    val iconColor by animateColorAsState(if (tile.on) colors.onPrimary else colors.onSurfaceVariant, label = "tile icon content")
    Surface(
        checked = tile.on,
        onCheckedChange = tile.onChange,
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        modifier = modifier.height(64.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(badge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tile.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(tile.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tile.note != null) {
                    Text(
                        tile.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A word on what each overlay shows, or where and when. */
private val Overlay.note: String?
    get() = when (this) {
        Overlay.Terrain -> "Hill shading"
        Overlay.Contours -> "Height lines"
        Overlay.Rain -> "Last 2 hours"
        Overlay.Clouds, Overlay.LiveSatellite, Overlay.Lightning -> "Last 3 hours"
        Overlay.Wind, Overlay.CloudForecast, Overlay.Storms -> "3-day forecast"
        Overlay.Pm25, Overlay.Pm10, Overlay.No2, Overlay.Ozone, Overlay.Dust -> "Europe · forecast"
        Overlay.Uv, Overlay.Co2, Overlay.Methane -> "World · forecast"
        Overlay.AirStations -> "Netherlands · now"
        Overlay.AirSensors -> "Citizen sensors · now"
        Overlay.Trains -> "Rail lines"
        Overlay.Metro, Overlay.Trams -> "Lines"
        Overlay.Buses -> "Next 30 minutes"
        Overlay.Railways -> "Tracks and signals"
        Overlay.Hiking, Overlay.Cycling, Overlay.Mtb -> "Marked routes"
        Overlay.Slopes -> "Pistes and lifts"
        Overlay.SeaMarks -> "Buoys and lights"
        Overlay.Charging, Overlay.Water, Overlay.Toilets, Overlay.BikeParking -> "Zoom in"
        Overlay.Defibrillators, Overlay.BikeRepair -> "Zoom in · slower"
        Overlay.WikiArticles -> "Articles nearby"
        Overlay.Elevation -> "Height of the ground"
        Overlay.Parcels -> "Zoom in close"
    }

/** What the overlays are loading, and how far along it is. */
@Composable
fun LoadingPill(loading: Map<String, Loading.Progress>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val started = loading.values.sumOf { it.started }
    val finished = loading.values.sumOf { it.finished }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh.copy(alpha = 0.92f),
        modifier = modifier.widthIn(max = 420.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Loading " + loading.keys.sorted().joinToString(", ") + if (started > 1) "  ·  $finished/$started" else "",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (started > 1) {
                LinearProgressIndicator(
                    progress = { finished.toFloat() / started },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

/** Earlier years' aerial photos of the Netherlands, to see how a place has changed. */
@Composable
private fun AerialYears(settings: Settings, onChange: (Settings) -> Unit) {
    Column {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf<Int?>(null) + AERIAL_YEARS).forEach { year ->
                FilterChip(
                    selected = settings.aerialYear == year,
                    onClick = { onChange(settings.copy(aerialYear = year)) },
                    label = { Text(year?.toString() ?: "Latest") },
                )
            }
        }
    }
}

private val MapStyle.icon: ImageVector
    get() = when (this) {
        MapStyle.Standard -> Icons.Outlined.Map
        MapStyle.Minimal -> Icons.Outlined.Tonality
    }

val Overlay.icon: ImageVector
    get() = when (this) {
        Overlay.Terrain -> Icons.Outlined.Terrain
        Overlay.Contours -> Icons.Outlined.Waves
        Overlay.Rain -> Icons.Outlined.Umbrella
        Overlay.Clouds -> Icons.Outlined.Cloud
        Overlay.LiveSatellite -> Icons.Outlined.Public
        Overlay.Lightning -> Icons.Outlined.Thunderstorm
        Overlay.Wind -> Icons.Outlined.Air
        Overlay.CloudForecast -> Icons.Outlined.WbCloudy
        Overlay.Storms -> Icons.Outlined.Storm
        Overlay.Pm25 -> Icons.Outlined.Grain
        Overlay.Pm10 -> Icons.Outlined.Masks
        Overlay.No2 -> Icons.Outlined.Factory
        Overlay.Ozone -> Icons.Outlined.FilterDrama
        Overlay.Dust -> Icons.Outlined.Tornado
        Overlay.Uv -> Icons.Outlined.WbSunny
        Overlay.Co2 -> Icons.Outlined.Co2
        Overlay.Methane -> Icons.Outlined.BubbleChart
        Overlay.AirStations -> Icons.Outlined.Sensors
        Overlay.AirSensors -> Icons.Outlined.Router
        Overlay.Trains -> Icons.Outlined.Train
        Overlay.Metro -> Icons.Outlined.Subway
        Overlay.Trams -> Icons.Outlined.Tram
        Overlay.Buses -> Icons.Outlined.DirectionsBus
        Overlay.Railways -> Icons.Outlined.DirectionsRailway
        Overlay.Hiking -> Icons.Outlined.Hiking
        Overlay.Cycling -> Icons.AutoMirrored.Outlined.DirectionsBike
        Overlay.Mtb -> Icons.Outlined.Forest
        Overlay.Slopes -> Icons.Outlined.DownhillSkiing
        Overlay.SeaMarks -> Icons.Outlined.Sailing
        Overlay.Charging -> Icons.Outlined.EvStation
        Overlay.Water -> Icons.Outlined.WaterDrop
        Overlay.Toilets -> Icons.Outlined.Wc
        Overlay.Defibrillators -> Icons.Outlined.MonitorHeart
        Overlay.BikeParking -> Icons.Outlined.PedalBike
        Overlay.BikeRepair -> Icons.Outlined.Handyman
        Overlay.WikiArticles -> Icons.AutoMirrored.Outlined.MenuBook
        Overlay.Elevation -> Icons.Outlined.Height
        Overlay.Parcels -> Icons.Outlined.GridOn
    }

/**
 * Steps through [times], shown in the clock's [pattern], with a color key for each of
 * [legends] above.
 */
@Composable
fun TimeSlider(
    times: List<Long>,
    index: Int,
    onIndex: (Int) -> Unit,
    pattern: String,
    modifier: Modifier = Modifier,
    legends: List<Pair<String, Scale>> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    val time = times.getOrNull(index)
    val clock = remember(time, pattern) {
        time?.let { DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault())) }.orEmpty()
    }
    Surface(
        shape = RoundedCornerShape(if (legends.isEmpty()) 28.dp else 20.dp),
        color = colors.surfaceContainerHigh.copy(alpha = 0.92f),
        modifier = modifier.widthIn(max = 420.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
            legends.forEach { (label, scale) -> Legend(label, scale, Modifier.padding(top = 6.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = index.coerceAtLeast(0).toFloat(),
                    onValueChange = { onIndex(it.roundToInt().coerceIn(0, times.lastIndex)) },
                    valueRange = 0f..times.lastIndex.coerceAtLeast(1).toFloat(),
                    steps = (times.size - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f).height(36.dp),
                )
                Text(
                    clock,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** What a forecast's colors mean: its name, the colors, and the values at either end. */
@Composable
private fun Legend(label: String, scale: Scale, modifier: Modifier = Modifier) {
    fun value(v: Float) = if (v != 0f && kotlin.math.abs(v) < 10) "%.1f".format(v) else v.roundToInt().toString()
    val unit = scale.unit.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
    Column(modifier) {
        Text(
            "$label · ${value(scale.stops.first().first)}–${value(scale.stops.last().first)}$unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    Brush.horizontalGradient(scale.stops.map { (_, c) -> Color(c).copy(alpha = maxOf(Color(c).alpha, 0.35f)) }),
                    RoundedCornerShape(3.dp),
                )
        )
    }
}
