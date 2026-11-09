package app.materialmaps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fort
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocalPostOffice
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Synagogue
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Yard
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * The map's place icons drawn again in the app's own style: a white Material icon on a round
 * badge, colored by kind of place, as on other maps. They go by the names of the icons in the
 * map style's own set (OpenMapTiles place classes) with [PREFIX] before them, and the place
 * layers ask for those first; kinds without a good match keep the style's icon.
 */
object PoiIcons {
    private enum class Kind(val color: Int) {
        Food(0xFFE8710A.toInt()),
        Shopping(0xFF1A73E8.toInt()),
        Lodging(0xFFD01884.toInt()),
        Culture(0xFF12A4AF.toInt()),
        Nature(0xFF1E8E3E.toInt()),
        Health(0xFFD93025.toInt()),
        Transport(0xFF4D5DC4.toInt()),
        Services(0xFF5F6368.toInt()),
    }

    private val icons: Map<String, Pair<Kind, ImageVector>> = buildMap {
        fun put(kind: Kind, icon: ImageVector, vararg names: String) = names.forEach { put(it, kind to icon) }
        put(Kind.Food, Icons.Filled.Restaurant, "restaurant")
        put(Kind.Food, Icons.Filled.LocalCafe, "cafe")
        put(Kind.Food, Icons.Filled.Fastfood, "fast_food")
        put(Kind.Food, Icons.Filled.LocalBar, "bar")
        put(Kind.Food, Icons.Filled.SportsBar, "beer")
        put(Kind.Food, Icons.Filled.Icecream, "ice_cream")
        put(Kind.Food, Icons.Filled.BakeryDining, "bakery")
        put(Kind.Food, Icons.Filled.SetMeal, "butcher", "sushi")
        put(Kind.Shopping, Icons.Filled.Storefront, "shop", "commercial")
        put(Kind.Shopping, Icons.Filled.LocalGroceryStore, "grocery")
        put(Kind.Shopping, Icons.Filled.Liquor, "alcohol_shop")
        put(Kind.Shopping, Icons.Filled.Checkroom, "clothing_store")
        put(Kind.Shopping, Icons.Filled.LocalFlorist, "florist")
        put(Kind.Shopping, Icons.Filled.Chair, "furniture")
        put(Kind.Shopping, Icons.Filled.CardGiftcard, "gift")
        put(Kind.Shopping, Icons.Filled.ContentCut, "hairdresser")
        put(Kind.Shopping, Icons.Filled.LocalLaundryService, "laundry")
        put(Kind.Lodging, Icons.Filled.Hotel, "lodging")
        put(Kind.Lodging, Icons.Filled.Cabin, "campsite", "shelter")
        put(Kind.Culture, Icons.Filled.Museum, "museum")
        put(Kind.Culture, Icons.Filled.Palette, "art_gallery")
        put(Kind.Culture, Icons.Filled.PhotoCamera, "attraction")
        put(Kind.Culture, Icons.Filled.TheaterComedy, "theatre")
        put(Kind.Culture, Icons.Filled.LocalMovies, "cinema")
        put(Kind.Culture, Icons.Filled.MusicNote, "music")
        put(Kind.Culture, Icons.Filled.Castle, "castle")
        put(Kind.Culture, Icons.Filled.Fort, "monument")
        put(Kind.Culture, Icons.Filled.Attractions, "amusement_park")
        put(Kind.Culture, Icons.Filled.Pets, "zoo", "aquarium")
        put(Kind.Nature, Icons.Filled.Park, "park")
        put(Kind.Nature, Icons.Filled.Yard, "garden")
        put(Kind.Nature, Icons.Filled.Toys, "playground")
        put(Kind.Nature, Icons.Filled.Pets, "dog_park")
        put(Kind.Nature, Icons.Filled.OutdoorGrill, "picnic_site")
        put(Kind.Nature, Icons.Filled.GolfCourse, "golf")
        put(Kind.Nature, Icons.Filled.Stadium, "stadium")
        put(Kind.Nature, Icons.Filled.Pool, "swimming")
        put(Kind.Nature, Icons.Filled.SportsSoccer, "pitch", "soccer")
        put(Kind.Nature, Icons.Filled.SportsTennis, "tennis")
        put(Kind.Nature, Icons.Filled.SportsBasketball, "basketball")
        put(Kind.Nature, Icons.Filled.SportsBaseball, "baseball")
        put(Kind.Nature, Icons.Filled.SportsFootball, "american_football")
        put(Kind.Nature, Icons.Filled.SportsCricket, "cricket")
        put(Kind.Nature, Icons.Filled.DownhillSkiing, "skiing")
        put(Kind.Health, Icons.Filled.LocalHospital, "hospital")
        put(Kind.Health, Icons.Filled.LocalPharmacy, "pharmacy")
        put(Kind.Health, Icons.Filled.MedicalServices, "doctors", "dentist")
        put(Kind.Health, Icons.Filled.Pets, "veterinary")
        put(Kind.Transport, Icons.Filled.DirectionsBus, "bus")
        put(Kind.Transport, Icons.Filled.Train, "railway")
        put(Kind.Transport, Icons.Filled.Subway, "railway_metro")
        put(Kind.Transport, Icons.Filled.Tram, "railway_light")
        put(Kind.Transport, Icons.Filled.DirectionsBoat, "ferry")
        put(Kind.Transport, Icons.Filled.Anchor, "harbor")
        put(Kind.Transport, Icons.Filled.LocalAirport, "airport", "airfield", "airport_11")
        put(Kind.Transport, Icons.Filled.DirectionsCar, "car")
        put(Kind.Transport, Icons.Filled.LocalGasStation, "fuel")
        put(Kind.Transport, Icons.Filled.LocalParking, "parking", "parking_garage")
        put(Kind.Transport, Icons.AutoMirrored.Filled.DirectionsBike, "bicycle", "bicycle_rental")
        put(Kind.Transport, Icons.Filled.DoorFront, "entrance")
        put(Kind.Services, Icons.Filled.AccountBalance, "bank", "town_hall")
        put(Kind.Services, Icons.Filled.LocalAtm, "atm")
        put(Kind.Services, Icons.Filled.LocalPostOffice, "post")
        put(Kind.Services, Icons.Filled.LocalLibrary, "library")
        put(Kind.Services, Icons.Filled.School, "school", "college")
        put(Kind.Services, Icons.Filled.LocalPolice, "police")
        put(Kind.Services, Icons.Filled.LocalFireDepartment, "fire_station")
        put(Kind.Services, Icons.Filled.Church, "place_of_worship", "religious_christian")
        put(Kind.Services, Icons.Filled.Mosque, "religious_muslim")
        put(Kind.Services, Icons.Filled.Synagogue, "religious_jewish")
        put(Kind.Services, Icons.Filled.Info, "information")
        put(Kind.Services, Icons.Filled.Wc, "toilets")
        put(Kind.Services, Icons.Filled.WaterDrop, "drinking_water")
        put(Kind.Services, Icons.Filled.Flag, "embassy")
        put(Kind.Services, Icons.Filled.Phone, "telephone")
        put(Kind.Services, Icons.Filled.Business, "office")
        put(Kind.Services, Icons.Filled.Factory, "industry")
        put(Kind.Services, Icons.Filled.Warehouse, "warehouse")
    }

