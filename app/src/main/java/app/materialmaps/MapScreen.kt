package app.materialmaps

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.location.Location
import android.net.Uri
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// Free OpenStreetMap vector tiles, no API key needed (https://openfreemap.org). Dark mode
// recolors the detailed light style (see DarkStyle); their own dark style is only a fallback.
private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

private const val PIN_SOURCE = "selected-place"
private const val PIN_LAYER = "selected-place-pin"
private const val ROUTE_SOURCE = "route"
private const val ROUTE_CASING_LAYER = "route-casing"
private const val ROUTE_LAYER = "route-line"
private const val ALT_ROUTE_SOURCE = "route-alternatives"
private const val ALT_ROUTE_LAYER = "route-alternatives-line"
private const val RESULTS_SOURCE = "search-results"
private const val RESULTS_LAYER = "search-results-dot"
private const val TRANSIT_WALK_SOURCE = "transit-walks"
private const val TRANSIT_WALK_LAYER = "transit-walks-line"
private const val TRANSIT_RIDE_SOURCE = "transit-rides"
private const val TRANSIT_RIDE_CASING_LAYER = "transit-rides-casing"
private const val TRANSIT_RIDE_LAYER = "transit-rides-line"

private enum class Follow { Off, Centered, Zoomed, Compass }

