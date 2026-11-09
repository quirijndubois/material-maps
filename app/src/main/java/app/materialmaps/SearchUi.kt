package app.materialmaps

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Signpost
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun PlaceRow(
    place: Place,
    from: Location?,
    onClick: () -> Unit,
    recent: Boolean = false,
    /** Shown after the distance, such as a remove button. */
    action: (@Composable () -> Unit)? = null,
) {
    val distance = from?.let {
        val result = FloatArray(1)
        Location.distanceBetween(it.latitude, it.longitude, place.lat, place.lon, result)
        formatDistance(result[0].toDouble())
    }
    ListItem(
        headlineContent = { Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = place.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Icon(if (recent) Icons.Outlined.History else placeIcon(place), contentDescription = null)
        },
        trailingContent = if (distance == null && action == null) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    distance?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    action?.invoke()
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun placeIcon(place: Place): ImageVector = when (place.key) {
    "amenity" -> when (place.type) {
        "restaurant", "fast_food", "food_court" -> Icons.Outlined.Restaurant
        "cafe" -> Icons.Outlined.LocalCafe
        "bar", "pub", "biergarten" -> Icons.Outlined.LocalBar
        "fuel", "charging_station" -> Icons.Outlined.LocalGasStation
        "parking", "bicycle_parking" -> Icons.Outlined.LocalParking
        "hospital", "clinic", "doctors" -> Icons.Outlined.LocalHospital
        "pharmacy" -> Icons.Outlined.LocalPharmacy
        "school", "university", "college", "kindergarten" -> Icons.Outlined.School
        "bank", "atm" -> Icons.Outlined.AccountBalance
        else -> Icons.Outlined.Place
    }
    "shop" -> if (place.type in setOf("supermarket", "convenience", "greengrocer")) {
        Icons.Outlined.LocalGroceryStore
    } else {
        Icons.Outlined.Storefront
    }
    "railway", "public_transport" -> Icons.Outlined.Train
    "highway" -> if (place.type == "bus_stop") Icons.Outlined.DirectionsBus else Icons.Outlined.Signpost
    "tourism" -> when (place.type) {
        "hotel", "hostel", "motel", "guest_house" -> Icons.Outlined.Hotel
        else -> Icons.Outlined.Museum
    }
    "leisure" -> Icons.Outlined.Park
    "place", "boundary" -> Icons.Outlined.LocationCity
    "building" -> Icons.Outlined.Home
    else -> categoryIcon(place.type)
}

/** For results that only carry a category, such as Transitous's "supermarket" or "museum". */
private fun categoryIcon(category: String?): ImageVector = when (category) {
    "restaurant", "fast_food", "food_court" -> Icons.Outlined.Restaurant
    "cafe" -> Icons.Outlined.LocalCafe
    "bar", "pub", "biergarten" -> Icons.Outlined.LocalBar
    "fuel", "charging_station" -> Icons.Outlined.LocalGasStation
    "parking", "bicycle_parking" -> Icons.Outlined.LocalParking
    "hospital", "clinic", "doctors" -> Icons.Outlined.LocalHospital
    "pharmacy", "chemist" -> Icons.Outlined.LocalPharmacy
    "school", "university", "college", "kindergarten" -> Icons.Outlined.School
    "bank", "atm" -> Icons.Outlined.AccountBalance
    "supermarket", "convenience", "greengrocer" -> Icons.Outlined.LocalGroceryStore
    "hotel", "hostel", "motel", "guest_house" -> Icons.Outlined.Hotel
    "museum", "attraction", "artwork", "gallery" -> Icons.Outlined.Museum
    "park", "garden", "playground" -> Icons.Outlined.Park
    "place", "state", "city", "town", "village", "suburb" -> Icons.Outlined.LocationCity
    "station", "railway" -> Icons.Outlined.Train
    "bus_stop", "bus_station" -> Icons.Outlined.DirectionsBus
    null -> Icons.Outlined.Place
    else -> if (category.startsWith("shop")) Icons.Outlined.Storefront else Icons.Outlined.Place
}

/** A search suggestion for every place of a kind nearby, above the places found by name. */
@Composable
fun KindRow(kind: PlaceKind, loading: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(kind.label) },
        supportingContent = { Text("Show all nearby") },
        leadingContent = {
            Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.onPrimaryContainer)
                    else Icon(kind.icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
