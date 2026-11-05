@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.materialmaps

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Date

fun transitModeIcon(mode: String): ImageVector = when (mode) {
    "WALK" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "BIKE", "RENTAL" -> Icons.AutoMirrored.Filled.DirectionsBike
    "CAR" -> Icons.Filled.DirectionsCar
    "BUS", "COACH" -> Icons.Filled.DirectionsBus
    "TRAM", "CABLE_CAR" -> Icons.Filled.Tram
    "SUBWAY", "METRO" -> Icons.Filled.Subway
    "FERRY" -> Icons.Filled.DirectionsBoat
    else -> Icons.Filled.Train
}

private fun parseColor(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** A line number in the operator's colors, like the signs at the stop. */
@Composable
fun LineBadge(mode: String, line: String?, color: String?, textColor: String?) {
    val background = parseColor(color) ?: MaterialTheme.colorScheme.secondaryContainer
    val foreground = parseColor(textColor)
        ?: if (color != null) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(transitModeIcon(mode), contentDescription = null, modifier = Modifier.size(16.dp))
            if (!line.isNullOrBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    line, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
    }
}

@Composable
fun clock(millis: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(millis))

/** "now", "in 4 min" or a clock time for later. */
@Composable
private fun untilText(millis: Long): String {
    val minutes = ((millis - System.currentTimeMillis()) / 60_000).toInt()
    return when {
        minutes <= 0 -> "now"
        minutes < 60 -> "in $minutes min"
        else -> clock(millis)
    }
}

@Composable
private fun DelayText(minutes: Int, realTime: Boolean, cancelled: Boolean) {
    val colors = MaterialTheme.colorScheme
    when {
        cancelled -> Text("Cancelled", color = colors.error, style = MaterialTheme.typography.labelMedium)
        minutes > 0 -> Text("+$minutes", color = colors.error, style = MaterialTheme.typography.labelMedium)
        realTime -> Text("on time", color = colors.tertiary, style = MaterialTheme.typography.labelMedium)
    }
}

/** When to travel: null time means now. */
data class TripTime(val time: Long? = null, val arriveBy: Boolean = false)

@Composable
fun TransitPanel(
    itineraries: List<Itinerary>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    tripTime: TripTime,
    onTripTimeChange: (TripTime) -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLater: (() -> Unit)?,
    onOpenTrip: (TransitLeg) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingTime by remember { mutableStateOf(false) }
    val itinerary = selected?.let { itineraries.getOrNull(it) }
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 12.dp)) {
            if (itinerary == null) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { pickingTime = true },
                        label = {
                            Text(
                                when {
                                    tripTime.time == null -> "Leave now"
                                    tripTime.arriveBy -> "Arrive by ${clock(tripTime.time)}"
                                    else -> "Depart at ${clock(tripTime.time)}"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.weight(1f))
                    if (loading && itineraries.isNotEmpty()) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            when {
                itineraries.isEmpty() && loading -> Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Finding connections…", style = MaterialTheme.typography.bodyLarge)
                }
                itineraries.isEmpty() -> Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(error ?: "No connections found.", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("Try again") }
                }
                itinerary != null -> ItineraryDetails(itinerary, onBack = { onSelect(null) }, onOpenTrip = onOpenTrip)
                else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(itineraries) { i, it ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ItineraryRow(it, onClick = { onSelect(i) })
                    }
                    if (onLater != null) item {
                        TextButton(onClick = onLater, enabled = !loading, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(if (tripTime.arriveBy) "Earlier" else "Later")
                        }
                    }
                }
            }
        }
    }
    if (pickingTime) {
        TripTimeDialog(
            initial = tripTime,
            onDismiss = { pickingTime = false },
            onConfirm = {
                pickingTime = false
                onTripTimeChange(it)
            },
        )
    }
}

