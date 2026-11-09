package app.materialmaps

import android.content.Context

/** OpenFreeMap's styles; dark mode recolors them (see DarkStyle). */
enum class MapStyle(val label: String, val url: String) {
    Standard("Standard", "https://tiles.openfreemap.org/styles/liberty"),
    Bright("Bright", "https://tiles.openfreemap.org/styles/bright"),
    Minimal("Minimal", "https://tiles.openfreemap.org/styles/positron"),
}

enum class Appearance(val label: String) { System("System"), Light("Light"), Dark("Dark") }

/** The map's own colors, which can differ from the rest of the app. */
enum class MapColors(val label: String) { MatchApp("Match app"), Light("Light"), Dark("Dark") }

/** A button in the bar under the search bar: Home, Work or a kind of place. */
data class QuickButton(val id: String, val label: String, val shortcut: Shortcut? = null, val category: Category? = null) {
    companion object {
        val all = Shortcut.entries.map { QuickButton(it.name, it.label, shortcut = it) } +
            Category.entries.map { QuickButton(it.name, it.label, category = it) }
        /** None: the bar only shows once some are picked in the settings. */
        val defaults = emptyList<String>()

        fun byId(id: String) = all.firstOrNull { it.id == id }
    }
}

enum class Units(val label: String) { Metric("Kilometers"), Imperial("Miles") }

data class Settings(
    val appearance: Appearance = Appearance.System,
    val mapStyle: MapStyle = MapStyle.Standard,
    val mapColors: MapColors = MapColors.MatchApp,
    val buildings3d: Boolean = true,
    /** Satellite photos under the map's roads and labels. */
    val satellite: Boolean = false,
    /** The map's own names of places, streets and the like. */
    val labels: Boolean = true,
    val overlays: Set<Overlay> = emptySet(),
    val showSaved: Boolean = true,
    val units: Units = Units.Metric,
    val avoidHighways: Boolean = false,
    val avoidTolls: Boolean = false,
    val avoidFerries: Boolean = false,
    val voiceGuidance: Boolean = true,
    val vehicle: Vehicle = Vehicle.Arrow,
    /** Ids of the [QuickButton]s to show, in order. */
    val quickButtons: List<String> = QuickButton.defaults,
    /** Besides walking, how public transport journeys may start and end. */
    val transitAccess: TransitAccess = TransitAccess(),
) {
    companion object {
        private const val PREFS = "settings"

        fun load(context: Context): Settings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = Settings()
            return Settings(
                appearance = enumOr(p.getString("appearance", null), d.appearance),
                mapStyle = enumOr(p.getString("mapStyle", null), d.mapStyle),
                mapColors = enumOr(p.getString("mapColors", null), d.mapColors),
                buildings3d = p.getBoolean("buildings3d", d.buildings3d),
                satellite = p.getBoolean("satellite", d.satellite),
                labels = p.getBoolean("labels", d.labels),
                overlays = p.getString("overlays", null)
                    ?.split(',')?.mapNotNull { name -> Overlay.entries.firstOrNull { it.name == name } }?.toSet()
                    ?: d.overlays,
                showSaved = p.getBoolean("showSaved", d.showSaved),
                units = enumOr(p.getString("units", null), d.units),
                avoidHighways = p.getBoolean("avoidHighways", d.avoidHighways),
                avoidTolls = p.getBoolean("avoidTolls", d.avoidTolls),
                avoidFerries = p.getBoolean("avoidFerries", d.avoidFerries),
                voiceGuidance = p.getBoolean("voiceGuidance", d.voiceGuidance),
                vehicle = enumOr(p.getString("vehicle", null), d.vehicle),
                quickButtons = p.getString("quickButtons", null)
                    ?.split(',')?.filter { QuickButton.byId(it) != null } ?: d.quickButtons,
                transitAccess = TransitAccess(
                    start = accessSet(p.getString("transitAccessStart", null)),
                    end = accessSet(p.getString("transitAccessEnd", null)),
                ),
            )
        }

        private fun accessSet(names: String?): Set<Access> =
            names?.split(',')?.mapNotNull { name -> Access.entries.firstOrNull { it.name == name } }?.toSet().orEmpty()

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("appearance", appearance.name)
            .putString("mapStyle", mapStyle.name)
            .putString("mapColors", mapColors.name)
            .putBoolean("buildings3d", buildings3d)
            .putBoolean("satellite", satellite)
            .putBoolean("labels", labels)
            .putString("overlays", overlays.joinToString(",") { it.name })
            .putBoolean("showSaved", showSaved)
            .putString("units", units.name)
            .putBoolean("avoidHighways", avoidHighways)
            .putBoolean("avoidTolls", avoidTolls)
            .putBoolean("avoidFerries", avoidFerries)
            .putBoolean("voiceGuidance", voiceGuidance)
            .putString("vehicle", vehicle.name)
            .putString("quickButtons", quickButtons.joinToString(","))
            .putString("transitAccessStart", transitAccess.start.joinToString(",") { it.name })
            .putString("transitAccessEnd", transitAccess.end.joinToString(",") { it.name })
            .apply()
    }
}