    /** A place's badge: its name among the map's images, its color, and its icon. */
    class Badge(val image: String, val color: Int, val icon: ImageVector)

    /** OpenStreetMap kinds of places, as search results have them, by the map's own name. */
    private val aliases = mapOf(
        "supermarket" to "grocery", "convenience" to "grocery", "greengrocer" to "grocery", "deli" to "grocery",
        "pub" to "bar", "nightclub" to "bar", "biergarten" to "beer", "food_court" to "fast_food",
        "hotel" to "lodging", "hostel" to "lodging", "guest_house" to "lodging", "motel" to "lodging", "apartment" to "lodging",
        "camp_site" to "campsite", "caravan_site" to "campsite",
        "bicycle_parking" to "bicycle", "station" to "railway", "halt" to "railway", "bus_stop" to "bus", "bus_station" to "bus",
        "tram_stop" to "railway_light", "subway_entrance" to "railway_metro", "subway" to "railway_metro",
        "ferry_terminal" to "ferry", "aerodrome" to "airport", "charging_station" to "fuel", "car_rental" to "car",
        "viewpoint" to "attraction", "artwork" to "art_gallery", "memorial" to "monument", "gallery" to "art_gallery",
        "nature_reserve" to "park", "sports_centre" to "stadium", "fitness_centre" to "stadium",
        "clinic" to "doctors", "kindergarten" to "school", "university" to "college", "townhall" to "town_hall",
        "post_office" to "post", "marketplace" to "shop", "mall" to "shop", "department_store" to "shop",
        "clothes" to "clothing_store", "shoes" to "clothing_store", "wine" to "alcohol_shop", "beverages" to "alcohol_shop",
        "cosmetics" to "shop", "books" to "library", "hairdresser" to "hairdresser", "dry_cleaning" to "laundry",
    )

