@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import android.text.format.DateFormat
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material3.LocalContentColor
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Date

/** A north arrow that turns with the map; tapping it points the map north again. */
@Composable
fun Compass(bearing: () -> Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val north = MaterialTheme.colorScheme.error
    val south = MaterialTheme.colorScheme.onSurfaceVariant
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics { contentDescription = "Point north" },
    ) {
        Canvas(
            Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = -bearing() }
        ) {
            val cx = size.width / 2
            val half = size.width * 0.24f
            val mid = size.height / 2
            drawPath(Path().apply { moveTo(cx, 0f); lineTo(cx + half, mid); lineTo(cx - half, mid); close() }, north)
            drawPath(Path().apply { moveTo(cx, size.height); lineTo(cx + half, mid); lineTo(cx - half, mid); close() }, south)
        }
    }
}

private val TravelMode.icon: ImageVector
    get() = when (this) {
        TravelMode.Car -> Icons.Filled.DirectionsCar
        TravelMode.Bike -> Icons.AutoMirrored.Filled.DirectionsBike
        TravelMode.Walk -> Icons.AutoMirrored.Filled.DirectionsWalk
        TravelMode.Transit -> Icons.Filled.DirectionsTransit
    }

/** OSRM maneuver types and modifiers: https://project-osrm.org/docs/v5.24.0/api/#stepmaneuver-object */
private fun maneuverIcon(m: Maneuver?): ImageVector = when (m?.type) {
    null -> Icons.Filled.Straight
    "depart" -> Icons.Filled.TripOrigin
    "arrive" -> Icons.Filled.Flag
    "merge" -> Icons.Filled.Merge
    "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
        if (m.modifier?.contains("left") == true && m.type == "roundabout turn") Icons.Filled.RoundaboutLeft
        else Icons.Filled.RoundaboutRight
    "on ramp", "off ramp" -> when {
        m.modifier?.contains("left") == true -> Icons.Filled.RampLeft
        else -> Icons.Filled.RampRight
    }
    "fork" -> if (m.modifier?.contains("left") == true) Icons.Filled.ForkLeft else Icons.Filled.ForkRight
    else -> directionIcon(m.modifier ?: "straight")
}

/** An arrow for a direction like `left`, `slight right` or `uturn`, as used by turns and lanes. */
private fun directionIcon(direction: String): ImageVector = when (direction) {
    "uturn" -> Icons.Filled.UTurnLeft
    "sharp right" -> Icons.Filled.TurnSharpRight
    "right" -> Icons.Filled.TurnRight
    "slight right" -> Icons.Filled.TurnSlightRight
    "slight left" -> Icons.Filled.TurnSlightLeft
    "left" -> Icons.Filled.TurnLeft
    "sharp left" -> Icons.Filled.TurnSharpLeft
    else -> Icons.Filled.Straight
}

/** The lanes at the next junction, with the ones to be in lit up. */
@Composable
private fun LaneRow(lanes: List<Lane>, modifier: Modifier = Modifier) {
    val content = LocalContentColor.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        lanes.forEach { lane ->
            Icon(
                directionIcon(lane.shown),
                contentDescription = null,
                tint = if (lane.valid) content else content.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** A round speed limit sign, in km/h or mph as the settings say. */
@Composable
fun SpeedLimitSign(kmh: Int, modifier: Modifier = Modifier) {
    val value = if (distanceUnits == Units.Imperial) ((kmh / 1.609344) / 5).roundToInt() * 5 else kmh
    Surface(
        shape = CircleShape,
        color = Color.White,
        contentColor = Color.Black,
        border = BorderStroke(5.dp, Color(0xFFD32F2F)),
        shadowElevation = 4.dp,
        modifier = modifier.size(56.dp).semantics { contentDescription = "Speed limit $value" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun etaText(context: android.content.Context, seconds: Double): String =
    DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (seconds * 1000).toLong()))

@Composable
fun DirectionsHeader(
    /** Where the route starts; null for the user's location. */
    origin: Place?,
    destination: Place,
    onChangeOrigin: () -> Unit,
    onChangeDestination: () -> Unit,
    onSwap: () -> Unit,
    mode: TravelMode,
    onModeChange: (TravelMode) -> Unit,
    onBack: () -> Unit,
    stops: List<Place>,
    onAddStop: () -> Unit,
    onRemoveStop: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Public transport plans from A to B only.
    val withStops = mode != TravelMode.Transit
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Tapping either end picks another place; the arrows swap them.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.TripOrigin, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            origin?.name ?: "Your location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (origin == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClickLabel = "Change starting point", onClick = onChangeOrigin)
                                .padding(vertical = 4.dp),
                        )
                        IconButton(onClick = onSwap, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.SwapVert, contentDescription = "Swap start and destination", modifier = Modifier.size(20.dp))
                        }
                    }
                    if (withStops) stops.forEach { stop ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.TripOrigin, contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stop.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemoveStop(stop) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove stop", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Place, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            destination.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClickLabel = "Change destination", onClick = onChangeDestination)
                                .padding(vertical = 4.dp),
                        )
                        if (withStops) {
                            IconButton(onClick = onAddStop, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.AddLocationAlt, contentDescription = "Add stop", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp)
            ) {
                TravelMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = m == mode,
                        onClick = { onModeChange(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, TravelMode.entries.size),
                        // Four modes don't fit with labels; the icons say enough.
                        icon = {},
                        label = { Icon(m.icon, contentDescription = m.label) },
                    )
                }
            }
        }
    }
}

