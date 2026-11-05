package app.materialmaps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** A trip to show, with what's already known about it so the header shows while it loads. */
data class TripRef(
    val tripId: String,
    val mode: String,
    val line: String?,
    val headsign: String?,
    val color: String?,
    val textColor: String?,
    /** Where the user would get on, highlighted in the list. */
    val boardingStopId: String?,
)

fun Departure.tripRef() = tripId?.let { TripRef(it, mode, line, headsign, color, textColor, stopId) }

fun TransitLeg.tripRef() = tripId?.let { TripRef(it, mode, line, headsign, color, textColor, fromStopId) }

private val STOP_ROW_HEIGHT = 52.dp

/** Every stop of a bus, train or tram run, with times and where the vehicle should be now. */
@Composable
fun TripPanel(
    ref: TripRef,
    trip: Trip?,
    now: Long,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                LineBadge(ref.mode, ref.line, ref.color, ref.textColor)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (trip?.headsign ?: ref.headsign)?.let { "to $it" }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    trip?.let {
                        Text(
                            listOfNotNull(it.agency, if (it.realTime) "live times" else "timetable").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            when {
                trip == null && error != null -> Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(error, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("Try again") }
                }
                trip == null -> Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Loading stops…", style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    if (trip.cancelled) {
                        Text(
                            "This trip is cancelled",
                            color = colors.error,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    TripStops(trip, ref.boardingStopId, trip.position(now))
                }
            }
        }
    }
}

@Composable
private fun TripStops(trip: Trip, boardingStopId: String?, position: TripPosition) {
    val colors = MaterialTheme.colorScheme
    val lineColor = trip.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: colors.primary
    // The last stop the vehicle reached; -1 before it sets off, everything once it's done.
    val reached = when (position) {
        TripPosition.NotStarted -> -1
        is TripPosition.AtStop -> position.index
        is TripPosition.Between -> position.after
        TripPosition.Finished -> trip.stops.lastIndex
    }
    val boarding = trip.stops.indexOfFirst { it.stopId == boardingStopId }
    val state = rememberLazyListState()
    // Open on where the vehicle is now, or else on the user's stop.
    LaunchedEffect(trip.stops.size) {
        val focus = if (reached in 0 until trip.stops.lastIndex) reached else boarding
        if (focus > 0) state.scrollToItem(focus - 1)
    }
    LazyColumn(Modifier.heightIn(max = listHeight(380.dp)), state = state) {
        itemsIndexed(trip.stops) { i, stop ->
            val vehicleHere = when (position) {
                is TripPosition.AtStop -> position.index == i
                is TripPosition.Between -> position.after == i
                TripPosition.NotStarted -> i == 0
                TripPosition.Finished -> false
            }
            // How far below this stop's dot the vehicle is, as a share of a row.
            val vehicleOffset = (position as? TripPosition.Between)?.fraction?.toFloat() ?: 0f
            val passed = i <= reached && !(position is TripPosition.AtStop && i == reached)
            val faded = colors.onSurfaceVariant.copy(alpha = 0.6f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(STOP_ROW_HEIGHT)
                    .padding(horizontal = 16.dp)
                    .then(if (vehicleHere) Modifier.zIndex(1f) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        clock(stop.time),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            stop.cancelled -> colors.error
                            stop.delayMinutes > 0 -> colors.error
                            passed -> faded
                            else -> colors.onSurface
                        },
                        textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null,
                    )
                    if (stop.delayMinutes != 0 && !stop.cancelled) {
                        Text(
                            clock(stop.scheduled),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
                Box(
                    Modifier
                        .width(36.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val passedLine = lineColor.copy(alpha = 0.35f)
                    Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                        val x = size.width / 2
                        val mid = size.height / 2
                        val stroke = 4.dp.toPx()
                        if (i > 0) drawLine(if (i <= reached) passedLine else lineColor, Offset(x, 0f), Offset(x, mid), stroke)
                        if (i < trip.stops.lastIndex) {
                            drawLine(if (i < reached) passedLine else lineColor, Offset(x, mid), Offset(x, size.height), stroke)
                        }
                        val big = i == 0 || i == trip.stops.lastIndex || i == boarding
                        val radius = (if (big) 7 else 5).dp.toPx()
                        drawCircle(if (passed) passedLine else lineColor, radius, Offset(x, mid))
                        drawCircle(Color.White, radius - 2.dp.toPx(), Offset(x, mid))
                    }
                    if (vehicleHere) {
                        Surface(
                            color = lineColor,
                            contentColor = Color.White,
                            shape = CircleShape,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .offset(y = STOP_ROW_HEIGHT / 2 - 12.dp + STOP_ROW_HEIGHT * vehicleOffset)
                                .size(24.dp),
                        ) {
                            Icon(
                                transitModeIcon(trip.mode),
                                contentDescription = "Now here",
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stop.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (i == boarding) FontWeight.Bold else null,
                        color = if (passed) faded else colors.onSurface,
                        textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val note = listOfNotNull(
                        "Your stop".takeIf { i == boarding },
                        stop.track?.let { "Platform $it" },
                        "Cancelled".takeIf { stop.cancelled },
                    ).joinToString(" · ")
                    if (note.isNotEmpty()) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stop.cancelled) colors.error else colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