    /** The badge for [place], by its kind, or null when there's no good one. */
    fun forPlace(place: Place): Badge? {
        val candidates = listOfNotNull(
            place.type, place.type?.let { aliases[it] }, place.poiClass,
            when (place.key) {
                "public_transport" -> if (place.type == "station") "railway" else "bus"
                "shop" -> "shop"
                "tourism" -> "attraction"
                "leisure" -> "park"
                else -> null
            },
        )
        val name = candidates.firstOrNull { it in icons } ?: return null
        val (kind, icon) = icons.getValue(name)
        return Badge(PREFIX + name, pastel(kind.color), icon)
    }

    /** Badges already drawn, by name, for this screen's density. */
    private val drawn = HashMap<String, Bitmap>()
    private var drawnFor: Float? = null

    private const val PREFIX = "mm-"

    /**
     * Puts the icons in [style] and has its place layers use them. Not under the style's own
     * names: its icon set loads after the style, and would take their place again.
     */
    fun apply(style: Style, density: Float) {
        if (drawnFor != density) {
            drawn.clear()
            drawnFor = density
        }
        for ((name, icon) in icons) {
            val bitmap = drawn.getOrPut(name) { badge(icon.second, pastel(icon.first.color), density) }
            style.addImage(PREFIX + name, bitmap)
        }
        for (layer in style.layers.filterIsInstance<SymbolLayer>()) {
            // The style's own icon names for these layers, as its rules have them: the
            // place's kind, or for a few shops what they sell.
            val name = when {
                layer.sourceLayer == "aerodrome_label" -> Expression.literal("airport_11")
                layer.sourceLayer != "poi" || layer.iconImage.isNull -> continue
                layer.id == "poi_transit" -> Expression.toString(Expression.get("class"))
                else -> Expression.switchCase(
                    Expression.any(
                        Expression.eq(Expression.get("subclass"), "florist"),
                        Expression.eq(Expression.get("subclass"), "furniture"),
                    ),
                    Expression.toString(Expression.get("subclass")),
                    Expression.toString(Expression.get("class")),
                )
            }
            // The style draws stops small; a badge needs a little more to read.
            if (layer.id == "poi_transit") layer.setProperties(PropertyFactory.iconSize(0.85f))
            // Ours where there is one, else the style's.
            layer.setProperties(
                PropertyFactory.iconImage(
                    Expression.coalesce(
                        Expression.image(Expression.concat(Expression.literal(PREFIX), name)),
                        Expression.image(name),
                    )
                )
            )
        }
    }

    /** The selected place's pin (see SelectedPin), in dp: its head across, and how high its middle stands. */
    const val PIN_HEAD = 34f
    const val PIN_HEIGHT = 31f
    /** The stem, from the spot up to under the head. */
    const val PIN_STEM = 16f

    /**
     * The pin's head as an image for the map, for once it stands still (see SelectedPin for
     * the same drawn over the map): a round badge in its kind's color with its icon, or in
     * [accent] with a dot, with a soft shadow; its middle is the image's middle.
     */
    fun pinHead(place: Place, accent: Int, density: Float): Bitmap {
        val badge = forPlace(place)
        val head = PIN_HEAD * density
        val shadow = 3 * density
        val size = (head + 2 * shadow).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = size / 2f
        paint.color = badge?.color ?: accent
        paint.setShadowLayer(shadow, 0f, density, 0x55000000)
        canvas.drawCircle(c, c, head / 2, paint)
        paint.clearShadowLayer()
        if (badge != null) {
            val iconSize = 20 * density
            canvas.save()
            canvas.translate(c - iconSize / 2, c - iconSize / 2)
            canvas.scale(iconSize / badge.icon.viewportWidth, iconSize / badge.icon.viewportHeight)
            paint.color = android.graphics.Color.WHITE
            draw(canvas, badge.icon.root, paint)
            canvas.restore()
        } else {
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(c, c, 5 * density, paint)
        }
        return bitmap
    }

