package app.materialmaps

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.android.geometry.LatLng

/** Orders search results for the list under the map. */
object ResultRanking {
    /**
     * Close by and well known first. Distance counts on the scale of the results themselves (a
     * few hundred meters for cafés in town, kilometers for hospitals), so a famous place a bit
     * further away can beat an unknown one next door, but not one across town. The search's
     * own order, how well each matched what was typed, breaks ties.
     */
    fun recommended(places: List<Place>, from: LatLng?): List<Place> {
        if (from == null) return places.withIndex().sortedByDescending { it.value.prominence - it.index * 1e-4 }.map { it.value }
        val km = places.map { distanceKm(from.latitude, from.longitude, it.lat, it.lon) }
        val scale = km.sorted()[km.size / 2].coerceAtLeast(0.3)
        return places.indices.sortedByDescending { i ->
            val near = 1.0 / (1.0 + km[i] / scale)
            val match = 1.0 - i.toDouble() / places.size
            0.6 * near + 0.3 * places[i].prominence + 0.1 * match
        }.map { places[it] }
    }

    fun nearest(places: List<Place>, from: LatLng?): List<Place> =
        if (from == null) places else places.sortedBy { distanceKm(from.latitude, from.longitude, it.lat, it.lon) }
}

/**
 * Everything a search put on the map, as a sheet that comes up from the bottom: drag it (or
 * just scroll the list) between a peek at the top of the list, half the screen, and nearly all
 * of it; let go and it eases to the nearest, or further on with a flick. Recommended first
 * (near and well known), or simply nearest first.
 */
@Composable
fun ResultsPanel(
    title: String,
    places: List<Place>,
    here: Location?,
    from: LatLng?,
    onSelect: (Place) -> Unit,
    onClose: () -> Unit,
    /** The most the sheet may cover, from the bottom of the screen. */
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var nearestFirst by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(places, from, nearestFirst) {
        if (nearestFirst) ResultRanking.nearest(places, from) else ResultRanking.recommended(places, from)
    }
    val density = LocalDensity.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Heights it rests at: the header and sort buttons, about half the screen, nearly all of it.
    val sheet = rememberSheetHeights()
    with(density) {
        val full = maxHeight.toPx()
        sheet.rest((PEEK_HEIGHT + bottomInset).toPx(), full * 0.55f, full)
    }

    val list = rememberLazyListState()
    // New results, or the other order: back to the top, at half height.
    LaunchedEffect(places) { sheet.glideTo(1) }
    LaunchedEffect(sorted) { list.scrollToItem(0) }

    Surface(
        modifier = modifier.height(with(density) { sheet.height.toDp() }),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(bottom = bottomInset)) {
            // The handle and header: drag here to move the sheet, tap the handle to switch heights.
            Column(Modifier.dragsSheet(sheet)) {
                SheetHandle(sheet)
                Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            placeCount(places.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !nearestFirst,
                        onClick = { nearestFirst = false },
                        label = { Text("Recommended") },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = nearestFirst,
                        onClick = { nearestFirst = true },
                        label = { Text("Nearest") },
                        leadingIcon = { Icon(Icons.Filled.NearMe, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).nestedScroll(sheet.nestedScroll),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(sorted) { place ->
                    PlaceRow(place, here, onClick = { onSelect(place) })
                }
            }
        }
    }
}

/** The sheet's lowest resting height, above the navigation bar: its handle, header and sort buttons. */
private val PEEK_HEIGHT = 132.dp

/**
 * Lets a bottom sheet reach past the [horizontal] padding of the column it's in and down by
 * [bottom], to the edges of the screen, while the column still lays out around its visible part.
 */
fun Modifier.bleed(horizontal: Dp, bottom: Dp): Modifier = layout { measurable, constraints ->
    val h = horizontal.roundToPx()
    val b = bottom.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + 2 * h, maxWidth = constraints.maxWidth + 2 * h))
    layout(placeable.width - 2 * h, (placeable.height - b).coerceAtLeast(0)) { placeable.place(-h, 0) }
}