@Composable
fun RoutePanel(
    routes: List<Route>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onStart: () -> Unit,
    onOpenElsewhere: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSteps by rememberSaveable { mutableStateOf(false) }
    val route = routes.getOrNull(selectedIndex)
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            when {
                route == null && loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Finding routes…", style = MaterialTheme.typography.bodyLarge)
                }
                route == null -> {
                    Text(error ?: "No route found.", style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry) { Text("Try again") }
                        TextButton(onClick = onOpenElsewhere) { Text("Open in another app") }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatDuration(route.duration),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${formatDistance(route.distance)} · arrive ${etaText(context, route.duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    if (routes.size > 1) {
                        Row(
                            Modifier
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            routes.forEachIndexed { i, r ->
                                FilterChip(
                                    selected = i == selectedIndex,
                                    onClick = { onSelect(i) },
                                    label = { Text("${formatDuration(r.duration)} · ${formatDistance(r.distance)}") },
                                )
                            }
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onStart) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start")
                        }
                        OutlinedButton(onClick = { showSteps = !showSteps }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (showSteps) "Hide steps" else "Steps")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onOpenElsewhere) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in another app")
                        }
                    }
                    AnimatedVisibility(showSteps) {
                        LazyColumn(
                            Modifier
                                .heightIn(max = listHeight(280.dp))
                                .padding(top = 8.dp)
                        ) {
                            items(route.maneuvers) { m ->
                                ListItem(
                                    headlineContent = { Text(m.instruction) },
                                    leadingContent = { Icon(maneuverIcon(m), contentDescription = null) },
                                    trailingContent = if (m.distance > 0) {
                                        { Text(formatDistance(m.distance), style = MaterialTheme.typography.labelMedium) }
                                    } else null,
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The big next-turn banner shown at the top while navigating. */
@Composable
fun NavigationBanner(
    route: Route?,
    progress: RouteProgress?,
    destination: Place,
    rerouting: Boolean,
    modifier: Modifier = Modifier,
) {
    val next = progress?.next ?: route?.maneuvers?.firstOrNull()
    val arrived = progress?.arrived == true
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (arrived) Icons.Filled.Flag else maneuverIcon(next),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                when {
                    arrived -> {
                        Text("You've arrived", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(destination.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    }
                    rerouting -> Text("Rerouting…", style = MaterialTheme.typography.headlineSmall)
                    else -> {
                        if (progress != null) {
                            Text(
                                formatDistance(progress.distanceToNext),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            next?.instruction ?: "Head to ${destination.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Lanes matter coming up to the junction, not kilometers before it.
                        val lanes = next?.lanes.orEmpty()
                        if (lanes.size > 1 && lanes.any { !it.valid } && (progress?.distanceToNext ?: 0.0) < 1000) {
                            LaneRow(lanes, Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationPanel(
    route: Route?,
    progress: RouteProgress?,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = progress?.remainingTime ?: route?.duration ?: 0.0
    val distance = progress?.remainingDistance ?: route?.distance ?: 0.0
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatDuration(time),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${formatDistance(distance)} · arrive ${etaText(context, time)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleMute) {
                Icon(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (muted) "Turn voice on" else "Mute voice",
                )
            }
            FilledTonalButton(onClick = onEnd) { Text("End") }
        }
    }
}