    /** The pin's stem as an image for the map, with the spot at its bottom middle. */
    fun pinStem(place: Place, accent: Int, density: Float): Bitmap {
        val color = forPlace(place)?.color ?: accent
        val width = (4 * density).toInt()
        val height = (PIN_STEM * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 3 * density
            strokeCap = Paint.Cap.ROUND
        }
        Canvas(bitmap).drawLine(width / 2f, 1.5f * density, width / 2f, height - 1.5f * density, paint)
        return bitmap
    }

    /** The place layers' own filters, so a hidden place can be shown again. */
    private val filters = java.util.WeakHashMap<Style, Map<String, Expression?>>()

    /**
     * Hides the map's own icon and name for [place] (null: none), where the pin for it stands
     * instead. The map's places have no ids to go by, so it's the one by that name right
     * there: others by the same name, like the rest of a chain, stay.
     */
    fun hide(style: Style, place: Place?) {
        val layers = style.layers.filterIsInstance<SymbolLayer>().filter { it.sourceLayer == "poi" }
        val original = filters.getOrPut(style) { layers.associate { it.id to it.filter } }
        for (layer in layers) {
            val own = original[layer.id]
            val hidden = place?.let { p ->
                val d = 0.0003
                val box = Polygon.fromLngLats(listOf(listOf(
                    Point.fromLngLat(p.lon - d, p.lat - d), Point.fromLngLat(p.lon + d, p.lat - d),
                    Point.fromLngLat(p.lon + d, p.lat + d), Point.fromLngLat(p.lon - d, p.lat + d),
                    Point.fromLngLat(p.lon - d, p.lat - d),
                )))
                Expression.not(Expression.all(Expression.eq(Expression.get("name"), Expression.literal(p.name)), Expression.within(box)))
            }
            when {
                hidden == null && own == null -> layer.setFilter(Expression.literal(true))
                hidden == null -> layer.setFilter(own!!)
                own == null -> layer.setFilter(hidden)
                else -> layer.setFilter(Expression.all(own, hidden))
            }
        }
    }

    /** [color] softened a little towards white. */
    private fun pastel(color: Int): Int = androidx.core.graphics.ColorUtils.blendARGB(color, android.graphics.Color.WHITE, 0.28f)

    /** A round badge of [color] with [icon] on it in white, 24 dp across. */
    private fun badge(icon: ImageVector, color: Int, density: Float): Bitmap {
        val size = (24 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        paint.color = color
        canvas.drawCircle(r, r, r, paint)
        // The icon, 15 dp of its 24, in the middle.
        val iconSize = 15 * density
        canvas.save()
        canvas.translate(r - iconSize / 2, r - iconSize / 2)
        canvas.scale(iconSize / icon.viewportWidth, iconSize / icon.viewportHeight)
        paint.color = android.graphics.Color.WHITE
        draw(canvas, icon.root, paint)
        canvas.restore()
        return bitmap
    }

    /** Draws the paths of a vector icon, as Material icons have them: filled, untransformed. */
    private fun draw(canvas: Canvas, group: VectorGroup, paint: Paint) {
        for (node in group) {
            when (node) {
                is VectorGroup -> draw(canvas, node, paint)
                is VectorPath -> {
                    val path = node.pathData.toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == androidx.compose.ui.graphics.PathFillType.EvenOdd) {
                        android.graphics.Path.FillType.EVEN_ODD
                    } else android.graphics.Path.FillType.WINDING
                    paint.alpha = (node.fillAlpha * 255).toInt()
                    canvas.drawPath(path, paint)
                    paint.alpha = 255
                }
            }
        }
    }
}
