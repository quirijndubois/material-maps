@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Map types and what to show on the map, from the layers button. */
@Composable
fun LayersSheet(settings: Settings, onChange: (Settings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Map type",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val types = MapStyle.entries.map { style ->
                Tile(style.label, style.icon, on = !settings.satellite && settings.mapStyle == style) {
                    onChange(settings.copy(mapStyle = style, satellite = false))
                }
            } + Tile("Satellite", Icons.Outlined.SatelliteAlt, on = settings.satellite) {
                onChange(settings.copy(satellite = true))
            }
            TileGrid(types, select = true)

            Text(
                "Map details",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            )
            val details = listOf(
                Tile("Labels", Icons.Outlined.TextFields, settings.labels) { onChange(settings.copy(labels = it)) },
                Tile("3D buildings", Icons.Outlined.Apartment, settings.buildings3d) { onChange(settings.copy(buildings3d = it)) },
                Tile("Saved places", Icons.Outlined.Bookmarks, settings.showSaved) { onChange(settings.copy(showSaved = it)) },
            ) + Overlay.entries.map { overlay ->
                Tile(overlay.label, overlay.icon, overlay in settings.overlays) { on ->
                    onChange(settings.copy(overlays = if (on) settings.overlays + overlay else settings.overlays - overlay))
                }
            }
            TileGrid(details, select = false)
        }
    }
}

private class Tile(val label: String, val icon: ImageVector, val on: Boolean, val onChange: (Boolean) -> Unit)

/** Four to a row, like a phone's quick settings. */
@Composable
private fun TileGrid(tiles: List<Tile>, select: Boolean) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { TileButton(it, select, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileButton(tile: Tile, select: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val interaction = if (select) {
        Modifier.selectable(selected = tile.on, role = Role.RadioButton) { tile.onChange(true) }
    } else {
        Modifier.toggleable(value = tile.on, role = Role.Switch) { tile.onChange(it) }
    }
    Column(
        modifier.then(interaction).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (tile.on) colors.primaryContainer else colors.surfaceContainerHighest,
            contentColor = if (tile.on) colors.onPrimaryContainer else colors.onSurfaceVariant,
            border = if (tile.on) BorderStroke(2.dp, colors.primary) else null,
            modifier = Modifier.size(width = 64.dp, height = 56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tile.icon, contentDescription = null, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tile.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (tile.on) colors.primary else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private val MapStyle.icon: ImageVector
    get() = when (this) {
        MapStyle.Standard -> Icons.Outlined.Map
        MapStyle.Bright -> Icons.Outlined.WbSunny
        MapStyle.Minimal -> Icons.Outlined.Tonality
    }

private val Overlay.icon: ImageVector
    get() = when (this) {
        Overlay.Terrain -> Icons.Outlined.Terrain
        Overlay.Hiking -> Icons.Outlined.Hiking
        Overlay.Cycling -> Icons.AutoMirrored.Outlined.DirectionsBike
        Overlay.Trains -> Icons.Outlined.Train
        Overlay.Metro -> Icons.Outlined.Subway
        Overlay.Trams -> Icons.Outlined.Tram
        Overlay.Slopes -> Icons.Outlined.DownhillSkiing
        Overlay.SeaMarks -> Icons.Outlined.Sailing
    }
