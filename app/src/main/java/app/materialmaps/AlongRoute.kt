@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

/** A place near the route: [ahead] meters further along it, and [off] meters to the side. */
data class AlongHit(val place: Place, val ahead: Double, val off: Double)

/** Finding places on the way: fuel, coffee, toilets along the rest of the route. */
object AlongRoute {
    /** How far to look ahead, and how far off the route places may be, by how one travels. */
    fun reach(mode: TravelMode): Pair<Double, Int> = when (mode) {
        TravelMode.Car -> 250_000.0 to 600
        TravelMode.Bike -> 60_000.0 to 300
        else -> 15_000.0 to 200
    }

    /** The route ahead from [traveled], as a point every few hundred meters, for a corridor search. */
    fun path(route: Route, traveled: Double, length: Double): List<LatLng> {
        val ahead = route.remainingFrom(traveled)
        if (ahead.size < 2) return ahead
        val step = (length / 400).coerceAtLeast(300.0)
        val out = mutableListOf(ahead.first())
        var since = 0.0
        var covered = 0.0
        for (i in 1 until ahead.size) {
            val d = metersBetween(ahead[i - 1], ahead[i])
            since += d
            covered += d
            if (since >= step) {
                out += ahead[i]
                since = 0.0
            }
            if (covered >= length) break
        }
        if (out.last() != ahead.last() && covered < length) out += ahead.last()
        return out
    }

    fun bounds(path: List<LatLng>): LatLngBounds? =
        if (path.size < 2) null else LatLngBounds.Builder().includes(path).build()

    /** [places] that are near the route and still ahead, closest along the way first. */
    fun rank(route: Route, traveled: Double, places: List<Place>, radius: Int): List<AlongHit> =
        places.mapNotNull { place ->
            val p = route.progress(place.lat, place.lon)
            val ahead = p.traveled - traveled
            if (p.offRoute > radius * 1.3 || ahead < -30) null else AlongHit(place, ahead.coerceAtLeast(0.0), p.offRoute)
        }
            .distinctBy { Triple(it.place.name, (it.place.lat * 3000).toInt(), (it.place.lon * 3000).toInt()) }
            .sortedBy { it.ahead }
            .take(150)
}

/** The kinds of places one most often stops for on the way, by how one travels. */
private fun commonStops(mode: TravelMode): List<Category> = when (mode) {
    TravelMode.Car -> listOf(Category.Fuel, Category.Charging, Category.Cafes, Category.FastFood, Category.Restaurants, Category.Toilets, Category.Groceries, Category.Parking)
    else -> listOf(Category.Cafes, Category.Groceries, Category.Bakeries, Category.Toilets, Category.FastFood, Category.Restaurants, Category.Pharmacy)
}

/**
 * Search along the route: pick a kind of place, or type one, and see those on the way, on the
 * map and in a list with how far ahead and how far off the route they are. Tapping one in the
 * list adds it as a stop; tapping its pin on the map points it out here ([focus]).
 */
@Composable
fun AlongRoutePanel(
    mode: TravelMode,
    kind: PlaceKind?,
    hits: List<AlongHit>,
    loading: Boolean,
    failed: Boolean,
    focus: Place?,
    onSearch: (PlaceKind) -> Unit,
    onPick: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val typed = remember(text) { PlaceKinds.match(text) }
    val list = rememberLazyListState()
    LaunchedEffect(focus) {
        val i = hits.indexOfFirst { it.place == focus }
        if (i >= 0) list.animateScrollToItem(i)
    }
    LaunchedEffect(kind) { list.scrollToItem(0) }
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Along the route", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            kind == null -> "What are you looking for?"
                            loading && hits.isEmpty() -> "Looking for ${kind.label.lowercase()}…"
                            else -> "${kind.label} · ${hits.size} on the way"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                commonStops(mode).forEach { category ->
                    FilterChip(
                        selected = kind?.value == category.value,
                        onClick = { onSearch(category.kind) },
                        label = { Text(category.label) },
                        leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Something else, like \"bakery\"") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { typed.firstOrNull()?.first?.let(onSearch) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (text.isNotBlank() && typed.isNotEmpty() && typed.none { it.first.value == kind?.value }) {
                typed.forEach { (match, _) ->
                    ListItem(
                        headlineContent = { Text(match.label) },
                        supportingContent = { Text("Search along the route") },
                        leadingContent = { Icon(match.icon, contentDescription = null, tint = colors.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSearch(match) },
                    )
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
            when {
                kind == null || (hits.isEmpty() && loading) -> Unit
                hits.isEmpty() -> Text(
                    if (failed) "Couldn't search right now. Check your connection." else "No ${kind.label.lowercase()} along the route",
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                else -> LazyColumn(state = list, modifier = Modifier.heightIn(max = listHeight(300.dp))) {
                    items(hits) { hit ->
                        val focused = hit.place == focus
                        ListItem(
                            headlineContent = { Text(hit.place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                val off = if (hit.off < 60) "on the route" else "${formatDistance(hit.off)} off the route"
                                Text("${formatDistance(hit.ahead)} ahead · $off")
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = if (focused) colors.tertiary else colors.tertiaryContainer,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            kind.icon,
                                            contentDescription = null,
                                            tint = if (focused) colors.onTertiary else colors.onTertiaryContainer,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { onPick(hit.place) }) {
                                    Icon(Icons.Outlined.AddLocationAlt, contentDescription = "Add as a stop", tint = colors.primary)
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (focused) colors.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                            ),
                            modifier = Modifier.clickable { onPick(hit.place) },
                        )
                    }
                }
            }
        }
    }
}
