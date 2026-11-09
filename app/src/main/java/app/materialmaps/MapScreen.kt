package app.materialmaps

import android.Manifest
import androidx.compose.foundation.layout.asPaddingValues
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.engine.LocationEngineDefault
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import java.util.Locale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.CircularProgressIndicator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong
import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.SmallFloatingActionButton
import androidx.core.animation.doOnEnd
import java.util.concurrent.atomic.AtomicReference

// Free OpenStreetMap vector tiles, no API key needed (https://openfreemap.org). Dark mode
// recolors the detailed light style (see DarkStyle); their own dark style is only a fallback.
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

private const val PIN_SOURCE = "selected-place"
private const val PIN_LAYER = "selected-place-pin"
private const val MEASURE_LINE_SOURCE = "measure-line"
private const val MEASURE_LINE_LAYER = "measure-line-line"
private const val MEASURE_POINTS_SOURCE = "measure-points"
private const val MEASURE_POINTS_LAYER = "measure-points-dot"
private const val ROUTE_SOURCE = "route"
/** How often the arrow gets its next stretch of road while navigating. */
private const val ARROW_UPDATE_MILLIS = 200L
private const val ROUTE_CASING_LAYER = "route-casing"
private const val ROUTE_LAYER = "route-line"
/** While navigating: the bit of route from the arrow to the next point of the line. */
private const val ROUTE_HEAD_SOURCE = "route-head"
private const val ROUTE_HEAD_CASING_LAYER = "route-head-casing"
private const val ROUTE_HEAD_LAYER = "route-head-line"
private const val ALT_ROUTE_SOURCE = "route-alternatives"
private const val ALT_ROUTE_LAYER = "route-alternatives-line"
// Walks are drawn as dots by layers of their own, shown instead of the solid ones: changing a
// layer's dashes on the fly only shows on tiles drawn after, so half the line kept the old look.
private const val ALT_ROUTE_DOTS_LAYER = "route-alternatives-dots"
private const val ROUTE_DOTS_LAYER = "route-dots"
private const val ROUTE_HEAD_DOTS_LAYER = "route-head-dots"
private const val SAVED_SOURCE = "saved-places"
private const val SAVED_LAYER = "saved-places-dot"
private const val RESULTS_SOURCE = "search-results"
private const val RESULTS_LAYER = "search-results-dot"
private const val RESULTS_LABEL_LAYER = "search-results-label"
private const val ALONG_SOURCE = "along-route"
private const val ALONG_LAYER = "along-route-dot"
private const val ALONG_LABEL_LAYER = "along-route-label"
private const val TRANSIT_WALK_SOURCE = "transit-walks"
private const val TRANSIT_WALK_LAYER = "transit-walks-line"
private const val TRANSIT_RIDE_SOURCE = "transit-rides"
private const val TRANSIT_RIDE_CASING_LAYER = "transit-rides-casing"
private const val TRANSIT_RIDE_LAYER = "transit-rides-line"
private const val TRIP_LINE_SOURCE = "trip-line"
private const val TRIP_LINE_CASING_LAYER = "trip-line-casing"
private const val TRIP_LINE_LAYER = "trip-line-line"
private const val TRIP_STOPS_SOURCE = "trip-stops"
private const val TRIP_STOPS_LAYER = "trip-stops-dot"
private const val TRIP_VEHICLE_SOURCE = "trip-vehicle"
private const val TRIP_VEHICLE_LAYER = "trip-vehicle-dot"

/** How the camera follows the user: not at all, north up (or along the route), or turning with the phone. */
private enum class Follow { Off, Following, Compass }

/** From this screen width on (landscape phones, tablets), panels go in a column on the left. */
private const val WIDE_DP = 600
private val SIDE_PANEL_WIDTH = 400.dp

/** How close the location button zooms in, unless already closer. */
private const val FOLLOW_ZOOM = 16.0

private enum class Panel { None, Place, List, Results, Route, Along, Navigation, Trip, Measure, PickOnMap }

/** What the place picker is choosing a place for. */
private enum class PickFor { Stop, Home, Work, Origin, Destination }