@Composable
private fun ItineraryRow(itinerary: Itinerary, onClick: () -> Unit) {
    val firstRide = itinerary.rides.firstOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${clock(itinerary.departure)} – ${clock(itinerary.arrival)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatDuration((itinerary.arrival - itinerary.departure) / 1000.0),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        FlowRow(
            Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itinerary.legs.forEachIndexed { i, leg ->
                if (i > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                    )
                }
                if (leg.isWalk) {
                    Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = "Walk",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
                        )
                        if (itinerary.rides.isEmpty()) {
                            Text(formatDuration((leg.arrival - leg.departure) / 1000.0), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    LineBadge(leg.mode, leg.line, leg.color, leg.textColor)
                }
            }
        }
        if (firstRide != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(clock(firstRide.departure)).append(" from ").append(firstRide.fromName)
                        firstRide.fromTrack?.let { append(" · platform ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                DelayText(firstRide.delayMinutes, firstRide.realTime, firstRide.cancelled)
            }
        }
    }
}

@Composable
private fun ItineraryDetails(itinerary: Itinerary, onBack: () -> Unit, onOpenTrip: (TransitLeg) -> Unit) {
    Row(Modifier.padding(start = 4.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All connections") }
        Text(
            "${clock(itinerary.departure)} – ${clock(itinerary.arrival)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            buildString {
                append(formatDuration((itinerary.arrival - itinerary.departure) / 1000.0))
                if (itinerary.transfers > 0) append(" · ${itinerary.transfers}× transfer")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LazyColumn(Modifier.heightIn(max = 380.dp)) {
        items(itinerary.legs) { leg -> LegRow(leg, onClick = if (leg.tripId != null) ({ onOpenTrip(leg) }) else null) }
    }
}

@Composable
private fun LegRow(leg: TransitLeg, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            clock(leg.departure),
            style = MaterialTheme.typography.labelLarge,
            textDecoration = if (leg.cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier.width(56.dp),
        )
        Column(Modifier.weight(1f)) {
            if (leg.isWalk) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildString {
                            append("Walk ").append(formatDuration((leg.arrival - leg.departure) / 1000.0))
                            leg.distance?.let { append(" (").append(formatDistance(it)).append(")") }
                            append(" to ").append(leg.toName.takeUnless { it == "END" } ?: "destination")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    buildString {
                        append(leg.fromName)
                        leg.fromTrack?.let { append(" · platform ").append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(leg.mode, leg.line, leg.color, leg.textColor)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        leg.headsign?.let { "to $it" } ?: leg.agency.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    DelayText(leg.delayMinutes, leg.realTime, leg.cancelled)
                }
                Text(
                    buildString {
                        append(if (leg.stops > 0) "${leg.stops + 1} stops · " else "")
                        append(formatDuration((leg.arrival - leg.departure) / 1000.0))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append("Get off at ").append(leg.toName).append(" · ").append(clock(leg.arrival))
                        leg.toTrack?.let { append(" · platform ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TripTimeDialog(initial: TripTime, onDismiss: () -> Unit, onConfirm: (TripTime) -> Unit) {
    val context = LocalContext.current
    val start = Calendar.getInstance().apply { initial.time?.let { timeInMillis = it } }
    val state = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var arriveBy by remember { mutableStateOf(initial.arriveBy) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                val picked = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, state.hour)
                    set(Calendar.MINUTE, state.minute)
                    set(Calendar.SECOND, 0)
                    // A time well in the past most likely means tomorrow.
                    if (timeInMillis < now - 60 * 60_000) add(Calendar.DAY_OF_MONTH, 1)
                }
                onConfirm(TripTime(picked.timeInMillis, arriveBy))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(TripTime()) }) { Text("Leave now") }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    listOf(false to "Depart at", true to "Arrive by").forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = arriveBy == value,
                            onClick = { arriveBy = value },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            label = { Text(label) },
                        )
                    }
                }
                TimePicker(state = state)
            }
        },
    )
}

/** How departures are grouped into tabs. */
enum class TransitKind(val label: String, val icon: ImageVector) {
    Train("Train", Icons.Filled.Train),
    Metro("Metro", Icons.Filled.Subway),
    Tram("Tram", Icons.Filled.Tram),
    Bus("Bus", Icons.Filled.DirectionsBus),
    Ferry("Ferry", Icons.Filled.DirectionsBoat),
    Other("Other", Icons.Filled.Schedule),
}

fun transitKind(mode: String): TransitKind = when (mode) {
    "BUS", "COACH" -> TransitKind.Bus
    "TRAM", "CABLE_CAR" -> TransitKind.Tram
    "SUBWAY", "METRO" -> TransitKind.Metro
    "FERRY" -> TransitKind.Ferry
    "RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_RAIL",
    "REGIONAL_FAST_RAIL", "SUBURBAN" -> TransitKind.Train
    else -> TransitKind.Other
}

/** Live departures for a stop or station, shown on its place card, with a tab per kind of transport. */
@Composable
fun DepartureBoard(
    departures: List<Departure>?,
    loading: Boolean,
    onOpenTrip: (Departure) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = departures.orEmpty().map { transitKind(it.mode) }.distinct().sorted()
    var chosen by rememberSaveable { mutableStateOf<TransitKind?>(null) }
    val active = chosen?.takeIf { it in kinds } ?: kinds.firstOrNull()
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Departures", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        when {
            departures == null -> if (!loading) {
                Text(
                    "Couldn't load departures.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            departures.isEmpty() -> Text(
                "No departures in the next while.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> {
                if (kinds.size > 1) {
                    SecondaryScrollableTabRow(
                        selectedTabIndex = kinds.indexOf(active).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        kinds.forEach { kind ->
                            LeadingIconTab(
                                selected = kind == active,
                                onClick = { chosen = kind },
                                text = { Text(kind.label) },
                                icon = { Icon(kind.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 240.dp).padding(top = 4.dp)) {
                    items(departures.filter { transitKind(it.mode) == active }) { d -> DepartureRow(d, onClick = { onOpenTrip(d) }) }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(d: Departure, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .then(if (d.tripId != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(88.dp)) { LineBadge(d.mode, d.line, d.color, d.textColor) }
        Column(Modifier.weight(1f)) {
            Text(
                d.headsign, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (d.cancelled) TextDecoration.LineThrough else null,
            )
            d.track?.let {
                Text("Platform $it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(untilText(d.time), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (d.delayMinutes > 0 && !d.cancelled) {
                    Text(
                        clock(d.scheduled),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                DelayText(d.delayMinutes, d.realTime, d.cancelled)
            }
        }
    }
}
