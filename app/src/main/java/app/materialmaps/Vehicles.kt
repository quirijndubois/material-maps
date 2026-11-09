@file:OptIn(ExperimentalMaterial3Api::class)

package app.materialmaps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.Style

/** What the user drives around in while navigating. Tap it on the map to pick another. */
enum class Vehicle(val label: String) {
    Arrow("Arrow"),
    Taxi("Yellow cab"),
    BeanMini("Mr Bean's Mini"),
    Delorean("DeLorean"),
    Herbie("Herbie"),
    Falcon("Millennium Falcon"),
    Routemaster("London bus"),
    MysteryVan("Mystery Machine"),
    Saucer("Flying saucer"),
    RubberDuck("Rubber duck");

    /** The 3D model, or null for the plain arrow, which MapLibre draws itself. */
    val model: Model?
        get() = when (this) {
            Arrow -> null
            Taxi -> VehicleModels.taxi
            BeanMini -> VehicleModels.beanMini
            Delorean -> VehicleModels.delorean
            Herbie -> VehicleModels.herbie
            Falcon -> VehicleModels.falcon
            Routemaster -> VehicleModels.routemaster
            MysteryVan -> VehicleModels.mysteryVan
            Saucer -> VehicleModels.saucer
            RubberDuck -> VehicleModels.rubberDuck
        }
}

/** The images MapLibre draws the location puck from in its GPS mode, and their stale versions. */
private val PUCK_IMAGES = listOf(
    "mapbox-location-icon", "mapbox-location-stale-icon",
    "mapbox-location-bearing-icon", "mapbox-location-bearing-stale-icon",
)

/**
 * Hides MapLibre's navigation arrow when a 3D [vehicle] is drawn in its place (see
 * [VehicleOverlay]). MapLibre only takes drawables from resources, so its images are swapped
 * after it has added them; for the arrow they're left alone.
 */
fun Style.useVehicle(vehicle: Vehicle) {
    if (vehicle.model == null) return
    val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    PUCK_IMAGES.forEach { addImage(it, empty) }
}

/** [Vehicle]s to pick from, slowly turning. */
@Composable
fun VehicleSheet(selected: Vehicle, onSelect: (Vehicle) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Your vehicle",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            VehicleGrid(selected, onSelect)
        }
    }
}

@Composable
fun VehicleGrid(selected: Vehicle, onSelect: (Vehicle) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spin = rememberInfiniteTransition(label = "vehicles")
    val yaw by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "yaw")
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Vehicle.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { vehicle ->
                    val on = vehicle == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .selectable(selected = on, role = Role.RadioButton) { onSelect(vehicle) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (on) colors.primaryContainer else colors.surfaceContainerHighest,
                            border = if (on) BorderStroke(2.dp, colors.primary) else null,
                            modifier = Modifier.size(92.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val model = vehicle.model
                                if (model == null) {
                                    ArrowPreview()
                                } else {
                                    androidx.compose.foundation.Canvas(Modifier.size(92.dp)) {
                                        drawIntoCanvas {
                                            model.draw(it.nativeCanvas, size.width / 2, size.height * 0.62f, size.width / 6.2f, yaw, 55f)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            vehicle.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) colors.primary else colors.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ArrowPreview() {
    val context = LocalContext.current
    val image = remember { arrowBitmap(context, 56).asImageBitmap() }
    Image(image, contentDescription = null, modifier = Modifier.size(56.dp))
}

private fun arrowBitmap(context: Context, sizeDp: Int): Bitmap {
    val drawable = context.getDrawable(R.drawable.location_arrow)!!
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, px, px)
    drawable.draw(Canvas(bitmap))
    return bitmap
}