/** Marks a [Place] that stands for where the user was, as a destination after swapping. */
private const val MY_LOCATION_KEY = "my-location"

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun MapScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    darkMap: Boolean,
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
    /** Shrunk to a picture-in-picture window: just the map and the next turn. */
    pip: Boolean = false,
    /** Navigation started or ended, so leaving the app can keep it going in a small window. */
    onNavigatingChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary.toArgb()
    val bottomInsetPx = WindowInsets.navigationBars.getBottom(density)
    val topInsetPx = WindowInsets.statusBars.getTop(density)
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= WIDE_DP
    fun dp(value: Int) = with(density) { value.dp.roundToPx() }

    val mapView = rememberMapViewWithLifecycle()
    val headingEngine = remember { RouteHeadingEngine(LocationEngineDefault.getDefaultLocationEngine(context)) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var bearing by remember { mutableFloatStateOf(0f) }
    var tilt by remember { mutableFloatStateOf(0f) }
    // Bottom edge of whatever sits at the top of the screen, so the compass can go below it.
    var topChromePx by remember { mutableIntStateOf(0) }

    var selected by remember { mutableStateOf<Place?>(null) }
    var lastShownPlace by remember { mutableStateOf<Place?>(null) }
    var follow by remember { mutableStateOf(Follow.Off) }
    // Counts camera flights to the user, so one that's been overtaken by another action
    // (a second tap, picking a place) can't change the follow mode when it ends.
    val flights = remember { AtomicInteger() }
    val turnAnimator = remember { AtomicReference<ValueAnimator?>(null) }
    var wantsTracking by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var askedForPermission by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<Place>()) }
    var resultsFor by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(RecentPlaces.load(context)) }
    var lists by remember { mutableStateOf(SavedPlaces.load(context)) }
    var openListId by remember { mutableStateOf<String?>(null) }
    var lastListId by remember { mutableStateOf<String?>(null) }
    var savingPlace by remember { mutableStateOf<Place?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLayers by rememberSaveable { mutableStateOf(false) }
    var showVehicles by rememberSaveable { mutableStateOf(false) }
    var shortcuts by remember { mutableStateOf(Shortcuts.load(context)) }
    var picking by remember { mutableStateOf<PickFor?>(null) }
    var categoryLoading by remember { mutableStateOf<Category?>(null) }
    var kindLoading by remember { mutableStateOf<PlaceKind?>(null) }
    // Kinds of places the search text could mean ("supermarket"), and the search text that's
    // just the name of the kind being shown, which shouldn't then be searched as text.
    var kindMatches by remember { mutableStateOf(emptyList<Pair<PlaceKind, Double>>()) }
    var kindQuery by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<PlaceDetails?>(null) }
    // Measuring distance: the points placed so far, or null when not measuring, and the map
    // point under the crosshair, where the next one would go.
    var measure by remember { mutableStateOf<List<LatLng>?>(null) }
    var aim by remember { mutableStateOf<LatLng?>(null) }
    var lastMeasure by remember { mutableStateOf(emptyList<LatLng>()) }
    LaunchedEffect(measure) { measure?.let { lastMeasure = it } }
    // Choosing a place for directions on the map: what it's for, and the address under the
    // crosshair, looked up once the map stops moving.
    var pickingOnMap by remember { mutableStateOf<PickFor?>(null) }
    var lastPickingOnMap by remember { mutableStateOf(PickFor.Origin) }
    var aimedPlace by remember { mutableStateOf<Place?>(null) }
    LaunchedEffect(pickingOnMap) { pickingOnMap?.let { lastPickingOnMap = it } }
    LaunchedEffect(pickingOnMap, aim) {
        val at = aim?.takeIf { pickingOnMap != null } ?: return@LaunchedEffect
        aimedPlace = null
        delay(400)
        aimedPlace = runCatching { Geocoder.reverse(at.latitude, at.longitude) }.getOrNull()?.copy(lat = at.latitude, lon = at.longitude)
    }
    var wiki by remember { mutableStateOf<WikiSummary?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var savedStopDepartures by remember { mutableStateOf(emptyMap<Place, List<Departure>?>()) }
    val offline = remember { OfflineMaps(context) }
    LaunchedEffect(Unit) { offline.refresh() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(SavedPlaces.export(lists).toByteArray()) }
        }.isSuccess
        Toast.makeText(context, if (ok) "Saved places exported" else "Couldn't write that file", Toast.LENGTH_SHORT).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
            SavedPlaces.import(context, text)
        }.onSuccess { (updated, added) ->
            lists = updated
            Toast.makeText(context, if (added == 1) "Added 1 place" else "Added $added places", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
        }
    }
    val savedOnMap by remember {
        derivedStateOf { if (settings.showSaved) lists.flatMap { it.places }.distinctBy { Triple(it.name, it.lat, it.lon) } else emptyList() }
    }

    var directionsTo by remember { mutableStateOf<Place?>(null) }
    // Where directions start; null for the user's location.
    var directionsFrom by remember { mutableStateOf<Place?>(null) }
    // Places to pass on the way, in order.
    var stops by remember { mutableStateOf(emptyList<Place>()) }
    var lastDestination by remember { mutableStateOf<Place?>(null) }
    var travelMode by rememberSaveable { mutableStateOf(TravelMode.Car) }
    var routes by remember { mutableStateOf(emptyList<Route>()) }
    var routeIndex by remember { mutableIntStateOf(0) }
    var routing by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routeRequest by remember { mutableIntStateOf(0) }
    var navigating by remember { mutableStateOf(false) }
    /**
     * Walking is navigated differently: the map turns with the phone and the dot sits where the
     * GPS puts it, as on foot the route is only a guide and the phone points where one looks.
     * Public transport is navigated the same way, being mostly on foot and the rest on board.
     */
    val walking by remember { derivedStateOf { travelMode == TravelMode.Walk || travelMode == TravelMode.Transit } }
    /** While navigating a public transport journey: it, and it as a route to follow. */
    var navItinerary by remember { mutableStateOf<Itinerary?>(null) }
    var transitRoute by remember { mutableStateOf<Route?>(null) }
    var progress by remember { mutableStateOf<RouteProgress?>(null) }
    val routeAhead = remember { RouteAhead() }
    var lastRerouteAt by remember { mutableLongStateOf(0L) }
    var speedLimits by remember { mutableStateOf<SpeedLimits?>(null) }
    // Search along the route: what's looked for, and what's on the way.
    var showAlong by remember { mutableStateOf(false) }
    var alongKind by remember { mutableStateOf<PlaceKind?>(null) }
    var alongHits by remember { mutableStateOf(emptyList<AlongHit>()) }
    var alongLoading by remember { mutableStateOf(false) }
    var alongFailed by remember { mutableStateOf(false) }
    /** The place on the way whose pin was tapped, pointed out in the list. */
    var alongFocus by remember { mutableStateOf<Place?>(null) }
    /** The latest GPS speed while navigating, in m/s; null when the GPS doesn't say. */
    var liveSpeed by remember { mutableStateOf<Float?>(null) }
    // While navigating: the latest fix, kept coming in the background too (see NavigationService).
    var navLocation by remember { mutableStateOf<Location?>(null) }
    var passedStops by remember { mutableStateOf(emptySet<Place>()) }
    val voice = remember { Voice(context) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var overview by remember { mutableStateOf(false) }
    // Where the route overview button goes back to; null means following the user.
    var beforeOverview by remember { mutableStateOf<CameraPosition?>(null) }
    var itineraries by remember { mutableStateOf(emptyList<Itinerary>()) }
    var itineraryIndex by remember { mutableStateOf<Int?>(null) }
    var transitNextPage by remember { mutableStateOf<String?>(null) }
    var tripTime by remember { mutableStateOf(TripTime()) }

    var poiLayers by remember { mutableStateOf(emptyArray<String>()) }
    var resultsOnMap by remember { mutableStateOf(false) }
    var mapResults by remember { mutableStateOf(emptyList<Place>()) }

    var departures by remember { mutableStateOf<List<Departure>?>(null) }

    // A bus, train or tram run opened from departures or a journey.
    var openTrip by remember { mutableStateOf<TripRef?>(null) }
    var lastTrip by remember { mutableStateOf<TripRef?>(null) }
    var trip by remember { mutableStateOf<Trip?>(null) }
    var tripError by remember { mutableStateOf<String?>(null) }
    var tripRequest by remember { mutableIntStateOf(0) }
    var tripNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var departuresLoading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocationPermission = granted.values.any { it }
        if (hasLocationPermission && directionsTo == null) wantsTracking = true
    }

    fun locationComponent(): LocationComponent? =
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }

    fun myLocation(): Location? = locationComponent()?.lastKnownLocation

    fun stopTracking() {
        flights.incrementAndGet()
        locationComponent()?.cameraMode = CameraMode.NONE
    }

    /**
     * Flies to the user and then follows them in [mode]. Flying first matters: MapLibre drops
     * zoom, tilt and padding changes made while it moves the camera into tracking mode, and
     * fitting a route or trip on screen leaves padding behind for the panel, which would keep
     * the user off center. Here the padding is always reset, to [topPadding] at most.
     */
    fun flyThenTrack(
        mode: Int,
        followAs: Follow,
        zoom: Double? = null,
        tilt: Double? = null,
        bearing: Double? = null,
        topPadding: Double = 0.0,
        leftPadding: Double = 0.0,
    ) {
        val component = locationComponent() ?: return
        val m = map
        val here = component.lastKnownLocation
        val flight = flights.incrementAndGet()
        if (m == null || here == null) {
            // No fix yet: follow right away; the camera moves to the user once there is one.
            component.cameraMode = mode
            follow = followAs
            return
        }
        val current = m.cameraPosition
        val target = CameraPosition.Builder()
            .target(LatLng(here.latitude, here.longitude))
            .zoom(zoom ?: current.zoom)
            .tilt(tilt ?: current.tilt)
            .bearing(bearing ?: current.bearing)
            .padding(leftPadding, topPadding, 0.0, 0.0)
            .build()
        follow = followAs
        m.animateCamera(CameraUpdateFactory.newCameraPosition(target), 700, object : MapLibreMap.CancelableCallback {
            override fun onFinish() {
                if (flight != flights.get()) return
                // Already over the user; MapLibre's own glide would ignore the compass and then snap.
                if (mode == CameraMode.TRACKING_COMPASS) component.setCameraMode(mode, 1, null, null, null, null)
                else component.cameraMode = mode
            }
            override fun onCancel() {
                // Cut short by the user moving the map, not by a newer flight or mode.
                if (flight == flights.get()) follow = Follow.Off
            }
        })
    }

    /** Stops a [turnThenTrack] under way; when the user did that, they're no longer followed. */
    fun stopTurning(byUser: Boolean) {
        val animator = turnAnimator.getAndSet(null) ?: return
        animator.cancel()
        if (byUser) follow = Follow.Off
    }

    /**
     * Turns the camera to [bearing] (and [tilt]) over the user, then follows them in [mode].
     * MapLibre only animates into a tracking mode when it wasn't tracking yet, and its north-up
     * mode only turns on the next GPS fix, so switching between follow modes would otherwise
     * snap, or do nothing at all while standing still. [bearing] is asked again every frame,
     * so turning towards the phone's heading ends where the phone points by then, and following
     * takes over without a jump.
     */
    fun turnThenTrack(mode: Int, followAs: Follow, bearing: () -> Double, tilt: Double? = null) {
        val component = locationComponent() ?: return
        val m = map ?: return
        val flight = flights.incrementAndGet()
        stopTurning(byUser = false)
        // Hold the camera still for the animation; this also reports following as off for now.
        component.cameraMode = CameraMode.NONE
        follow = followAs
        val start = m.cameraPosition
        val from = start.target
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(500)
        animator.interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        animator.addUpdateListener {
            if (flight != flights.get()) {
                animator.cancel()
                return@addUpdateListener
            }
            val t = (it.animatedValue as Float).toDouble()
            val camera = CameraPosition.Builder(start)
            val turn = ((bearing() - start.bearing) % 360 + 540) % 360 - 180
            camera.bearing(start.bearing + turn * t)
            val here = component.lastKnownLocation
            if (here != null && from != null) {
                camera.target(LatLng(
                    from.latitude + (here.latitude - from.latitude) * t,
                    from.longitude + (here.longitude - from.longitude) * t,
                ))
            }
            if (tilt != null) camera.tilt(start.tilt + (tilt - start.tilt) * t)
            m.moveCamera(CameraUpdateFactory.newCameraPosition(camera.build()))
        }
        animator.doOnEnd {
            // With the shortest transition MapLibre takes: by default it glides to the user for
            // 750 ms, ignoring the compass meanwhile, and then snaps to where the phone points.
            // Without one at all, it never hears the glide end and ignores the compass for good.
            if (turnAnimator.compareAndSet(animator, null) && flight == flights.get()) {
                component.setCameraMode(mode, 1, null, null, null, null)
            }
        }
        turnAnimator.set(animator)
        animator.start()
    }

    /** The route being navigated: the chosen one, or the public transport journey. */
    fun navRoute(): Route? = transitRoute ?: routes.getOrNull(routeIndex)

    /** How the camera follows the user while navigating: along the route, or on foot with the phone. */
    fun routeCameraMode() = if (walking) CameraMode.TRACKING_COMPASS else CameraMode.TRACKING_GPS

    /** Centers on the user and follows them: at street zoom, or turning with the route while navigating. */
    fun followMe() {
        val component = locationComponent()
        if (component == null) {
            wantsTracking = true
            return
        }
        overview = false
        if (navigating && walking) {
            // On foot: turning with the phone, a little tilted, the user a bit below the middle.
            val heading = component.compassEngine?.lastHeading?.toDouble()
            flyThenTrack(
                CameraMode.TRACKING_COMPASS, Follow.Compass, zoom = 18.0, tilt = 30.0, bearing = heading,
                topPadding = mapView.height * 0.25,
                leftPadding = if (wide) with(density) { (SIDE_PANEL_WIDTH + 32.dp).toPx().toDouble() } else 0.0,
            )
        } else if (navigating) {
            // The driving view: heading up, tilted, with the user low on screen to see the road ahead.
            val here = component.lastKnownLocation
            val bearing = here?.takeIf { it.hasBearing() }?.bearing?.toDouble()
            flyThenTrack(
                CameraMode.TRACKING_GPS, Follow.Following, zoom = 17.0, tilt = 50.0, bearing = bearing,
                topPadding = mapView.height * 0.4,
                leftPadding = if (wide) with(density) { (SIDE_PANEL_WIDTH + 32.dp).toPx().toDouble() } else 0.0,
            )
        } else {
            flyThenTrack(CameraMode.TRACKING, Follow.Following, zoom = maxOf(map?.cameraPosition?.zoom ?: 0.0, FOLLOW_ZOOM))
        }
    }

    /**
     * The location button: center on the user at street zoom and follow them; again to turn
     * the map with the phone; again to go back to north up, still following.
     */
    fun onLocateClick() {
        val component = locationComponent()
        if (component == null || navigating || follow == Follow.Off) {
            followMe()
            return
        }
        if (follow == Follow.Following) {
            val fallback = map?.cameraPosition?.bearing ?: 0.0
            turnThenTrack(CameraMode.TRACKING_COMPASS, Follow.Compass, { component.compassEngine?.lastHeading?.toDouble() ?: fallback })
        } else {
            turnThenTrack(CameraMode.TRACKING_GPS_NORTH, Follow.Following, { 0.0 })
        }
    }

    fun select(place: Place, zoom: Double? = 16.0) {
        selected = place
        expanded = false
        keyboard?.hide()
        stopTracking()
        val target = LatLng(place.lat, place.lon)
        val update = if (zoom != null) CameraUpdateFactory.newLatLngZoom(target, zoom)
        else CameraUpdateFactory.newLatLng(target)
        map?.animateCamera(update, 700)
    }

    fun pickResult(place: Place) {
        query = place.name
        recents = RecentPlaces.add(context, place)
        select(place)
    }

    /**
     * Room to leave around things fitted on the map, as left, top, right, bottom pixels: for the
     * panel, which takes [bottomSpace] dp at the bottom, or the side column on wide screens.
     */
    fun framePadding(bottomSpace: Int): IntArray = if (wide) {
        intArrayOf(dp(SIDE_PANEL_WIDTH.value.toInt() + 64), topInsetPx + dp(32), dp(48), bottomInsetPx + dp(48))
    } else {
        intArrayOf(dp(48), topChromePx + dp(32), dp(48), bottomInsetPx + dp(bottomSpace))
    }

    /** Fits [points] on screen beside or above the panel. */
    fun frame(points: List<LatLng>, bottomSpace: Int) {
        val m = map ?: return
        val padding = framePadding(bottomSpace)
        // Flat: fitting a tilted view is only a guess, which leaves a long route partly off screen.
        runCatching {
            m.getCameraForLatLngBounds(LatLngBounds.Builder().includes(points).build(), padding, m.cameraPosition.bearing, 0.0)
        }.getOrNull()?.let { m.animateCamera(CameraUpdateFactory.newCameraPosition(it), 700) }
    }

    /** Puts [places] on the map as dots and fits the nearby ones on screen. */
    fun showOnMap(places: List<Place>, bottomSpace: Int = 120) {
        mapResults = places
        resultsOnMap = true
        selected = null
        expanded = false
        keyboard?.hide()
        stopTracking()
        if (places.isEmpty()) return
        // Frame the nearby ones; far-away ones are still in the list.
        val center = map?.cameraPosition?.target
        val near = center?.let { c -> places.filter { distanceKm(c.latitude, c.longitude, it.lat, it.lon) < 25 } }
        val framed = near?.takeIf { it.isNotEmpty() } ?: places
        if (framed.size == 1) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(framed[0].lat, framed[0].lon), 15.0), 700)
        } else {
            frame(framed.map { LatLng(it.lat, it.lon) }, bottomSpace)
        }
    }

    fun showResultsOnMap() {
        if (results.size == 1) {
            pickResult(results.first())
            return
        }
        if (results.isEmpty()) return
        showOnMap(results, bottomSpace = 380)
    }

    fun openList(list: PlaceList) {
        openListId = list.id
        lastListId = list.id
        showOnMap(list.places, bottomSpace = 360)
    }

    /**
     * Where the measuring crosshair sits: the middle of the map, or of the part beside the
     * side panel on wide screens. Read from the view, as it can change size with rotation.
     */
    fun crosshair(): PointF {
        val width = mapView.width.toFloat()
        val side = if (width / density.density >= WIDE_DP) with(density) { (SIDE_PANEL_WIDTH + 16.dp).toPx() } else 0f
        return PointF(side + (width - side) / 2, mapView.height / 2f)
    }

    fun startMeasuring(from: Place) {
        stopTracking()
        selected = null
        measure = listOf(LatLng(from.lat, from.lon))
        aim = map?.projection?.fromScreenLocation(crosshair())
    }

    fun startPickingOnMap(purpose: PickFor) {
        stopTracking()
        pickingOnMap = purpose
        aim = map?.projection?.fromScreenLocation(crosshair())
    }

    fun pickPlace(purpose: PickFor, place: Place) {
        when (purpose) {
            PickFor.Stop -> stops = stops + place
            PickFor.Home -> shortcuts = Shortcuts.set(context, Shortcut.Home, place)
            PickFor.Work -> shortcuts = Shortcuts.set(context, Shortcut.Work, place)
            PickFor.Origin -> directionsFrom = place
            PickFor.Destination -> {
                directionsTo = place
                lastDestination = place
            }
        }
    }

    fun closeList() {
        openListId = null
        resultsOnMap = false
    }

    /**
     * Places of [kind] in the map data already on the phone, within [bounds]. Instant and
     * offline, but only as complete as the tiles: zoomed out they leave out smaller places.
     */
    fun kindFromMap(kind: PlaceKind, bounds: LatLngBounds): List<Place> {
        val s = style ?: return emptyList()
        val sourceId = poiLayers.firstNotNullOfOrNull { (s.getLayer(it) as? SymbolLayer)?.sourceId } ?: return emptyList()
        val source = s.getSourceAs<VectorSource>(sourceId) ?: return emptyList()
        return source.querySourceFeatures(arrayOf("poi"), null).mapNotNull { feature ->
            val subclass = feature.getStringProperty("subclass") ?: return@mapNotNull null
            if (subclass !in kind.subclasses) return@mapNotNull null
            val point = feature.geometry() as? Point ?: return@mapNotNull null
            if (!bounds.contains(LatLng(point.latitude(), point.longitude()))) return@mapNotNull null
            val name = feature.getStringProperty("name")?.takeIf { it.isNotBlank() }
            if (name == null && kind.needsName) return@mapNotNull null
            Place(
                name = name ?: kind.label.removeSuffix("s"),
                subtitle = "",
                lat = point.latitude(),
                lon = point.longitude(),
                type = subclass,
                key = kind.key,
            )
        }
            // Tiles overlap a little, so the same place can come back more than once.
            .distinctBy { Triple(it.name, (it.lat * 1e4).roundToLong(), (it.lon * 1e4).roundToLong()) }
    }

    /**
     * [request]'s answer if it comes within [millis], else null. A network read can't be cut
     * short, so it's left to finish on its own instead of keeping the user waiting.
     */
    suspend fun <T> briefly(millis: Long, request: suspend () -> T): T? {
        val answer = scope.async(Dispatchers.IO) { runCatching { request() }.getOrNull() }
        return withTimeoutOrNull(millis) { answer.await() }.also { if (it == null) answer.cancel() }
    }

    /**
     * Every [kind] of place nearby, for a search like "supermarket" or a quick button. Zoomed
     * in, that's around what's on screen; zoomed out, around the user (or the middle of the
     * map), looking further when there are only a few. Read from the map's street-level tiles,
     * which have every shop and café; OpenStreetMap's Overpass only for kinds they leave out.
     */
    fun showKind(kind: PlaceKind, chip: Category? = null) {
        val m = map ?: return
        expanded = false
        keyboard?.hide()
        val camera = m.cameraPosition
        val view = m.projection.visibleRegion.latLngBounds
        val here = myLocation()?.let { LatLng(it.latitude, it.longitude) }
        val zoomedIn = camera.zoom >= 14
        val center = if (zoomedIn) view.center else here ?: camera.target ?: return
        // What's on screen, but never just the block one is zoomed in on.
        val radius = if (zoomedIn) (metersBetween(view.northEast, view.southWest) / 2).coerceIn(800.0, 2500.0) else 1200.0
        scope.launch {
            categoryLoading = chip
            kindLoading = kind
            try {
                var found = try {
                    // Look further until there's a fair choice; each step is a few more tiles.
                    var near = PoiTiles.near(kind, center, radius)
                    for (further in listOf(2000.0, 3000.0)) {
                        if (near.size >= 10 || further <= radius) continue
                        near = PoiTiles.near(kind, center, further)
                    }
                    near
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline: whatever the map on screen has.
                    kindFromMap(kind, view)
                }
                if (found.isEmpty()) {
                    val dLat = radius / 111_320.0
                    val dLon = radius / (111_320.0 * kotlin.math.cos(Math.toRadians(center.latitude)))
                    val box = LatLngBounds.from(center.latitude + dLat, center.longitude + dLon, center.latitude - dLat, center.longitude - dLon)
                    found = briefly(12_000) { Overpass.find(kind, box) }.orEmpty()
                }
                if (found.isEmpty()) {
                    Toast.makeText(context, "No ${kind.label.lowercase()} nearby", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val from = here ?: center
                // The search bar shows what was looked for; that isn't a new text search.
                kindQuery = kind.label
                query = kind.label
                results = found.sortedBy { distanceKm(from.latitude, from.longitude, it.lat, it.lon) }
                resultsFor = kind.label
                showOnMap(results, bottomSpace = 380)
            } finally {
                categoryLoading = null
                kindLoading = null
            }
        }
    }

    fun showCategory(category: Category) = showKind(category.kind, chip = category)

    /** Frames where one is and the places found on the way, all along it, so they can be seen on the map. */
    fun showAlongOnMap() {
        val hits = alongHits
        if (hits.isEmpty()) return
        val start = progress?.snapped ?: myLocation()?.let { LatLng(it.latitude, it.longitude) }
            ?: routes.getOrNull(routeIndex)?.points?.firstOrNull()
        stopTracking()
        frame(listOfNotNull(start) + hits.map { LatLng(it.place.lat, it.place.lon) }, bottomSpace = 420)
    }

    fun closeAlong() {
        showAlong = false
        alongFocus = null
        // Back to the driving view, which looking around left.
        if (navigating) followMe()
    }

    /**
     * Places of [kind] along the rest of the route: first what the map on the phone has,
     * instantly, then everything OpenStreetMap has in a corridor along the way.
     */
    fun searchAlong(kind: PlaceKind) {
        val route = routes.getOrNull(routeIndex) ?: return
        val traveled = progress?.traveled ?: 0.0
        val (length, radius) = AlongRoute.reach(travelMode)
        val path = AlongRoute.path(route, traveled, length)
        alongKind = kind
        alongHits = emptyList()
        alongFocus = null
        alongFailed = false
        alongLoading = true
        scope.launch {
            try {
                PoiTiles.along(kind, path, radius.toDouble()) { found ->
                    if (alongKind == kind) alongHits = withContext(Dispatchers.Default) { AlongRoute.rank(route, traveled, found, radius) }
                }
                if (alongHits.isEmpty() && alongKind == kind) {
                    // Kinds the map's tiles leave out: ask OpenStreetMap, but not for long.
                    // A query can only be so long: every few points of the path will do for it.
                    val thinned = path.filterIndexed { i, _ -> i % ((path.size + 119) / 120).coerceAtLeast(1) == 0 }
                    val online = briefly(15_000) { Overpass.alongPath(kind, thinned, radius) }
                    if (online != null && alongKind == kind) alongHits = withContext(Dispatchers.Default) { AlongRoute.rank(route, traveled, online, radius) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (alongKind == kind && alongHits.isEmpty()) alongFailed = true
            } finally {
                if (alongKind == kind) {
                    alongLoading = false
                    showAlongOnMap()
                }
            }
        }
    }

    /** Adds [place] as a stop, in order along the route, which then goes past it. */
    fun stopAlong(place: Place) {
        val route = routes.getOrNull(routeIndex)
        val at = route?.progress(place.lat, place.lon)?.traveled
        val index = if (route == null || at == null) stops.size
        else stops.indexOfFirst { route.progress(it.lat, it.lon).traveled > at }.takeIf { it >= 0 } ?: stops.size
        stops = stops.toMutableList().apply { add(index, place) }
        closeAlong()
        Toast.makeText(context, "Going via ${place.name}", Toast.LENGTH_SHORT).show()
    }

    fun downloadVisibleArea() {
        val m = map ?: return
        val bounds = m.projection.visibleRegion.latLngBounds
        val zoom = m.cameraPosition.zoom
        val center = bounds.center
        scope.launch {
            val near = runCatching { Geocoder.reverse(center.latitude, center.longitude) }.getOrNull()
            val town = near?.subtitle?.split(", ")?.lastOrNull { it.isNotBlank() && it.none(Char::isDigit) }
            offline.download(
                name = town ?: near?.name ?: "Map area",
                styleUrl = settings.mapStyle.url,
                bounds = bounds,
                minZoom = (zoom - 3).coerceIn(0.0, 12.0),
                pixelRatio = context.resources.displayMetrics.density,
            )
        }
    }

    fun startDirections(place: Place) {
        directionsFrom = null
        stops = emptyList()
        passedStops = emptySet()
        directionsTo = place
        lastDestination = place
        routes = emptyList()
        routeIndex = 0
        routeError = null
        routeRequest++
        if (!hasLocationPermission) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    fun onShortcut(shortcut: Shortcut) {
        val place = shortcuts[shortcut]
        if (place == null) picking = if (shortcut == Shortcut.Home) PickFor.Home else PickFor.Work
        else startDirections(place)
    }

    fun stopNavigation() {
        navigating = false
        navItinerary = null
        transitRoute = null
        liveSpeed = null
        overview = false
        progress = null
        voice.stop()
        locationComponent()?.apply {
            cameraMode = CameraMode.NONE
            setNavigationPuck(context, style, accent, navigating = false, settings.vehicle)
        }
        map?.let { m ->
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(m.cameraPosition).tilt(0.0).padding(0.0, 0.0, 0.0, 0.0).build()
                ),
                500,
            )
        }
    }

    /** While navigating: the whole route from here to the destination, north up. Again goes back. */
    fun toggleRouteOverview() {
        val m = map ?: return
        if (overview) {
            val back = beforeOverview
            if (back == null) followMe()
            else {
                overview = false
                m.animateCamera(CameraUpdateFactory.newCameraPosition(back), 700)
            }
            return
        }
        val route = navRoute() ?: return
        beforeOverview = if (follow != Follow.Off) null else m.cameraPosition
        stopTracking()
        overview = true
        val here = myLocation()?.let { LatLng(it.latitude, it.longitude) }
        // What's already behind doesn't need to fit.
        val ahead = progress?.let { route.remainingFrom(it.traveled) } ?: route.points
        val bounds = LatLngBounds.Builder().includes(ahead + listOfNotNull(here)).build()
        val padding = framePadding(200)
        runCatching { m.getCameraForLatLngBounds(bounds, padding, 0.0, 0.0) }.getOrNull()?.let {
            m.animateCamera(CameraUpdateFactory.newCameraPosition(it), 700)
        }
    }

    fun exitDirections() {
        if (navigating) stopNavigation()
        directionsTo = null
        directionsFrom = null
        routes = emptyList()
        itineraries = emptyList()
        itineraryIndex = null
        routeError = null
    }

    fun loadLaterConnections() {
        val target = directionsTo ?: return
        val page = transitNextPage ?: return
        val from = directionsFrom?.let { LatLng(it.lat, it.lon) }
            ?: myLocation()?.let { LatLng(it.latitude, it.longitude) } ?: return
        scope.launch {
            routing = true
            try {
                val more = Transitous.plan(
                    from.latitude, from.longitude, target.lat, target.lon, tripTime.time, tripTime.arriveBy, page,
                    settings.transitAccess,
                )
                val seen = itineraries.map { it.departure to it.arrival }.toSet()
                itineraries = (itineraries + more.itineraries.filterNot { (it.departure to it.arrival) in seen })
                    .sortedBy { if (tripTime.arriveBy) -it.departure else it.arrival }
                transitNextPage = more.nextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't load more connections", Toast.LENGTH_SHORT).show()
            } finally {
                routing = false
            }
        }
    }

    fun showTrip(ref: TripRef) {
        openTrip = ref
        lastTrip = ref
    }

    /** Navigates the chosen route, or public transport [itinerary]. */
    fun startNavigation(itinerary: Itinerary? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Without it the navigation notification is hidden; navigation itself works either way.
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Navigation always goes from where the user is; another starting point was for a look.
        // A journey keeps to its timetable though, so that's followed from wherever it starts.
        if (itinerary == null) directionsFrom = null
        navItinerary = itinerary
        transitRoute = itinerary?.toRoute(directionsTo?.name ?: "your destination")
        navigating = true
        progress = null
        locationComponent()?.setNavigationPuck(context, style, accent, navigating = !walking, settings.vehicle)
        followMe()
    }

    fun pointNorth() {
        val m = map ?: return
        val component = locationComponent()
        if (component != null && component.cameraMode != CameraMode.NONE) {
            // Keep following, but north up from here; flat too, except in the driving view.
            val mode = if (component.cameraMode == CameraMode.TRACKING) CameraMode.TRACKING else CameraMode.TRACKING_GPS_NORTH
            turnThenTrack(mode, Follow.Following, { 0.0 }, tilt = if (navigating) null else 0.0)
            return
        }
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(m.cameraPosition).bearing(0.0).tilt(0.0).build()
            ),
            400,
        )
    }

    // On first launch, ask for location like a maps app would, then jump to the user.
    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            wantsTracking = true
        } else if (!askedForPermission) {
            askedForPermission = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    fun loadStyle(m: MapLibreMap, builder: Style.Builder) {
            m.setStyle(builder) { loaded ->
                // Upright labels throughout: the styles set names of water, nature and areas in italics.
                loaded.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
                    val fonts = (layer.textFont.value as? Array<*>)?.filterIsInstance<String>() ?: return@forEach
                    if (fonts.any { "Italic" in it }) {
                        layer.setProperties(PropertyFactory.textFont(fonts.map { if (it == "Noto Sans Italic") "Noto Sans Regular" else it.replace(" Italic", "") }.toTypedArray()))
                    }
                }
                // Routes go above roads and buildings but below the labels, which these styles
                // keep at the end (some symbol layers, like one-way arrows, come much earlier).
                // The pin goes on top of everything.
                var anchor = loaded.layers.lastOrNull { it !is SymbolLayer }?.id
                fun add(layer: Layer) {
                    anchor.let { if (it != null) loaded.addLayerAbove(layer, it) else loaded.addLayer(layer) }
                    anchor = layer.id
                }
                // Lines widen as the map zooms in, so the route still looks like a road when
                // driving close up instead of a hairline; [border] is added at every zoom.
                fun line(id: String, source: String, color: Int, width: Float, border: Float = 0f) =
                    LineLayer(id, source).withProperties(
                        PropertyFactory.lineColor(color),
                        PropertyFactory.lineWidth(
                            Expression.interpolate(
                                Expression.exponential(1.5f), Expression.zoom(),
                                Expression.stop(10, width * 0.6f + border),
                                Expression.stop(14, width + border),
                                Expression.stop(17, width * 2.5f + border),
                                Expression.stop(20, width * 6f + border),
                            )
                        ),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    )
                loaded.addSource(GeoJsonSource(ALT_ROUTE_SOURCE))
                loaded.addSource(GeoJsonSource(ROUTE_SOURCE))
                loaded.addSource(GeoJsonSource(ROUTE_HEAD_SOURCE))
                loaded.addSource(GeoJsonSource(TRANSIT_WALK_SOURCE))
                loaded.addSource(GeoJsonSource(TRANSIT_RIDE_SOURCE))
                loaded.addSource(GeoJsonSource(TRIP_LINE_SOURCE))
                loaded.addSource(GeoJsonSource(TRIP_STOPS_SOURCE))
                loaded.addSource(GeoJsonSource(TRIP_VEHICLE_SOURCE))
                loaded.addSource(GeoJsonSource(PIN_SOURCE))
                loaded.addSource(GeoJsonSource(MEASURE_LINE_SOURCE))
                loaded.addSource(GeoJsonSource(MEASURE_POINTS_SOURCE))
                loaded.addSource(GeoJsonSource(SAVED_SOURCE))
                loaded.addSource(GeoJsonSource(RESULTS_SOURCE))
                poiLayers = loaded.layers
                    .filter { it is SymbolLayer && it.sourceLayer == "poi" }
                    .map { it.id }
                    .toTypedArray()
                val casing = ColorUtils.blendARGB(accent, android.graphics.Color.BLACK, 0.35f)
                add(line(ALT_ROUTE_LAYER, ALT_ROUTE_SOURCE, colors.outline.toArgb(), 6f))
                add(line(ROUTE_CASING_LAYER, ROUTE_SOURCE, casing, 6f, border = 4f))
                add(line(ROUTE_HEAD_CASING_LAYER, ROUTE_HEAD_SOURCE, casing, 6f, border = 4f))
                add(line(ROUTE_LAYER, ROUTE_SOURCE, accent, 6f))
                add(line(ROUTE_HEAD_LAYER, ROUTE_HEAD_SOURCE, accent, 6f))
                val dots = arrayOf(0f, 2f)
                add(line(ALT_ROUTE_DOTS_LAYER, ALT_ROUTE_SOURCE, colors.outline.toArgb(), 6f).withProperties(PropertyFactory.lineDasharray(dots)))
                add(line(ROUTE_DOTS_LAYER, ROUTE_SOURCE, accent, 6f).withProperties(PropertyFactory.lineDasharray(dots)))
                add(line(ROUTE_HEAD_DOTS_LAYER, ROUTE_HEAD_SOURCE, accent, 6f).withProperties(PropertyFactory.lineDasharray(dots)))
                // With stops, each leg in its own color: the app's for the first, then the others.
                fun byLeg(shade: Float) = Expression.match(
                    Expression.coalesce(Expression.get("leg"), Expression.literal(0)),
                    Expression.color(accent),
                    *(0 until 16).map { i ->
                        Expression.stop(i, Expression.color(ColorUtils.blendARGB(legColor(i, accent), android.graphics.Color.BLACK, shade)))
                    }.toTypedArray(),
                )
                listOf(ROUTE_LAYER, ROUTE_HEAD_LAYER, ROUTE_DOTS_LAYER, ROUTE_HEAD_DOTS_LAYER).forEach { loaded.getLayer(it)?.setProperties(PropertyFactory.lineColor(byLeg(0f))) }
                listOf(ROUTE_CASING_LAYER, ROUTE_HEAD_CASING_LAYER).forEach { loaded.getLayer(it)?.setProperties(PropertyFactory.lineColor(byLeg(0.35f))) }
                // Walks as dots, rides in the line's own color.
                add(
                    line(TRANSIT_WALK_LAYER, TRANSIT_WALK_SOURCE, colors.onSurfaceVariant.toArgb(), 5f)
                        .withProperties(PropertyFactory.lineDasharray(arrayOf(0f, 1.8f)))
                )
                add(line(TRANSIT_RIDE_CASING_LAYER, TRANSIT_RIDE_SOURCE, casing, 6f, border = 3f))
                add(
                    line(TRANSIT_RIDE_LAYER, TRANSIT_RIDE_SOURCE, accent, 6f)
                        .withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))))
                )
                add(line(TRIP_LINE_CASING_LAYER, TRIP_LINE_SOURCE, casing, 6f, border = 3f))
                add(
                    line(TRIP_LINE_LAYER, TRIP_LINE_SOURCE, accent, 6f)
                        .withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))))
                )
                loaded.addLayer(
                    CircleLayer(TRIP_STOPS_LAYER, TRIP_STOPS_SOURCE).withProperties(
                        PropertyFactory.circleRadius(4f),
                        PropertyFactory.circleColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeWidth(2.5f),
                        PropertyFactory.circleStrokeColor(Expression.toColor(Expression.get("color"))),
                    )
                )
                loaded.addLayer(
                    CircleLayer(TRIP_VEHICLE_LAYER, TRIP_VEHICLE_SOURCE).withProperties(
                        PropertyFactory.circleRadius(10f),
                        PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    )
                )
                loaded.addLayer(
                    CircleLayer(SAVED_LAYER, SAVED_SOURCE).withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor(colors.tertiary.toArgb()),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    )
                )
                loaded.addLayer(
                    CircleLayer(RESULTS_LAYER, RESULTS_SOURCE).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(colors.secondary.toArgb()),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    )
                )
                // Names next to the pins, where there's room: the map leaves out those that would
                // overlap, so more show as one zooms in. The best results get theirs first.
                val font = loaded.layers.filterIsInstance<SymbolLayer>()
                    .firstNotNullOfOrNull { (it.textFont.value as? Array<*>)?.filterIsInstance<String>()?.takeIf { f -> f.isNotEmpty() } }
                    ?.toTypedArray() ?: arrayOf("Noto Sans Regular")
                fun labels(id: String, source: String) = SymbolLayer(id, source).withProperties(
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(font),
                    PropertyFactory.textSize(12.5f),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textOffset(arrayOf(0f, 0.8f)),
                    PropertyFactory.textMaxWidth(9f),
                    PropertyFactory.textColor(colors.onSurface.toArgb()),
                    PropertyFactory.textHaloColor(colors.surface.toArgb()),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.symbolSortKey(Expression.get("order")),
                )
                // Places found along the route, in their own color, closest along the way labelled first.
                loaded.addSource(GeoJsonSource(ALONG_SOURCE))
                loaded.addLayer(
                    CircleLayer(ALONG_LAYER, ALONG_SOURCE).withProperties(
                        PropertyFactory.circleRadius(
                            Expression.switchCase(Expression.get("focus"), Expression.literal(10f), Expression.literal(7f))
                        ),
                        PropertyFactory.circleColor(colors.tertiary.toArgb()),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    )
                )
                loaded.addLayer(labels(ALONG_LABEL_LAYER, ALONG_SOURCE))
                loaded.addLayer(
                    SymbolLayer(RESULTS_LABEL_LAYER, RESULTS_SOURCE).withProperties(
                        PropertyFactory.textField(Expression.get("name")),
                        PropertyFactory.textFont(font),
                        PropertyFactory.textSize(12.5f),
                        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                        PropertyFactory.textOffset(arrayOf(0f, 0.8f)),
                        PropertyFactory.textMaxWidth(9f),
                        PropertyFactory.textColor(colors.onSurface.toArgb()),
                        PropertyFactory.textHaloColor(colors.surface.toArgb()),
                        PropertyFactory.textHaloWidth(1.6f),
                        PropertyFactory.symbolSortKey(Expression.get("order")),
                    )
                )
                loaded.addLayer(
                    CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleColor(accent),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    )
                )
                loaded.addLayer(
                    line(MEASURE_LINE_LAYER, MEASURE_LINE_SOURCE, colors.tertiary.toArgb(), 4f)
                        .withProperties(PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)))
                )
                loaded.addLayer(
                    CircleLayer(MEASURE_POINTS_LAYER, MEASURE_POINTS_SOURCE).withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor(colors.tertiary.toArgb()),
                    )
                )
                style = loaded
            }
    }

    // Set up the map once. Styles are loaded below, as the settings ask.
    var styleFellBack by remember { mutableStateOf(false) }
    val currentLoadStyle by rememberUpdatedState(::loadStyle)
    DisposableEffect(mapView) {
        // Fall back to the standard light style if the chosen one can't be loaded.
        val failListener = MapView.OnDidFailLoadingMapListener {
            val m = map
            if (m != null && style == null && !styleFellBack) {
                styleFellBack = true
                currentLoadStyle(m, Style.Builder().fromUri(MapStyle.Standard.url))
            }
        }
        mapView.addOnDidFailLoadingMapListener(failListener)
        mapView.getMapAsync { m ->
            map = m
            // Open where the map was last left, so there's no world view while waiting for a fix.
            context.lastCamera()?.let { m.moveCamera(CameraUpdateFactory.newCameraPosition(it)) }
            m.addOnCameraIdleListener { context.saveCamera(m.cameraPosition) }
            m.uiSettings.apply {
                isLogoEnabled = false
                // We draw our own compass so it can sit below the search bar.
                isCompassEnabled = false
                // Credits (including the map's attribution) live behind our own button.
                isAttributionEnabled = false
            }
            mapView.setOnTouchListener(MapGestures(
                mapView, m,
                onStart = {
                    overview = false
                    stopTurning(byUser = true)
                },
                onStopTracking = { stopTracking() },
                onFreeBearing = {
                    flights.incrementAndGet()
                    locationComponent()?.cameraMode = CameraMode.TRACKING
                    if (follow == Follow.Compass) follow = Follow.Following
                },
            ))
            // Moving the map by hand leaves the route overview; its button then shows it again.
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    overview = false
                    stopTurning(byUser = true)
                }
            }
            m.addOnCameraMoveListener {
                // Following, the camera sits on the arrow as it glides, so the route can be cut
                // right where the arrow is on every frame.
                if (navigating && transitRoute == null && locationComponent()?.cameraMode == routeCameraMode()) {
                    val route = routes.getOrNull(routeIndex)
                    val s = style
                    val target = m.cameraPosition.target
                    if (route != null && s != null && target != null) routeAhead.draw(s, route, target)
                }
                bearing = m.cameraPosition.bearing.toFloat()
                tilt = m.cameraPosition.tilt.toFloat()
                if (measure != null || pickingOnMap != null) aim = m.projection.fromScreenLocation(crosshair())
            }
            m.addOnMapClickListener { point ->
                // A place found along the route: point it out in the list.
                if (showAlong) {
                    val p = m.projection.toScreenLocation(point)
                    val slop = dp(16)
                    m.queryRenderedFeatures(RectF(p.x - slop, p.y - slop, p.x + slop, p.y + slop), ALONG_LAYER).firstOrNull()
                        ?.getNumberProperty("index")?.toInt()?.let { alongHits.getOrNull(it) }?.let {
                            alongFocus = it.place
                            return@addOnMapClickListener true
                        }
                }
                // Tapping yourself while navigating picks what you drive.
                val here = locationComponent()?.lastKnownLocation
                if (navigating && !walking && here != null) {
                    val at = m.projection.toScreenLocation(LatLng(here.latitude, here.longitude))
                    val tap = m.projection.toScreenLocation(point)
                    if (hypot(at.x - tap.x, at.y - tap.y) < dp(40)) {
                        showVehicles = true
                        return@addOnMapClickListener true
                    }
                }
                // While measuring, a tap adds a point there.
                measure?.let {
                    measure = it + point
                    return@addOnMapClickListener true
                }
                // While choosing a place on the map, a tap brings that spot under the crosshair.
                if (pickingOnMap != null) {
                    val target = m.cameraPosition.target ?: return@addOnMapClickListener true
                    val p = m.projection.toScreenLocation(point)
                    val c = crosshair()
                    val t = m.projection.toScreenLocation(target)
                    val moved = m.projection.fromScreenLocation(PointF(t.x + p.x - c.x, t.y + p.y - c.y))
                    m.animateCamera(CameraUpdateFactory.newLatLng(moved), 300)
                    return@addOnMapClickListener true
                }
                val p = m.projection.toScreenLocation(point)
                val slop = dp(16)
                val area = RectF(p.x - slop, p.y - slop, p.x + slop, p.y + slop)
                fun hit(vararg layers: String) =
                    if (layers.isEmpty()) null else m.queryRenderedFeatures(area, *layers).firstOrNull()
                if (directionsTo == null) {
                    // Search results first, then saved places, then the map's own POIs (shops,
                    // sights, stations, stops).
                    hit(RESULTS_LAYER)?.getNumberProperty("index")?.toInt()?.let { mapResults.getOrNull(it) }?.let {
                        select(it, zoom = null)
                        return@addOnMapClickListener true
                    }
                    hit(SAVED_LAYER)?.getNumberProperty("index")?.toInt()?.let { savedOnMap.getOrNull(it) }?.let {
                        select(it, zoom = null)
                        return@addOnMapClickListener true
                    }
                    hit(*poiLayers)?.let { feature ->
                        val poi = poiPlace(feature, point) ?: return@let null
                        select(poi, zoom = null)
                        if (poi.key != "public_transport") {
                            // The map knows the name and kind; look up the address.
                            scope.launch {
                                val address = runCatching { Geocoder.reverse(poi.lat, poi.lon) }.getOrNull()
                                if (address != null && selected === poi) {
                                    selected = poi.copy(subtitle = listOf(address.name, address.subtitle).filter { it.isNotBlank() }.joinToString(", "))
                                }
                            }
                        }
                        true
                    }?.let { return@addOnMapClickListener true }
                }
                if (directionsTo != null) {
                    // Tapping an alternative route picks it.
                    val route = hit(ALT_ROUTE_LAYER, ALT_ROUTE_DOTS_LAYER)
                    route?.getNumberProperty("index")?.toInt()?.let { routeIndex = it }
                    route != null
                } else {
                    selected = null
                    false
                }
            }
            m.addOnMapLongClickListener { point ->
                if (directionsTo != null || measure != null || pickingOnMap != null) return@addOnMapLongClickListener false
                val dropped = Place(
                    name = "Dropped pin",
                    subtitle = Photon.formatCoordinates(point.latitude, point.longitude),
                    lat = point.latitude,
                    lon = point.longitude,
                )
                select(dropped, zoom = null)
                scope.launch {
                    val address = runCatching { Geocoder.reverse(dropped.lat, dropped.lon) }.getOrNull()
                    if (address != null && selected === dropped) {
                        selected = address.copy(lat = dropped.lat, lon = dropped.lon)
                    }
                }
                true
            }
        }
        onDispose { mapView.removeOnDidFailLoadingMapListener(failListener) }
    }

    // Load the chosen map style, recolored when dark, and again whenever that changes.
    LaunchedEffect(map, settings.mapStyle, darkMap) {
        val m = map ?: return@LaunchedEffect
        style = null
        styleFellBack = false
        val url = settings.mapStyle.url
        if (darkMap) {
            val palette = DarkStyle.Palette(colors.surface.toArgb(), colors.tertiary.toArgb())
            val cached = DarkStyle.cached(context, url, palette)
            if (cached != null) loadStyle(m, Style.Builder().fromJson(cached))
            val fresh = DarkStyle.load(context, url, palette)
            if (cached == null) {
                loadStyle(m, if (fresh != null) Style.Builder().fromJson(fresh) else Style.Builder().fromUri(DARK_STYLE))
            }
        } else {
            loadStyle(m, Style.Builder().fromUri(url))
        }
    }

    LaunchedEffect(navigating) { onNavigatingChange(navigating) }
    // The driving view keeps the user low on screen by a padding in pixels, which in the small
    // window (and back out of it) would be all wrong: set it up again for the new size.
    LaunchedEffect(pip) {
        if (!navigating || follow == Follow.Off) return@LaunchedEffect
        delay(300)
        followMe()
    }

    // A newly picked vehicle shows right away.
    LaunchedEffect(settings.vehicle, walking) {
        if (navigating) locationComponent()?.setNavigationPuck(context, style, accent, navigating = !walking, settings.vehicle)
    }

    // Satellite, labels, 3D buildings and overlays, as picked from the layers button.
    LaunchedEffect(style, settings.satellite, settings.labels, settings.buildings3d, settings.overlays) {
        val s = style ?: return@LaunchedEffect
        s.applyLayers(settings, dark = darkMap, below = ALT_ROUTE_LAYER)
    }


    // The measured path: placed points, plus a last stretch to the crosshair.
    LaunchedEffect(measure, aim, style) {
        val s = style ?: return@LaunchedEffect
        val line = s.getSourceAs<GeoJsonSource>(MEASURE_LINE_SOURCE) ?: return@LaunchedEffect
        val dots = s.getSourceAs<GeoJsonSource>(MEASURE_POINTS_SOURCE) ?: return@LaunchedEffect
        val points = measure.orEmpty()
        val path = (points + listOfNotNull(aim.takeIf { measure != null })).map { Point.fromLngLat(it.longitude, it.latitude) }
        line.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(path.takeIf { it.size >= 2 }?.let { Feature.fromGeometry(LineString.fromLngLats(it)) }))
        )
        dots.setGeoJson(FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }))
    }

    // Saved places as dots, unless turned off in the settings.
    LaunchedEffect(style, savedOnMap) {
        val source = style?.getSourceAs<GeoJsonSource>(SAVED_SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                savedOnMap.mapIndexed { i, place ->
                    Feature.fromGeometry(Point.fromLngLat(place.lon, place.lat)).apply { addNumberProperty("index", i) }
                }
            )
        )
    }

    // Show the blue dot and follow the user when asked.
    LaunchedEffect(style, hasLocationPermission, wantsTracking) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (!hasLocationPermission) return@LaunchedEffect
        m.enableLocation(context, s, headingEngine, accent, gps = navigating && !walking, vehicle = settings.vehicle) { following -> if (!following) follow = Follow.Off }
        if (wantsTracking) {
            wantsTracking = false
            // Tracking only zooms once there is a fix, so wait for the first one (usually the
            // last known location, available almost at once) and jump straight to the user.
            // Launched separately because clearing wantsTracking restarts this effect.
            scope.launch {
                var here = myLocation()
                var waited = 0
                while (here == null && waited < 60) {
                    delay(250)
                    waited++
                    here = myLocation()
                }
                if (here != null && !navigating) {
                    m.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(here.latitude, here.longitude),
                            maxOf(m.cameraPosition.zoom, FOLLOW_ZOOM),
                        )
                    )
                }
                followMe()
            }
        }
    }

    // Move the pin to the selected place.
    LaunchedEffect(selected, style) {
        val source = style?.getSourceAs<GeoJsonSource>(PIN_SOURCE) ?: return@LaunchedEffect
        val place = selected
        if (place == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        } else {
            source.setGeoJson(Point.fromLngLat(place.lon, place.lat))
            lastShownPlace = place
        }
    }

    // Search while typing. Requests aren't cancelled when the next key comes in (Photon
    // often takes a second to answer); the newest answer to arrive wins instead. Each search
    // shows its quick first answer, then the full one.
    LaunchedEffect(Unit) { PlaceKinds.load(context) }
    LaunchedEffect(Unit) {
        var sent = 0L
        var shown = 0L
        snapshotFlow { query.trim() }
            .distinctUntilChanged()
            .debounce(200)
            .collect { q ->
                // Showing a kind of place put its name in the bar; that's no new search.
                if (q == kindQuery) return@collect
                kindMatches = if (q.isEmpty()) emptyList() else withContext(Dispatchers.Default) { PlaceKinds.match(q) }
                val id = ++sent
                val center = map?.cameraPosition
                val lat = center?.target?.latitude
                val lon = center?.target?.longitude
                val cached = if (q.isEmpty()) emptyList() else Geocoder.cached(q, lat, lon)
                if (cached != null) {
                    shown = id
                    results = cached
                    resultsFor = q
                    searching = false
                    searchFailed = false
                    return@collect
                }
                searching = true
                launch {
                    val outcome = try {
                        Result.success(
                            Geocoder.search(q, lat, lon, center?.zoom, early = { first ->
                                if (id >= shown && query.trim() != kindQuery) {
                                    shown = id
                                    results = first
                                    resultsFor = q
                                    searchFailed = false
                                }
                            })
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    if (id == sent) searching = false
                    if (id < shown || query.trim() == kindQuery) return@launch
                    outcome.onSuccess {
                        shown = id
                        results = it
                        resultsFor = q
                        searchFailed = false
                    }.onFailure {
                        if (id == sent) searchFailed = true
                    }
                }
            }
    }

    // Fetch routes from the user's location to the destination.
    // Only the settings that change the route itself fetch it again.
    val routeOptions = listOf(settings.avoidHighways, settings.avoidTolls, settings.avoidFerries, settings.units)
    LaunchedEffect(directionsTo, directionsFrom, travelMode, routeRequest, hasLocationPermission, tripTime, routeOptions, stops, settings.transitAccess) {
        val target = directionsTo ?: return@LaunchedEffect
        // The journey being taken stays as it is.
        if (navItinerary != null) return@LaunchedEffect
        val origin = directionsFrom
        if (origin == null && !hasLocationPermission) {
            routeError = "Allow location access to get directions from where you are."
            return@LaunchedEffect
        }
        routing = true
        routeError = null
        if (!navigating) {
            routes = emptyList()
            itineraries = emptyList()
        }
        try {
            var from = origin?.toLocation() ?: myLocation()
            var waited = 0
            while (from == null && waited < 20) {
                delay(500)
                waited++
                from = myLocation()
            }
            if (from == null) {
                routeError = "Couldn't find your location yet."
                return@LaunchedEffect
            }
            if (travelMode == TravelMode.Transit) {
                val plan = Transitous.plan(
                    from.latitude, from.longitude, target.lat, target.lon, tripTime.time, tripTime.arriveBy,
                    access = settings.transitAccess,
                )
                itineraryIndex = null
                itineraries = plan.itineraries
                transitNextPage = plan.nextPage
            } else {
                // Stops that are already behind (reached while navigating) aren't routed to again.
                val ahead = stops.filterNot { it in passedStops }
                val found = Valhalla.route(
                    LatLng(from.latitude, from.longitude),
                    ahead.map { LatLng(it.lat, it.lon) },
                    LatLng(target.lat, target.lon),
                    travelMode,
                    settings,
                )
                routeIndex = 0
                routes = found
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RouteException) {
            routeError = e.message
        } catch (e: Exception) {
            routeError = "Couldn't reach the routing service. Check your connection."
        } finally {
            routing = false
        }
    }

    // Draw the routes and, unless navigating, fit them on screen. While navigating, the route
    // being driven is drawn as the user goes, without the part behind them (see below).
    LaunchedEffect(routes, routeIndex, style, navigating) {
        val s = style ?: return@LaunchedEffect
        val main = s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return@LaunchedEffect
        val alternatives = s.getSourceAs<GeoJsonSource>(ALT_ROUTE_SOURCE) ?: return@LaunchedEffect
        val route = routes.getOrNull(routeIndex)
        if (!navigating) {
            routeAhead.clear(s)
            main.setGeoJson(FeatureCollection.fromFeatures(route?.legFeatures().orEmpty()))
        }
        // First on screen; the alternatives can take a moment to work out on a long route.
        if (route != null && route.points.size >= 2 && !navigating) {
            stopTracking()
            frame(route.points, 300)
        }
        // Alternatives only where they go another way: under the chosen route they'd show
        // through its gaps, and between its dots on foot.
        val others = withContext(Dispatchers.Default) {
            routes.mapIndexedNotNull { i, r ->
                if (i == routeIndex) return@mapIndexedNotNull null
                val stretches = if (route != null) r.apartFrom(route) else listOf(r.points)
                Feature.fromGeometry(
                    MultiLineString.fromLngLats(stretches.map { line -> line.map { Point.fromLngLat(it.longitude, it.latitude) } })
                ).apply { addNumberProperty("index", i) }
            }
        }
        alternatives.setGeoJson(FeatureCollection.fromFeatures(others))
    }

    // A walk is drawn as dots, like the walks between rides; other routes as a solid line.
    LaunchedEffect(style, walking) {
        val s = style ?: return@LaunchedEffect
        fun show(visible: Boolean, vararg layers: String) = layers.forEach {
            s.getLayer(it)?.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
        }
        show(!walking, ALT_ROUTE_LAYER, ROUTE_CASING_LAYER, ROUTE_HEAD_CASING_LAYER, ROUTE_LAYER, ROUTE_HEAD_LAYER)
        show(walking, ALT_ROUTE_DOTS_LAYER, ROUTE_DOTS_LAYER, ROUTE_HEAD_DOTS_LAYER)
    }

    // Draw the chosen public transport journey (the first one while browsing the list).
    LaunchedEffect(itineraries, itineraryIndex, style) {
        val s = style ?: return@LaunchedEffect
        val walks = s.getSourceAs<GeoJsonSource>(TRANSIT_WALK_SOURCE) ?: return@LaunchedEffect
        val rides = s.getSourceAs<GeoJsonSource>(TRANSIT_RIDE_SOURCE) ?: return@LaunchedEffect
        val itinerary = itineraries.getOrNull(itineraryIndex ?: 0)
        val legs = itinerary?.legs.orEmpty().filter { it.points.size >= 2 }
        fun TransitLeg.feature() = Feature.fromGeometry(
            LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
        ).apply { addStringProperty("color", color ?: String.format("#%06X", accent and 0xFFFFFF)) }
        walks.setGeoJson(FeatureCollection.fromFeatures(legs.filter { it.isWalk }.map { it.feature() }))
        rides.setGeoJson(FeatureCollection.fromFeatures(legs.filterNot { it.isWalk }.map { it.feature() }))
        val points = legs.flatMap { it.points }
        if (points.size >= 2) {
            stopTracking()
            frame(points, 360)
        }
    }

    // Search results as pins on the map.
    LaunchedEffect(resultsOnMap, mapResults, style) {
        val source = style?.getSourceAs<GeoJsonSource>(RESULTS_SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                if (!resultsOnMap) emptyList()
                else {
                    // Labels go to the best results first when they'd overlap.
                    val from = myLocation()?.let { LatLng(it.latitude, it.longitude) } ?: map?.cameraPosition?.target
                    val order = ResultRanking.recommended(mapResults, from).withIndex().associate { it.value to it.index }
                    mapResults.mapIndexed { i, place ->
                        Feature.fromGeometry(Point.fromLngLat(place.lon, place.lat)).apply {
                            addNumberProperty("index", i)
                            addNumberProperty("order", order[place] ?: i)
                            addStringProperty("name", place.name)
                        }
                    }
                }
            )
        )
    }

    // Places found along the route, while that search is open.
    LaunchedEffect(showAlong, alongHits, alongFocus, style) {
        val source = style?.getSourceAs<GeoJsonSource>(ALONG_SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                if (!showAlong) emptyList()
                else alongHits.mapIndexed { i, hit ->
                    Feature.fromGeometry(Point.fromLngLat(hit.place.lon, hit.place.lat)).apply {
                        addNumberProperty("index", i)
                        addNumberProperty("order", if (hit.place == alongFocus) -1 else i)
                        addStringProperty("name", hit.place.name)
                        addBooleanProperty("focus", hit.place == alongFocus)
                    }
                }
            )
        )
    }

    // The open trip's stops and times, refreshed for live delays while it's shown, and right
    // away when the app comes back to the front.
    LaunchedEffect(openTrip, tripRequest) {
        trip = null
        tripError = null
        val ref = openTrip ?: return@LaunchedEffect
        var first = true
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val loaded = Transitous.trip(ref.tripId)
                    trip = loaded
                    tripError = null
                    if (first) {
                        first = false
                        val points = loaded.points.ifEmpty { loaded.stops.map { LatLng(it.lat, it.lon) } }
                        if (points.size >= 2) {
                            stopTracking()
                            frame(points, 440)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep showing the last times if a refresh fails.
                    if (trip == null) tripError = "Couldn't load this trip."
                }
                delay(30_000)
            }
        }
    }

    // Move the vehicle along as time passes between refreshes.
    LaunchedEffect(openTrip) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (openTrip != null) {
                tripNow = System.currentTimeMillis()
                delay(10_000)
            }
        }
    }

    // Draw the open trip: its line, its stops and where the vehicle should be now.
    LaunchedEffect(trip, tripNow, style) {
        val s = style ?: return@LaunchedEffect
        val line = s.getSourceAs<GeoJsonSource>(TRIP_LINE_SOURCE) ?: return@LaunchedEffect
        val stops = s.getSourceAs<GeoJsonSource>(TRIP_STOPS_SOURCE) ?: return@LaunchedEffect
        val vehicle = s.getSourceAs<GeoJsonSource>(TRIP_VEHICLE_SOURCE) ?: return@LaunchedEffect
        val t = trip
        val color = t?.color ?: String.format("#%06X", accent and 0xFFFFFF)
        fun Feature.colored() = apply { addStringProperty("color", color) }
        val points = t?.points.orEmpty().ifEmpty { t?.stops.orEmpty().map { LatLng(it.lat, it.lon) } }
        line.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    points.takeIf { it.size >= 2 }?.let { pts ->
                        Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.longitude, it.latitude) })).colored()
                    }
                )
            )
        )
        stops.setGeoJson(
            FeatureCollection.fromFeatures(t?.stops.orEmpty().map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).colored() })
        )
        val here = t?.let { it.locate(it.position(tripNow)) }
        vehicle.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(here?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)).colored() })
            )
        )
    }

    // Opening hours, phone and website for the selected place.
    // Opening hours, phone and website, plus a Wikipedia summary for sights and landmarks.
    LaunchedEffect(selected?.name, selected?.lat, selected?.lon) {
        details = null
        wiki = null
        detailsLoading = false
        val place = selected ?: return@LaunchedEffect
        if (place.key == "building" || place.name == "Dropped pin") return@LaunchedEffect
        detailsLoading = true
        // Nominatim is quick; Overpass has the same data but is often too busy to answer in time.
        val found = runCatching { Nominatim.details(place) }
            .recoverCatching { Overpass.details(place) }
            .getOrNull()
        details = found?.takeIf { it.openingHours != null || it.phone != null || it.website != null }
        // OpenStreetMap's link to the article finds it in any language; without one, look for
        // an article nearby with the place's name.
        wiki = found?.takeIf { it.wikipedia != null || it.wikidata != null }
            ?.let { runCatching { Wikipedia.forTags(it.wikipedia, it.wikidata) }.getOrNull() }
            ?: runCatching { Wikipedia.nearby(place) }.getOrNull()
        detailsLoading = false
    }

    // Next departures from saved stops, while the search panel shows them.
    val savedStops by remember { derivedStateOf { lists.flatMap { it.places }.filter { it.departureRadius() != null }.distinctBy { it.name } } }
    LaunchedEffect(expanded, savedStops) {
        if (!expanded || savedStops.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                savedStops.take(4).forEach { stop ->
                    val radius = stop.departureRadius() ?: return@forEach
                    val found = runCatching { Transitous.departures(stop.lat, stop.lon, radius, count = 6) }.getOrNull()
                    savedStopDepartures = savedStopDepartures + (stop to (found ?: savedStopDepartures[stop] ?: emptyList()))
                }
                delay(30_000)
            }
        }
    }

    // Locations other apps send us: geo: links and shared map links.
    LaunchedEffect(incoming, map) {
        val uri = incoming ?: return@LaunchedEffect
        if (map == null) return@LaunchedEffect
        onIncomingHandled()
        when (val link = MapLink.parse(uri)) {
            is MapLink.At -> {
                val place = Place(
                    name = link.label ?: "Dropped pin",
                    subtitle = Photon.formatCoordinates(link.lat, link.lon),
                    lat = link.lat,
                    lon = link.lon,
                )
                select(place, zoom = link.zoom ?: 16.0)
                if (link.label == null) {
                    val address = runCatching { Geocoder.reverse(place.lat, place.lon) }.getOrNull()
                    if (address != null && selected === place) selected = address.copy(lat = place.lat, lon = place.lon)
                }
            }
            is MapLink.Search -> {
                query = link.query
                expanded = true
            }
            null -> Toast.makeText(context, "Couldn't find a location in that link", Toast.LENGTH_SHORT).show()
        }
    }

    // Live departures for stops and stations, refreshed while the card is open, and right away
    // when the app comes back to the front (polling stops while it's in the background).
    LaunchedEffect(selected) {
        departures = null
        val place = selected ?: return@LaunchedEffect
        val radius = place.departureRadius() ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                departuresLoading = true
                try {
                    departures = Transitous.departures(place.lat, place.lon, radius)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep showing the last list if a refresh fails.
                } finally {
                    departuresLoading = false
                }
                delay(30_000)
            }
        }
    }

    // Follow progress along the route while navigating, and reroute when the user leaves it.
    LaunchedEffect(navigating, routes, routeIndex, transitRoute) {
        if (!navigating) return@LaunchedEffect
        val route = navRoute() ?: return@LaunchedEffect
        val transit = transitRoute != null
        var offRouteTicks = 0
        var speed = 0f
        var nextAnnouncement = 0
        var shownTurn: String? = null
        while (true) {
            val here = navLocation ?: myLocation()
            if (here != null) {
                val p = route.progress(here.latitude, here.longitude)
                progress = p
                // Say what's due by now. Several can fall due at once (after a GPS gap, or a
                // reroute that starts mid-instruction); only the latest still makes sense then.
                var due: Announcement? = null
                while (nextAnnouncement < route.announcements.size && route.announcements[nextAnnouncement].at <= p.traveled) {
                    val a = route.announcements[nextAnnouncement++]
                    if (p.traveled - a.at < 150) due = a
                }
                if (due != null && settings.voiceGuidance) voice.say(due.text)
                stops.firstOrNull { stop ->
                    stop !in passedStops && distanceKm(here.latitude, here.longitude, stop.lat, stop.lon) < 0.05
                }?.let { passedStops = passedStops + it }
                // The next turn in the notification, for when the app isn't in front.
                val turn = if (p.arrived) "You've arrived" else p.next?.instruction ?: "Head to ${directionsTo?.name}"
                val turnTitle = if (p.arrived) directionsTo?.name.orEmpty() else formatDistance(p.distanceToNext)
                if (turn + turnTitle != shownTurn) {
                    shownTurn = turn + turnTitle
                    NavigationService.update(context, turnTitle, turn)
                }
                // Smoothed, so one odd GPS reading doesn't make the map jump.
                if (here.hasSpeed()) speed += (here.speed - speed) * 0.3f
                // The speedometer shows each reading as it is, like a car's.
                liveSpeed = if (here.hasSpeed()) here.speed else null
                // Only show the road ahead. While following, the camera listener does this every
                // frame; otherwise the latest fix will do.
                if (!transit && locationComponent()?.cameraMode != routeCameraMode()) {
                    style?.let { routeAhead.draw(it, route, p.snapped) }
                }
                val component = locationComponent()
                if (follow != Follow.Off && component?.cameraMode == CameraMode.TRACKING_GPS) {
                    val zoom = drivingZoom(speed, p)
                    if (abs(zoom - (map?.cameraPosition?.zoom ?: zoom)) > 0.2) {
                        component.zoomWhileTracking(zoom, 1500)
                    }
                }
                val off = p.offRoute > 50 && here.accuracy < 50 && !p.arrived
                offRouteTicks = if (off) offRouteTicks + 1 else 0
                val now = System.currentTimeMillis()
                // A journey isn't rerouted: its rides go where they go, whatever the user does.
                if (!transit && offRouteTicks >= 3 && !routing && now - lastRerouteAt > 15_000) {
                    lastRerouteAt = now
                    routeRequest++
                }
            }
            delay(1000)
        }
    }

    // Run the foreground service while navigating, and take location updates of our own: the
    // map's location component stops listening when the app goes to the background.
    // The map's arrow is fed from here while navigating: on the route it's moved every frame
    // (see RouteHeadingEngine), off it, or on foot, each fix is passed on.
    DisposableEffect(navigating) {
        if (!navigating) return@DisposableEffect onDispose {}
        NavigationService.start(context, "Navigating", directionsTo?.name)
        val component = locationComponent()
        component?.locationEngine = null
        val callback = object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                val location = result.lastLocation ?: return
                navLocation = location
                if (!headingEngine.following) locationComponent()?.forceLocationUpdate(location)
            }
            override fun onFailure(exception: Exception) {}
        }
        val request = LocationEngineRequest.Builder(1000)
            .setFastestInterval(500)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        @SuppressLint("MissingPermission")
        fun listen() = runCatching { headingEngine.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper()) }
        listen()
        onDispose {
            headingEngine.removeLocationUpdates(callback)
            component?.takeIf { it.isLocationComponentActivated }?.locationEngine = headingEngine
            navLocation = null
            NavigationService.stop(context)
        }
    }

    LaunchedEffect(navigating) {
        if (!navigating) return@LaunchedEffect
        while (true) {
            // Each batch is timed to run out just as the next one comes.
            headingEngine.ahead(ARROW_UPDATE_MILLIS)?.let { locationComponent()?.forceLocationUpdate(it, true) }
            delay(ARROW_UPDATE_MILLIS)
        }
    }

    // Ending navigation from the notification.
    LaunchedEffect(Unit) {
        var seen = NavigationService.endRequests.value
        NavigationService.endRequests.collect {
            if (it != seen) {
                seen = it
                if (navigating) stopNavigation()
            }
        }
    }

    // Speed limits for the route being driven, looked up once it's chosen.
    LaunchedEffect(navigating, routes, routeIndex) {
        speedLimits = null
        val route = routes.getOrNull(routeIndex)
        if (!navigating || travelMode != TravelMode.Car || route == null) return@LaunchedEffect
        speedLimits = runCatching { Valhalla.speedLimits(route) }.getOrNull()
    }

    // GPS once a second while navigating; outside of that, every few seconds at most and
    // without insisting on satellites, which saves battery while just looking at the map.
    LaunchedEffect(style, navigating) {
        val component = locationComponent() ?: return@LaunchedEffect
        component.locationEngineRequest = if (navigating) {
            LocationEngineRequest.Builder(1000)
                .setFastestInterval(500)
                .setDisplacement(3f)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .build()
        } else {
            LocationEngineRequest.Builder(3000)
                .setFastestInterval(1000)
                .setDisplacement(5f)
                .setPriority(LocationEngineRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()
        }
    }

    // While navigating, the arrow and the map point along the route rather than where the phone
    // points. Not on foot: there the dot goes where the GPS says and turns with the phone.
    LaunchedEffect(navigating, routes, routeIndex, walking) {
        headingEngine.route = if (navigating && !walking) routes.getOrNull(routeIndex) else null
    }

    // Keep the screen on while navigating.
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = !expanded && (showAlong || pickingOnMap != null || measure != null || openTrip != null || navigating || directionsTo != null || selected != null || openListId != null || resultsOnMap)) {
        when {
            showAlong -> closeAlong()
            pickingOnMap != null -> pickingOnMap = null
            measure != null -> measure = null
            openTrip != null -> openTrip = null
            navigating -> stopNavigation()
            directionsTo != null -> exitDirections()
            selected != null -> selected = null
            openListId != null -> closeList()
            else -> resultsOnMap = false
        }
    }

    val compassVisible by remember {
        derivedStateOf {
            val b = ((bearing % 360) + 360) % 360
            min(b, 360 - b) > 0.5f || tilt > 0.5f
        }
    }
    val trackTop = Modifier.onGloballyPositioned { if (!expanded) topChromePx = it.boundsInRoot().bottom.toInt() }

    // What's left for lists in panels once the top bar and the panel's own header and buttons
    // are taken off; in the side column the panel only shares the height with the top bar.
    val listMax = (configuration.screenHeightDp.dp - with(density) { topChromePx.toDp() } - if (wide) 220.dp else 330.dp)
        .coerceIn(120.dp, 380.dp)
    CompositionLocalProvider(LocalListMaxHeight provides listMax) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            val vehicleModel = settings.vehicle.model
            if (navigating && !walking && vehicleModel != null) {
                // Placed over the user every frame by the view itself.
                AndroidView(
                    factory = { VehicleOverlay(it) },
                    update = {
                        it.map = map
                        it.model = vehicleModel
                    },
                    modifier = Modifier.size(VehicleOverlay.SIZE_DP.dp),
                )
            }
            if (pip) {
                // The small window while navigating from another app: the next turn and speed.
                val destination = directionsTo
                if (navigating && destination != null) {
                    PipBanner(
                        route = navRoute(),
                        progress = progress,
                        destination = destination,
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(6.dp),
                    )
                    liveSpeed?.let { Speedometer(it, progress?.let { p -> speedLimits?.at(p.segment) }, Modifier.align(Alignment.BottomStart).padding(6.dp)) }
                }
            } else {
                if (measure != null || pickingOnMap != null) {
                    // Aim with the map; the crosshair stays put. Same spot as crosshair() works out.
                    Box(Modifier.fillMaxSize().padding(start = if (wide) SIDE_PANEL_WIDTH + 16.dp else 0.dp)) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = colors.onSurface,
                            modifier = Modifier.align(Alignment.Center).size(36.dp),
                        )
                    }
                }

                var showCredits by rememberSaveable { mutableStateOf(false) }
                CreditsButton(
                    onClick = { showCredits = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 4.dp, bottom = 4.dp),
                )
                if (showCredits) CreditsSheet(onDismiss = { showCredits = false })
                if (showSettings) {
                    SettingsSheet(
                        settings = settings,
                        onChange = onSettingsChange,
                        onClearHistory = if (recents.isNotEmpty()) ({
                            RecentPlaces.clear(context)
                            recents = emptyList()
                        }) else null,
                        shortcuts = shortcuts,
                        onSetShortcut = { picking = if (it == Shortcut.Home) PickFor.Home else PickFor.Work },
                        onClearShortcut = { shortcuts = Shortcuts.set(context, it, null) },
                        offlineAreas = offline.areas,
                        onDownloadArea = {
                            showSettings = false
                            downloadVisibleArea()
                            Toast.makeText(context, "Downloading the map of this area", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteArea = { offline.delete(it.id) },
                        onExport = { exportLauncher.launch("saved-places.geojson") },
                        onImport = { importLauncher.launch(arrayOf("application/geo+json", "application/json", "*/*")) },
                        onDismiss = { showSettings = false },
                    )
                }
                picking?.let { purpose ->
                    PlacePicker(
                        title = pickTitle(purpose),
                        near = myLocation(),
                        suggestions = (shortcuts.values + lists.flatMap { it.places } + recents).distinctBy { it.name to it.lat },
                        onPick = { place ->
                            picking = null
                            pickPlace(purpose, place)
                        },
                        onDismiss = { picking = null },
                        onChooseOnMap = when (purpose) {
                            PickFor.Stop, PickFor.Origin, PickFor.Destination -> ({
                                picking = null
                                startPickingOnMap(purpose)
                            })
                            else -> null
                        },
                        onPickMyLocation = when (purpose) {
                            PickFor.Origin -> ({ picking = null; directionsFrom = null })
                            PickFor.Destination -> myLocation()?.let { here ->
                                {
                                    picking = null
                                    directionsTo = Place("Your location", "", here.latitude, here.longitude, key = MY_LOCATION_KEY)
                                }
                            }
                            else -> null
                        },
                    )
                }
                savingPlace?.let { place ->
                    SaveToListSheet(
                        place = place,
                        lists = lists,
                        onToggle = { lists = SavedPlaces.toggle(context, it.id, place) },
                        onCreate = { lists = SavedPlaces.create(context, it, place) },
                        onDismiss = { savingPlace = null },
                    )
                }

                // The layers button, with the compass under it when the map is turned or tilted.
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = with(density) { topChromePx.toDp() } + 12.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!navigating) {
                        SmallFloatingActionButton(
                            onClick = { showLayers = true },
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.primary,
                        ) {
                            Icon(Icons.Outlined.Layers, contentDescription = "Layers")
                        }
                    }
                    AnimatedVisibility(
                        visible = compassVisible,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Compass(bearing = { bearing }, onClick = ::pointNorth)
                    }
                }
            if (showVehicles) {
                    VehicleSheet(
                        selected = settings.vehicle,
                        onSelect = { onSettingsChange(settings.copy(vehicle = it)) },
                        onDismiss = { showVehicles = false },
                    )
                }
                if (showLayers) {
                    LayersSheet(settings = settings, onChange = onSettingsChange, onDismiss = { showLayers = false })
                }

                val following = follow != Follow.Off
                // The location and navigation buttons, over the map.
                val mapButtons: @Composable () -> Unit = {
                    if (navigating && directionsTo != null) {
                        // Like a car navigation app: see the whole route, then jump back to the driving view.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val limit = progress?.let { p -> speedLimits?.at(p.segment) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    limit?.let { SpeedLimitSign(it) }
                                    liveSpeed?.let { Speedometer(it, limit) }
                                }
                                AnimatedVisibility(
                                    visible = !following,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut(),
                                ) {
                                    ExtendedFloatingActionButton(
                                        onClick = ::followMe,
                                        icon = { Icon(Icons.Filled.Navigation, contentDescription = null) },
                                        text = { Text("Re-center") },
                                        containerColor = colors.surfaceContainerHigh,
                                        contentColor = colors.primary,
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            FloatingActionButton(
                                onClick = ::toggleRouteOverview,
                                containerColor = if (overview) colors.primaryContainer else colors.surfaceContainerHigh,
                            ) {
                                Icon(
                                    Icons.Filled.Route,
                                    contentDescription = "Route overview",
                                    tint = if (overview) colors.onPrimaryContainer else colors.primary,
                                )
                            }
                        }
                    } else FloatingActionButton(
                        onClick = {
                            if (hasLocationPermission) onLocateClick()
                            else permissionLauncher.launch(LOCATION_PERMISSIONS)
                        },
                        containerColor = if (following) colors.primaryContainer else colors.surfaceContainerHigh,
                    ) {
                        Icon(
                            when (follow) {
                                Follow.Off -> Icons.Filled.LocationSearching
                                Follow.Compass -> Icons.Filled.Explore
                                else -> Icons.Filled.MyLocation
                            },
                            contentDescription = "My location",
                            tint = if (following) colors.onPrimaryContainer else colors.primary,
                        )
                    }
                }
                // Place cards, routes, trips and the like.
                val panelContent: @Composable () -> Unit = {
                    val panel = when {
                        pickingOnMap != null -> Panel.PickOnMap
                        showAlong && directionsTo != null && travelMode != TravelMode.Transit -> Panel.Along
                        directionsTo != null && navigating -> Panel.Navigation
                        measure != null -> Panel.Measure
                        openTrip != null -> Panel.Trip
                        directionsTo != null -> Panel.Route
                        selected != null -> Panel.Place
                        openListId != null -> Panel.List
                        resultsOnMap && mapResults.size > 1 -> Panel.Results
                        else -> Panel.None
                    }
                    // Animating the panel's size makes the location button glide up and down with it.
                    AnimatedContent(
                        targetState = panel,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut()) using
                                SizeTransform(clip = false)
                        },
                        contentAlignment = Alignment.BottomCenter,
                        label = "bottomPanel",
                    ) { target ->
                        // Fall back to the last values so panels keep their content while animating out.
                        val destination = directionsTo ?: lastDestination
                        val place = selected ?: lastShownPlace
                        val shownResults = mapResults
                        val panelModifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .animateContentSize()
                        when {
                            target == Panel.PickOnMap -> PickOnMapPanel(
                                title = pickTitle(pickingOnMap ?: lastPickingOnMap),
                                place = aimedPlace,
                                aim = aim,
                                onDone = {
                                    val purpose = pickingOnMap
                                    val at = aim
                                    if (purpose != null && at != null) {
                                        pickingOnMap = null
                                        pickPlace(purpose, aimedPlace ?: droppedPin(at))
                                    }
                                },
                                onClose = { pickingOnMap = null },
                                modifier = panelModifier,
                            )
                            target == Panel.Measure -> MeasurePanel(
                                points = measure ?: lastMeasure,
                                aim = aim,
                                onAdd = { aim?.let { a -> measure = measure.orEmpty() + a } },
                                onUndo = { measure = measure?.dropLast(1)?.takeIf { it.isNotEmpty() } ?: measure },
                                onClose = { measure = null },
                                modifier = panelModifier,
                            )
                            target == Panel.Trip && (openTrip ?: lastTrip) != null -> TripPanel(
                                ref = openTrip ?: lastTrip!!,
                                trip = trip,
                                now = tripNow,
                                error = tripError,
                                onBack = { openTrip = null },
                                onRetry = { tripRequest++ },
                                modifier = panelModifier,
                            )
                            target == Panel.Navigation && destination != null -> NavigationPanel(
                                route = navRoute(),
                                arrival = navItinerary?.arrival,
                                progress = progress,
                                muted = !settings.voiceGuidance,
                                onToggleMute = {
                                    if (settings.voiceGuidance) voice.stop()
                                    onSettingsChange(settings.copy(voiceGuidance = !settings.voiceGuidance))
                                },
                                onEnd = ::stopNavigation,
                                onSearchAlong = if (navItinerary == null) ({ showAlong = true }) else null,
                                modifier = panelModifier,
                            )
                            target == Panel.Route && destination != null && travelMode == TravelMode.Transit -> TransitPanel(
                                itineraries = itineraries,
                                selected = itineraryIndex,
                                onSelect = { itineraryIndex = it },
                                tripTime = tripTime,
                                onTripTimeChange = { tripTime = it },
                                loading = routing,
                                error = routeError,
                                onRetry = {
                                    if (hasLocationPermission) routeRequest++
                                    else permissionLauncher.launch(LOCATION_PERMISSIONS)
                                },
                                onLater = if (transitNextPage != null) ::loadLaterConnections else null,
                                onOpenTrip = { leg -> leg.tripRef()?.let(::showTrip) },
                                access = settings.transitAccess,
                                onAccessChange = { onSettingsChange(settings.copy(transitAccess = it)) },
                                onStart = { startNavigation(it) },
                                modifier = panelModifier,
                            )
                            target == Panel.Along -> AlongRoutePanel(
                                mode = travelMode,
                                kind = alongKind,
                                hits = alongHits,
                                loading = alongLoading,
                                failed = alongFailed,
                                focus = alongFocus,
                                onSearch = ::searchAlong,
                                onPick = ::stopAlong,
                                onClose = ::closeAlong,
                                modifier = panelModifier,
                            )
                            target == Panel.Route && destination != null -> RoutePanel(
                                routes = routes,
                                selectedIndex = routeIndex,
                                onSelect = { routeIndex = it },
                                loading = routing,
                                error = routeError,
                                onRetry = {
                                    if (hasLocationPermission) routeRequest++
                                    else permissionLauncher.launch(LOCATION_PERMISSIONS)
                                },
                                onStart = { startNavigation() },
                                onOpenElsewhere = { context.openDirections(destination) },
                                onSearchAlong = { showAlong = true },
                                modifier = panelModifier,
                            )
                            target == Panel.List && lists.any { it.id == (openListId ?: lastListId) } -> {
                                val list = lists.first { it.id == (openListId ?: lastListId) }
                                PlaceListPanel(
                                    list = list,
                                    here = myLocation(),
                                    onSelect = { select(it) },
                                    onRemove = {
                                        lists = SavedPlaces.remove(context, list.id, it)
                                        mapResults = mapResults.filterNot { p -> p.isSameAs(it) }
                                    },
                                    onRename = { lists = SavedPlaces.rename(context, list.id, it) },
                                    onDelete = {
                                        closeList()
                                        lists = SavedPlaces.delete(context, list.id)
                                    },
                                    onClose = ::closeList,
                                    modifier = panelModifier,
                                )
                            }
                            target == Panel.Results && shownResults.isNotEmpty() -> ResultsPanel(
                                title = resultsFor.ifBlank { "Results" },
                                places = shownResults,
                                here = myLocation(),
                                from = myLocation()?.let { LatLng(it.latitude, it.longitude) } ?: map?.cameraPosition?.target,
                                onSelect = { select(it, zoom = null) },
                                onClose = { resultsOnMap = false },
                                // Up to just under the search bar.
                                maxHeight = configuration.screenHeightDp.dp - with(density) { topChromePx.toDp() } - 8.dp +
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                                // A sheet from the bottom edge on phones; in the side column, a card like the others.
                                modifier = if (wide) Modifier.fillMaxWidth().padding(top = 16.dp)
                                else Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .bleed(horizontal = 16.dp, bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                            )
                            target == Panel.Place && place != null -> PlaceCard(
                                place,
                                saved = lists.containing(place).isNotEmpty(),
                                details = details,
                                wiki = wiki,
                                loading = detailsLoading,
                                onSave = { savingPlace = place },
                                onMeasure = { startMeasuring(place) },
                                onDirections = { startDirections(place) },
                                onClose = { selected = null },
                                modifier = panelModifier,
                                departures = if (place.departureRadius() != null) {
                                    {
                                    DepartureBoard(
                                        departures, departuresLoading,
                                        onOpenTrip = { d -> d.tripRef()?.let(::showTrip) },
                                        modifier = Modifier.padding(top = 16.dp, end = 12.dp),
                                    )
                                }
                                } else null,
                            )
                            else -> Spacer(Modifier.fillMaxWidth())
                        }
                    }
                }
                if (wide) {
                    // Side by side: the panel in a column on the left, the buttons over the map beside it.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = SIDE_PANEL_WIDTH + 32.dp, end = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.End,
                    ) { mapButtons() }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, bottom = 16.dp)
                            .width(SIDE_PANEL_WIDTH),
                    ) { panelContent() }
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        mapButtons()
                        panelContent()
                    }
                }

                val destination = directionsTo
                // Bars at the top span the screen, or the side column on wide screens.
                val topWidth = if (wide) Modifier.width(SIDE_PANEL_WIDTH) else Modifier.fillMaxWidth()
                when {
                    destination != null && navigating -> NavigationBanner(
                        route = navRoute(),
                        progress = progress,
                        destination = destination,
                        rerouting = routing,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .then(topWidth)
                            .then(trackTop),
                    )
                    destination != null -> DirectionsHeader(
                        origin = directionsFrom,
                        destination = destination,
                        onChangeOrigin = { picking = PickFor.Origin },
                        onChangeDestination = { picking = PickFor.Destination },
                        onSwap = {
                            val here = myLocation()
                            // From the user's location, the swap needs to know where that is.
                            if (directionsFrom != null || here != null) {
                                val newTo = directionsFrom ?: Place("Your location", "", here!!.latitude, here.longitude, key = MY_LOCATION_KEY)
                                directionsFrom = destination.takeUnless { it.key == MY_LOCATION_KEY }
                                directionsTo = newTo
                                stops = stops.reversed()
                            }
                        },
                        mode = travelMode,
                        onModeChange = { travelMode = it },
                        onBack = ::exitDirections,
                        stops = stops,
                        onAddStop = { picking = PickFor.Stop },
                        onRemoveStop = { stop -> stops = stops - stop },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .then(topWidth)
                            .then(trackTop),
                    )
                    else -> {
                        val sidePadding by animateDpAsState(if (expanded) 0.dp else 16.dp, label = "searchPadding")
                        // Chips under the bar while just looking at the map; they then mark the top's edge.
                        val showChips = !expanded && selected == null && openListId == null && settings.quickButtons.isNotEmpty()
                        var searchBottomPx by remember { mutableIntStateOf(0) }
                        if (showChips) {
                            QuickChips(
                                buttons = settings.quickButtons.mapNotNull { QuickButton.byId(it) },
                                shortcuts = shortcuts,
                                loading = categoryLoading,
                                onShortcut = ::onShortcut,
                                onCategory = ::showCategory,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = with(density) { searchBottomPx.toDp() } + 8.dp)
                                    .then(if (wide) Modifier.width(SIDE_PANEL_WIDTH + 32.dp) else Modifier)
                                    .then(trackTop),
                            )
                        }
                        SearchBar(
                            modifier = Modifier
                                .align(if (wide && !expanded) Alignment.TopStart else Alignment.TopCenter)
                                .padding(horizontal = sidePadding)
                                .then(if (wide && !expanded) Modifier.width(SIDE_PANEL_WIDTH) else Modifier)
                                .onGloballyPositioned { if (!expanded) searchBottomPx = it.boundsInRoot().bottom.toInt() }
                                .then(if (showChips) Modifier else trackTop),
                            inputField = {
                                SearchBarDefaults.InputField(
                                    query = query,
                                    onQueryChange = {
                                        query = it
                                        kindQuery = null
                                        resultsOnMap = false
                                    },
                                    onSearch = {
                                        // "supermarket": all of them nearby, not places called that.
                                        val kind = kindMatches.firstOrNull()?.takeIf { it.second >= PlaceKinds.SURE }?.first
                                        if (kind != null && query.trim() != kindQuery) showKind(kind) else showResultsOnMap()
                                    },
                                    expanded = expanded,
                                    onExpandedChange = { expanded = it },
                                    placeholder = { Text("Search here") },
                                    leadingIcon = {
                                        if (expanded) {
                                            IconButton(onClick = { expanded = false }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                            }
                                        } else {
                                            Icon(Icons.Filled.Search, contentDescription = null)
                                        }
                                    },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = {
                                                query = ""
                                                resultsOnMap = false
                                                selected = null
                                            }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                                            }
                                        } else {
                                            IconButton(onClick = { showSettings = true }) {
                                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                                            }
                                        }
                                    },
                                )
                            },
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            shadowElevation = if (expanded) 0.dp else 4.dp,
                        ) {
                            val q = query.trim()
                            // Reserve the bar's height so results don't jump when it appears.
                            if (searching && q.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else Spacer(Modifier.height(4.dp))
                            val here = myLocation()
                            when {
                                q.isEmpty() -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                    if (savedStops.isNotEmpty()) {
                                        Text(
                                            "Your stops",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                        )
                                        savedStops.take(4).forEach { stop ->
                                            SavedStopDepartures(stop, savedStopDepartures[stop], onOpenTrip = { d ->
                                                d.tripRef()?.let {
                                                    expanded = false
                                                    showTrip(it)
                                                }
                                            })
                                        }
                                    }
                                    SavedListsSection(lists, onOpen = ::openList)
                                    if (recents.isNotEmpty()) RecentsSection(recents, here, onPick = ::pickResult, onClear = {
                                        RecentPlaces.clear(context)
                                        recents = emptyList()
                                    })
                                }
                                searchFailed && results.isEmpty() && kindMatches.isEmpty() -> SearchHint("Couldn't reach the search service. Check your connection.")
                                results.isEmpty() && resultsFor == q && !searching && kindMatches.isEmpty() -> SearchHint("No results for \"$q\"")
                                else -> LazyColumn {
                                    // Kinds of places first: "Supermarket · nearby" shows them all.
                                    if (q != kindQuery) items(kindMatches.map { it.first }) { kind ->
                                        KindRow(kind, loading = kindLoading == kind, onClick = { showKind(kind) })
                                    }
                                    if (results.size > 1) item {
                                        AssistChip(
                                            onClick = ::showResultsOnMap,
                                            label = { Text("Show all on map") },
                                            leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
                                    items(results) { place ->
                                        PlaceRow(place, here, onClick = { pickResult(place) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentsSection(recents: List<Place>, here: Location?, onPick: (Place) -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Recent",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text("Clear") }
    }
    recents.forEach { place ->
        PlaceRow(place, here, onClick = { onPick(place) }, recent = true)
    }
}

@Composable
private fun SearchHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun PlaceCard(
    place: Place,
    details: PlaceDetails?,
    wiki: WikiSummary?,
    loading: Boolean,
    saved: Boolean,
    onSave: () -> Unit,
    onMeasure: () -> Unit,
    onDirections: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    departures: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        // With a photo, summary and departures it can be taller than a short screen.
        Column(
            Modifier
                .heightIn(max = LocalListMaxHeight.current + 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(top = 12.dp)
                ) {
                    Text(
                        place.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (place.subtitle.isNotBlank()) {
                        Text(
                            place.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Stops already say what runs there ("Bus · Tram"); "Station"/"Stop" adds nothing.
                    place.type?.takeIf { place.key != "public_transport" }?.let { type ->
                        Text(
                            type.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            // Say it's coming rather than have hours and a photo pop in out of nowhere.
            if (loading) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Loading details…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            details?.let { PlaceDetailsRows(it) }
            wiki?.let { WikiCard(it, Modifier.padding(top = 12.dp, end = 12.dp)) }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onDirections) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Directions")
                }
                OutlinedButton(onClick = onSave) {
                    Icon(
                        if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (saved) "Saved" else "Save")
                }
                OutlinedButton(onClick = { context.sharePlace(place) }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                OutlinedButton(onClick = onMeasure) {
                    Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Measure")
                }
                OutlinedIconButton(onClick = { context.openPlaceElsewhere(place) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in another app")
                }
            }
            departures?.invoke()
        }
    }
}

private fun pickTitle(purpose: PickFor) = when (purpose) {
    PickFor.Stop -> "Add a stop"
    PickFor.Home -> "Set home"
    PickFor.Work -> "Set work"
    PickFor.Origin -> "Choose starting point"
    PickFor.Destination -> "Choose destination"
}

/** A place with no name, just where it is on the map. */
private fun droppedPin(at: LatLng) =
    Place("Dropped pin", Photon.formatCoordinates(at.latitude, at.longitude), at.latitude, at.longitude)

/** Choosing a place by moving the map under the crosshair; [place] is its address, once known. */
@Composable
private fun PickOnMapPanel(
    title: String,
    place: Place?,
    aim: LatLng?,
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        place?.name ?: aim?.let { Photon.formatCoordinates(it.latitude, it.longitude) }.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        place?.subtitle?.takeIf { it.isNotBlank() } ?: "Move the map to aim the crosshair, or tap the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
            }
            Button(onClick = onDone, enabled = aim != null, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose this place")
            }
        }
    }
}

/** The measured distance so far, including the stretch to the crosshair. */
@Composable
private fun MeasurePanel(
    points: List<LatLng>,
    aim: LatLng?,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val path = points + listOfNotNull(aim)
    val total = path.zipWithNext { a, b -> a.distanceTo(b) }.sum()
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text("Total distance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDistance(total), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Stop measuring") }
            }
            Text(
                "Move the map to aim the crosshair, or tap the map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add point")
                }
                OutlinedButton(onClick = onUndo, enabled = points.size > 1) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Undo")
                }
            }
        }
    }
}

/** A Wikipedia summary with the article's picture, credited and linked as the license asks. */
@Composable
private fun WikiCard(summary: WikiSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by remember(summary) { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, summary.imageUrl) {
        value = summary.imageUrl?.let { url ->
            runCatching {
                val bytes = httpGetBytes(url)
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Column(modifier) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = summary.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
        Text(
            summary.extract,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable { expanded = !expanded }
                .animateContentSize(),
        )
        Text(
            "From Wikipedia",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { context.openUri(Uri.parse(summary.url)) }
                .padding(vertical = 6.dp),
        )
    }
}

/** Whether the place is open now, and how to reach it. */
@Composable
private fun PlaceDetailsRows(details: PlaceDetails) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 8.dp)) {
        details.openingHours?.let { hours ->
            val state = remember(hours) { OpeningHours.state(hours) }
            val week = remember(hours) { OpeningHours.week(hours) }
            var showWeek by remember(hours) { mutableStateOf(false) }
            Row(
                Modifier
                    .then(if (week != null) Modifier.clickable { showWeek = !showWeek } else Modifier)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state?.text ?: hours,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state?.open) {
                        true -> colors.tertiary
                        false -> colors.error
                        null -> colors.onSurfaceVariant
                    },
                )
                if (week != null) {
                    Icon(
                        if (showWeek) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (showWeek) "Hide opening hours" else "Show opening hours",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (showWeek && week != null) {
                val today = remember { LocalDate.now().dayOfWeek.value - 1 }
                Column(Modifier.padding(bottom = 4.dp)) {
                    week.forEachIndexed { i, times ->
                        val weight = if (i == today) FontWeight.Bold else null
                        Row(Modifier.padding(vertical = 1.dp)) {
                            Text(
                                DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = weight,
                                modifier = Modifier.width(110.dp),
                            )
                            Text(times, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
                        }
                    }
                }
            }
        }
        details.phone?.let { phone ->
            Row(
                Modifier
                    .clickable { context.openUri(Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Call, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(phone, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            }
        }
        details.website?.let { site ->
            val url = if (site.startsWith("http")) site else "https://$site"
            Row(
                Modifier
                    .clickable { context.openUri(Uri.parse(url)) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    Uri.parse(url).host?.removePrefix("www.") ?: site,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Context.openUri(uri: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No app can open this", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.cameraPrefs() = getSharedPreferences("camera", Context.MODE_PRIVATE)

private fun Context.lastCamera(): CameraPosition? {
    val prefs = cameraPrefs()
    if (!prefs.contains("lat")) return null
    return CameraPosition.Builder()
        .target(LatLng(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble()))
        .zoom(prefs.getFloat("zoom", 15f).toDouble())
        .build()
}

private fun Context.saveCamera(position: CameraPosition) {
    val target = position.target ?: return
    cameraPrefs().edit()
        .putFloat("lat", target.latitude.toFloat())
        .putFloat("lon", target.longitude.toFloat())
        .putFloat("zoom", position.zoom.toFloat())
        .apply()
}

private fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.enableLocation(
    context: Context,
    style: Style,
    engine: LocationEngine,
    color: Int,
    gps: Boolean,
    vehicle: Vehicle,
    onTrackingChanged: (Boolean) -> Unit,
) {
    val component = locationComponent
    if (!component.isLocationComponentActivated) {
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .locationComponentOptions(locationOptions(context, color, gps))
                .locationEngine(engine)
                // The native indicator layer just takes a new position and bearing. The default
                // one rebuilds a GeoJSON source on each update, which with live heading kept
                // every tile worker busy and made panning stutter.
                .useSpecializedLocationLayer(true)
                // Ignore GPS jitter under 3 m: each update animates the camera and dot for about
                // a second, so a phone lying still otherwise keeps the map redrawing nonstop.
                .locationEngineRequest(
                    LocationEngineRequest.Builder(1000)
                        .setFastestInterval(500)
                        .setDisplacement(3f)
                        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                        .build()
                )
                .build()
        )
        component.compassEngine = HeadingEngine(context)
        component.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() = onTrackingChanged(false)
            override fun onCameraTrackingChanged(currentMode: Int) =
                onTrackingChanged(currentMode != CameraMode.NONE)
        })
    }
    component.isLocationComponentEnabled = true
    component.setNavigationPuck(context, style, color, gps, vehicle)
}

/** The blue dot with a heading cone normally; a direction arrow, or the chosen [vehicle], while navigating. */
private fun LocationComponent.setNavigationPuck(
    context: Context,
    style: Style?,
    color: Int,
    navigating: Boolean,
    vehicle: Vehicle,
) {
    applyStyle(locationOptions(context, color, navigating))
    renderMode = if (navigating) RenderMode.GPS else RenderMode.COMPASS
    if (navigating) style?.useVehicle(vehicle)
}

private fun locationOptions(context: Context, color: Int, navigating: Boolean): LocationComponentOptions {
    val builder = LocationComponentOptions.builder(context)
        .bearingTintColor(color)
        // Our heading engine already updates smoothly; animating each reading only adds lag.
        .compassAnimationEnabled(false)
        .accuracyColor(color)
        .accuracyAlpha(0.15f)
    return if (navigating) {
        // MapLibre tints the whole arrow, which would hide its outline, so it carries its own
        // colors. Its white ring would sit under the arrow, so that goes.
        builder
            .gpsDrawable(R.drawable.location_arrow)
            .foregroundTintColor(null)
            .backgroundDrawable(R.drawable.location_none)
            .backgroundDrawableStale(R.drawable.location_none)
            .accuracyAlpha(0f)
            .build()
    } else {
        builder.foregroundTintColor(color).build()
    }
}

/** Hands off to a navigation app (Google Maps, Organic Maps, OsmAnd, ...). */
private fun Context.openDirections(place: Place) {
    // The same as opening the place elsewhere: the other app offers its own directions.
    openPlaceElsewhere(place)
}

/** Shows the place in another maps app (Google Maps, Organic Maps, OsmAnd, ...), asking which. */
private fun Context.openPlaceElsewhere(place: Place) {
    val uri = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(place.name)})")
    // The chooser says so itself when no app can open it.
    val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open in")
        // We open geo: links too now; showing this place here again would be pointless.
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, MainActivity::class.java)))
    startActivity(chooser)
}

private fun Context.sharePlace(place: Place) {
    val link = String.format(
        Locale.US,
        "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f",
        place.lat, place.lon, place.lat, place.lon,
    )
    val text = listOf(place.name, place.subtitle, link).filter { it.isNotBlank() }.joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null))
}

/** A tapped POI from the map style. */
private fun poiPlace(feature: Feature, tap: LatLng): Place? {
    val name = feature.getStringProperty("name")?.takeIf { it.isNotBlank() } ?: return null
    val point = feature.geometry() as? Point
    val lat = point?.latitude() ?: tap.latitude
    val lon = point?.longitude() ?: tap.longitude
    val kind = feature.getStringProperty("class")
    val subclass = feature.getStringProperty("subclass")
    return when (kind) {
        "bus" -> stopPlace(name, lat, lon, setOf(TransitKind.Bus))
        "rail", "railway" -> stopPlace(
            name, lat, lon,
            setOf(if (subclass in setOf("subway", "subway_entrance")) TransitKind.Metro else if (subclass == "tram_stop") TransitKind.Tram else TransitKind.Train),
        )
        else -> Place(name = name, subtitle = "", lat = lat, lon = lon, type = subclass ?: kind)
    }
}

/**
 * How far to zoom the driving view: close in town, further out the faster the road, so there's
 * always some seconds of road ahead on screen. Coming up to a turn it zooms back in to show it.
 */
private fun drivingZoom(speed: Float, progress: RouteProgress): Double {
    // 17.5 up to about 30 km/h, down to 15 at about 120 km/h.
    val bySpeed = 17.5 - ((speed - 8.0) / 25.0).coerceIn(0.0, 1.0) * 2.5
    // Two hundred meters ahead in town; on a highway exit about ten seconds ahead.
    val turnSoon = progress.next != null && !progress.arrived &&
        progress.distanceToNext < maxOf(200.0, speed * 10.0)
    return if (turnSoon) maxOf(bySpeed, 16.5) else bySpeed
}

/**
 * Draws only the part of the route still ahead while navigating. The stretch from the arrow to
 * the next point of the line is its own tiny source, cheap enough to redraw on every frame as
 * the arrow glides; the rest is only redrawn when the arrow passes a point of the line.
 */
private class RouteAhead {
    private var route: Route? = null
    private var segment = -1

    fun draw(style: Style, route: Route, at: LatLng) {
        if (route.points.size < 2) return
        val p = route.progress(at.latitude, at.longitude)
        val next = p.segment + 1
        fun point(l: LatLng) = Point.fromLngLat(l.longitude, l.latitude)
        if (route !== this.route || p.segment != segment) {
            this.route = route
            segment = p.segment
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(route.legFeatures(from = next)))
        }
        style.getSourceAs<GeoJsonSource>(ROUTE_HEAD_SOURCE)?.setGeoJson(
            Feature.fromGeometry(LineString.fromLngLats(listOf(point(p.snapped), point(route.points[next])))).apply {
                addNumberProperty("leg", route.legAt(p.segment))
            }
        )
    }

    fun clear(style: Style) {
        route = null
        segment = -1
        style.getSourceAs<GeoJsonSource>(ROUTE_HEAD_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    }
}

/** The route from point [from] on as lines, one per leg, each knowing which leg it is for its color. */
private fun Route.legFeatures(from: Int = 0): List<Feature> = legs(from).map { (leg, points) ->
    Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
        addNumberProperty("leg", leg)
    }
}

private fun Place.toLocation() = Location("place").also {
    it.latitude = lat
    it.longitude = lon
}