private enum class Panel { None, Place, Route, Navigation }

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary.toArgb()
    val bottomInsetPx = WindowInsets.navigationBars.getBottom(density)
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

    var directionsTo by remember { mutableStateOf<Place?>(null) }
    var lastDestination by remember { mutableStateOf<Place?>(null) }
    var travelMode by rememberSaveable { mutableStateOf(TravelMode.Car) }
    var routes by remember { mutableStateOf(emptyList<Route>()) }
    var routeIndex by remember { mutableIntStateOf(0) }
    var routing by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routeRequest by remember { mutableIntStateOf(0) }
    var navigating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<RouteProgress?>(null) }
    var lastRerouteAt by remember { mutableLongStateOf(0L) }
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
        locationComponent()?.cameraMode = CameraMode.NONE
    }

    /** Centers on the user and follows them: at street zoom, or turning with the route while navigating. */
    fun followMe() {
        val component = locationComponent()
        if (component == null) {
            wantsTracking = true
            return
        }
        overview = false
        val here = component.lastKnownLocation
        val m = map
        if (navigating && here != null && m != null) {
            // The driving view: heading up, tilted, with the user low on screen to see the road
            // ahead. Fly there first, then follow: MapLibre drops zoom, tilt and padding changes
            // made while it's still moving the camera into tracking mode.
            val driving = CameraPosition.Builder()
                .target(LatLng(here.latitude, here.longitude))
                .zoom(17.0)
                .tilt(50.0)
                .bearing(if (here.hasBearing()) here.bearing.toDouble() else m.cameraPosition.bearing)
                .padding(0.0, mapView.height * 0.4, 0.0, 0.0)
                .build()
            follow = Follow.Zoomed
            m.animateCamera(CameraUpdateFactory.newCameraPosition(driving), 800, object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    if (navigating) component.cameraMode = CameraMode.TRACKING_GPS
                }
                override fun onCancel() {
                    follow = Follow.Off
                }
            })
            return
        } else if (navigating) {
            component.cameraMode = CameraMode.TRACKING_GPS
        } else {
            component.cameraMode = CameraMode.TRACKING
            component.zoomWhileTracking(15.0)
        }
        follow = Follow.Zoomed
    }

    /** The location button: center, then zoom in, then turn the map with the phone. */
    fun onLocateClick() {
        val component = locationComponent()
        if (component == null || navigating) {
            followMe()
            return
        }
        when (follow) {
            Follow.Off -> {
                component.cameraMode = CameraMode.TRACKING
                follow = Follow.Centered
            }
            Follow.Centered -> {
                component.zoomWhileTracking(16.0)
                follow = Follow.Zoomed
            }
            Follow.Zoomed -> {
                component.cameraMode = CameraMode.TRACKING_COMPASS
                follow = Follow.Compass
            }
            Follow.Compass -> {
                // Back to north-up, still following.
                component.cameraMode = CameraMode.TRACKING_GPS_NORTH
                follow = Follow.Centered
            }
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

    fun showResultsOnMap() {
        if (results.size == 1) {
            pickResult(results.first())
            return
        }
        if (results.isEmpty()) return
        mapResults = results
        resultsOnMap = true
        selected = null
        expanded = false
        keyboard?.hide()
        stopTracking()
        // Frame the nearby matches; far-away ones are still in the list.
        val center = map?.cameraPosition?.target
        val near = center?.let { c -> results.filter { distanceKm(c.latitude, c.longitude, it.lat, it.lon) < 25 } }
        val framed = near?.takeIf { it.isNotEmpty() } ?: results
        if (framed.size == 1) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(framed[0].lat, framed[0].lon), 15.0), 700)
        } else {
            runCatching {
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().includes(framed.map { LatLng(it.lat, it.lon) }).build(),
                        dp(56), topChromePx + dp(40), dp(56), bottomInsetPx + dp(120),
                    ),
                    700,
                )
            }
        }
    }

    fun startDirections(place: Place) {
        directionsTo = place
        lastDestination = place
        routes = emptyList()
        routeIndex = 0
        routeError = null
        routeRequest++
        if (!hasLocationPermission) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    fun stopNavigation() {
        navigating = false
        overview = false
        progress = null
        locationComponent()?.apply {
            cameraMode = CameraMode.NONE
            setNavigationPuck(context, accent, navigating = false)
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
        val route = routes.getOrNull(routeIndex) ?: return
        beforeOverview = if (follow != Follow.Off) null else m.cameraPosition
        stopTracking()
        overview = true
        val here = myLocation()?.let { LatLng(it.latitude, it.longitude) }
        val bounds = LatLngBounds.Builder().includes(route.points + listOfNotNull(here)).build()
        val padding = intArrayOf(dp(48), topChromePx + dp(32), dp(48), bottomInsetPx + dp(200))
        runCatching { m.getCameraForLatLngBounds(bounds, padding, 0.0, 0.0) }.getOrNull()?.let {
            m.animateCamera(CameraUpdateFactory.newCameraPosition(it), 700)
        }
    }

    fun exitDirections() {
        if (navigating) stopNavigation()
        directionsTo = null
        routes = emptyList()
        itineraries = emptyList()
        itineraryIndex = null
        routeError = null
    }

    fun loadLaterConnections() {
        val target = directionsTo ?: return
        val page = transitNextPage ?: return
        val from = myLocation() ?: return
        scope.launch {
            routing = true
            try {
                val more = Transitous.plan(
                    from.latitude, from.longitude, target.lat, target.lon, tripTime.time, tripTime.arriveBy, page,
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

    fun startNavigation() {
        navigating = true
        progress = null
        locationComponent()?.setNavigationPuck(context, accent, navigating = true)
        followMe()
    }

    fun pointNorth() {
        val m = map ?: return
        val component = locationComponent()
        if (component != null && component.cameraMode in setOf(CameraMode.TRACKING_GPS, CameraMode.TRACKING_COMPASS)) {
            component.cameraMode = CameraMode.TRACKING_GPS_NORTH
            if (follow == Follow.Compass) follow = Follow.Centered
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

    // Load the map style once; fall back to the light style if the dark one fails.
    DisposableEffect(mapView) {
        var fellBack = false
        fun load(m: MapLibreMap, builder: Style.Builder) {
            m.setStyle(builder) { loaded ->
                // Routes go above roads and buildings but below the labels, which these styles
                // keep at the end (some symbol layers, like one-way arrows, come much earlier).
                // The pin goes on top of everything.
                var anchor = loaded.layers.lastOrNull { it !is SymbolLayer }?.id
                fun add(layer: Layer) {
                    anchor.let { if (it != null) loaded.addLayerAbove(layer, it) else loaded.addLayer(layer) }
                    anchor = layer.id
                }
                fun line(id: String, source: String, color: Int, width: Float) =
                    LineLayer(id, source).withProperties(
                        PropertyFactory.lineColor(color),
                        PropertyFactory.lineWidth(width),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    )
                loaded.addSource(GeoJsonSource(ALT_ROUTE_SOURCE))
                loaded.addSource(GeoJsonSource(ROUTE_SOURCE))
                loaded.addSource(GeoJsonSource(TRANSIT_WALK_SOURCE))
                loaded.addSource(GeoJsonSource(TRANSIT_RIDE_SOURCE))
                loaded.addSource(GeoJsonSource(PIN_SOURCE))
                loaded.addSource(GeoJsonSource(RESULTS_SOURCE))
                poiLayers = loaded.layers
                    .filter { it is SymbolLayer && it.sourceLayer == "poi" }
                    .map { it.id }
                    .toTypedArray()
                val casing = ColorUtils.blendARGB(accent, android.graphics.Color.BLACK, 0.35f)
                add(line(ALT_ROUTE_LAYER, ALT_ROUTE_SOURCE, colors.outline.toArgb(), 6f))
                add(line(ROUTE_CASING_LAYER, ROUTE_SOURCE, casing, 10f))
                add(line(ROUTE_LAYER, ROUTE_SOURCE, accent, 6f))
                // Walks as dots, rides in the line's own color.
                add(
                    line(TRANSIT_WALK_LAYER, TRANSIT_WALK_SOURCE, colors.onSurfaceVariant.toArgb(), 5f)
                        .withProperties(PropertyFactory.lineDasharray(arrayOf(0f, 1.8f)))
                )
                add(line(TRANSIT_RIDE_CASING_LAYER, TRANSIT_RIDE_SOURCE, casing, 9f))
                add(
                    line(TRANSIT_RIDE_LAYER, TRANSIT_RIDE_SOURCE, accent, 6f)
                        .withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))))
                )
                loaded.addLayer(
                    CircleLayer(RESULTS_LAYER, RESULTS_SOURCE).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(colors.secondary.toArgb()),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
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
                style = loaded
            }
        }
        val failListener = MapView.OnDidFailLoadingMapListener {
            val m = map
            if (m != null && style == null && !fellBack) {
                fellBack = true
                load(m, Style.Builder().fromUri(LIGHT_STYLE))
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
            // Moving the map by hand leaves the route overview; its button then shows it again.
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) overview = false
            }
            m.addOnCameraMoveListener {
                bearing = m.cameraPosition.bearing.toFloat()
                tilt = m.cameraPosition.tilt.toFloat()
            }
            m.addOnMapClickListener { point ->
                val p = m.projection.toScreenLocation(point)
                val slop = dp(16)
                val area = RectF(p.x - slop, p.y - slop, p.x + slop, p.y + slop)
                fun hit(vararg layers: String) =
                    if (layers.isEmpty()) null else m.queryRenderedFeatures(area, *layers).firstOrNull()
                if (directionsTo == null) {
                    // Search results first, then the map's own POIs (shops, sights, stations, stops).
                    hit(RESULTS_LAYER)?.getNumberProperty("index")?.toInt()?.let { mapResults.getOrNull(it) }?.let {
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
                    val route = hit(ALT_ROUTE_LAYER)
                    route?.getNumberProperty("index")?.toInt()?.let { routeIndex = it }
                    route != null
                } else {
                    selected = null
                    false
                }
            }
            m.addOnMapLongClickListener { point ->
                if (directionsTo != null) return@addOnMapLongClickListener false
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
            if (dark) {
                scope.launch {
                    val palette = DarkStyle.Palette(colors.surface.toArgb(), colors.tertiary.toArgb())
                    val cached = DarkStyle.cached(context, palette)
                    if (cached != null) load(m, Style.Builder().fromJson(cached))
                    val fresh = DarkStyle.load(context, LIGHT_STYLE, palette)
                    if (cached == null) {
                        load(m, if (fresh != null) Style.Builder().fromJson(fresh) else Style.Builder().fromUri(DARK_STYLE))
                    }
                }
            } else {
                load(m, Style.Builder().fromUri(LIGHT_STYLE))
            }
        }
        onDispose { mapView.removeOnDidFailLoadingMapListener(failListener) }
    }

    // Show the blue dot and follow the user when asked.
    LaunchedEffect(style, hasLocationPermission, wantsTracking) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (!hasLocationPermission) return@LaunchedEffect
        m.enableLocation(context, s, headingEngine, accent, gps = navigating) { following -> if (!following) follow = Follow.Off }
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
                            maxOf(m.cameraPosition.zoom, 15.0),
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
    // often takes a second to answer); the newest answer to arrive wins instead.
    LaunchedEffect(Unit) {
        var sent = 0L
        var shown = 0L
        snapshotFlow { query.trim() }
            .distinctUntilChanged()
            .debounce(200)
            .collect { q ->
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
                        Result.success(Geocoder.search(q, lat, lon, center?.zoom))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    if (id == sent) searching = false
                    if (id < shown) return@launch
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
    LaunchedEffect(directionsTo, travelMode, routeRequest, hasLocationPermission, tripTime) {
        val target = directionsTo ?: return@LaunchedEffect
        if (!hasLocationPermission) {
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
            var from = myLocation()
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
                )
                itineraryIndex = null
                itineraries = plan.itineraries
                transitNextPage = plan.nextPage
            } else {
                val found = Valhalla.route(from.latitude, from.longitude, target.lat, target.lon, travelMode)
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

    // Draw the routes and, unless navigating, fit them on screen.
    LaunchedEffect(routes, routeIndex, style) {
        val s = style ?: return@LaunchedEffect
        val main = s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return@LaunchedEffect
        val alternatives = s.getSourceAs<GeoJsonSource>(ALT_ROUTE_SOURCE) ?: return@LaunchedEffect
        fun Route.line() = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
        val route = routes.getOrNull(routeIndex)
        main.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(route?.let { Feature.fromGeometry(it.line()) })))
        alternatives.setGeoJson(
            FeatureCollection.fromFeatures(
                routes.mapIndexedNotNull { i, r ->
                    if (i == routeIndex) null
                    else Feature.fromGeometry(r.line()).apply { addNumberProperty("index", i) }
                }
            )
        )
        if (route != null && route.points.size >= 2 && !navigating) {
            stopTracking()
            val bounds = LatLngBounds.Builder().includes(route.points).build()
            runCatching {
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds, dp(48), topChromePx + dp(32), dp(48), bottomInsetPx + dp(300),
                    ),
                    700,
                )
            }
        }
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
            runCatching {
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().includes(points).build(),
                        dp(48), topChromePx + dp(32), dp(48), bottomInsetPx + dp(360),
                    ),
                    700,
                )
            }
        }
    }

    // Search results as pins on the map.
    LaunchedEffect(resultsOnMap, mapResults, style) {
        val source = style?.getSourceAs<GeoJsonSource>(RESULTS_SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                if (!resultsOnMap) emptyList()
                else mapResults.mapIndexed { i, place ->
                    Feature.fromGeometry(Point.fromLngLat(place.lon, place.lat)).apply { addNumberProperty("index", i) }
                }
            )
        )
    }

    // Live departures for stops and stations, refreshed while the card is open.
    LaunchedEffect(selected) {
        departures = null
        val place = selected ?: return@LaunchedEffect
        val radius = place.departureRadius() ?: return@LaunchedEffect
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

    // Follow progress along the route while navigating, and reroute when the user leaves it.
    LaunchedEffect(navigating, routes, routeIndex) {
        if (!navigating) return@LaunchedEffect
        val route = routes.getOrNull(routeIndex) ?: return@LaunchedEffect
        var offRouteTicks = 0
        var speed = 0f
        while (true) {
            val here = myLocation()
            if (here != null) {
                val p = route.progress(here.latitude, here.longitude)
                progress = p
                // Smoothed, so one odd GPS reading doesn't make the map jump.
                if (here.hasSpeed()) speed += (here.speed - speed) * 0.3f
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
                if (offRouteTicks >= 3 && !routing && now - lastRerouteAt > 15_000) {
                    lastRerouteAt = now
                    routeRequest++
                }
            }
            delay(1000)
        }
    }

    // While navigating, the arrow and the map point along the route rather than where the phone points.
    LaunchedEffect(navigating, routes, routeIndex) {
        headingEngine.route = if (navigating) routes.getOrNull(routeIndex) else null
    }

    // Keep the screen on while navigating.
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = !expanded && (navigating || directionsTo != null || selected != null || resultsOnMap)) {
        when {
            navigating -> stopNavigation()
            directionsTo != null -> exitDirections()
            selected != null -> selected = null
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

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        var showCredits by rememberSaveable { mutableStateOf(false) }
        CreditsButton(
            onClick = { showCredits = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 4.dp, bottom = 4.dp),
        )
        if (showCredits) CreditsSheet(onDismiss = { showCredits = false })

        AnimatedVisibility(
            visible = compassVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = with(density) { topChromePx.toDp() } + 12.dp, end = 16.dp),
        ) {
            Compass(bearing = { bearing }, onClick = ::pointNorth)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val following = follow != Follow.Off
            if (navigating && directionsTo != null) {
                // Like a car navigation app: see the whole route, then jump back to the driving view.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
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
            val panel = when {
                directionsTo != null && navigating -> Panel.Navigation
                directionsTo != null -> Panel.Route
                selected != null -> Panel.Place
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
                val panelModifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .animateContentSize()
                when {
                    target == Panel.Navigation && destination != null -> NavigationPanel(
                        route = routes.getOrNull(routeIndex),
                        progress = progress,
                        onEnd = ::stopNavigation,
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
                        onStart = ::startNavigation,
                        onOpenElsewhere = { context.openDirections(destination) },
                        modifier = panelModifier,
                    )
                    target == Panel.Place && place != null -> PlaceCard(
                        place,
                        onDirections = { startDirections(place) },
                        onClose = { selected = null },
                        modifier = panelModifier,
                        departures = if (place.departureRadius() != null) {
                            { DepartureBoard(departures, departuresLoading, Modifier.padding(top = 16.dp, end = 12.dp)) }
                        } else null,
                    )
                    else -> Spacer(Modifier.fillMaxWidth())
                }
            }
        }

        val destination = directionsTo
        when {
            destination != null && navigating -> NavigationBanner(
                route = routes.getOrNull(routeIndex),
                progress = progress,
                destination = destination,
                rerouting = routing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .then(trackTop),
            )
            destination != null -> DirectionsHeader(
                destination = destination,
                mode = travelMode,
                onModeChange = { travelMode = it },
                onBack = ::exitDirections,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .then(trackTop),
            )
            else -> {
                val sidePadding by animateDpAsState(if (expanded) 0.dp else 16.dp, label = "searchPadding")
                SearchBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = sidePadding)
                        .then(trackTop),
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = {
                                query = it
                                resultsOnMap = false
                            },
                            onSearch = { showResultsOnMap() },
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
                        q.isEmpty() -> if (recents.isNotEmpty()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Recent",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    RecentPlaces.clear(context)
                                    recents = emptyList()
                                }) { Text("Clear") }
                            }
                            LazyColumn {
                                items(recents) { place ->
                                    PlaceRow(place, here, onClick = { pickResult(place) }, recent = true)
                                }
                            }
                        }
                        searchFailed && results.isEmpty() -> SearchHint("Couldn't reach the search service. Check your connection.")
                        results.isEmpty() && resultsFor == q && !searching -> SearchHint("No results for \"$q\"")
                        else -> LazyColumn {
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
    onDirections: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    departures: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
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
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDirections) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Directions")
                }
                OutlinedButton(onClick = { context.sharePlace(place) }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                OutlinedIconButton(onClick = { context.openPlaceElsewhere(place) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in another app")
                }
            }
            departures?.invoke()
        }
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
    component.setNavigationPuck(context, color, gps)
}

/** The blue dot with a heading cone normally; a direction arrow while navigating. */
private fun LocationComponent.setNavigationPuck(context: Context, color: Int, navigating: Boolean) {
    applyStyle(locationOptions(context, color, navigating))
    renderMode = if (navigating) RenderMode.GPS else RenderMode.COMPASS
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
    val uri = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(place.name)})")
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No navigation app installed", Toast.LENGTH_SHORT).show()
    }
}

/** Shows the place in another maps app (Google Maps, Organic Maps, OsmAnd, ...), asking which. */
private fun Context.openPlaceElsewhere(place: Place) {
    val uri = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(place.name)})")
    // The chooser says so itself when no app can open it.
    startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open in"))
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
