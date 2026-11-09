package app.materialmaps

import org.maplibre.android.geometry.LatLng
import java.text.DateFormat
import java.util.Date

/**
 * A public transport journey as a [Route] to navigate: all its legs as one line, with a
 * maneuver for each turn of the walks and drives, for boarding each ride and for getting off.
 * Boarding comes with its line, time and platform; getting off is announced a little before
 * the stop.
 */
fun Itinerary.toRoute(destination: String): Route {
    val points = ArrayList<LatLng>()
    val legStarts = ArrayList<Int>()
    // Each leg's first point is the previous one's last; the line takes it once.
    val legBegin = legs.map { leg ->
        val begin = (points.size - 1).coerceAtLeast(0)
        val shape = leg.points.ifEmpty { listOf(LatLng(0.0, 0.0)) }
        points += if (points.isNotEmpty() && metersBetween(points.last(), shape.first()) < 1.0) shape.drop(1) else shape
        legStarts += begin
        begin
    }
    val along = DoubleArray(points.size).also { a ->
        for (i in 1 until points.size) a[i] = a[i - 1] + metersBetween(points[i - 1], points[i])
    }
    val maneuvers = ArrayList<Maneuver>()
    val announcements = ArrayList<Announcement>()
    fun add(type: String, modifier: String?, instruction: String, index: Int, sayBefore: Double?) {
        maneuvers += Maneuver(type, modifier, instruction, 0.0, 0.0, index.coerceIn(0, points.lastIndex.coerceAtLeast(0)))
        if (sayBefore != null) announcements += Announcement((along.getOrElse(index) { 0.0 } - sayBefore).coerceAtLeast(0.0), instruction)
    }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    legs.forEachIndexed { i, leg ->
        val begin = legBegin[i]
        val end = legBegin.getOrNull(i + 1) ?: points.lastIndex
        val next = legs.getOrNull(i + 1)
        if (leg.isStreet) {
            // Where to: the stop, or, for a drive to a car park or drop-off, the stop after it.
            val to = leg.toName.takeUnless { it.isBlank() || it == "END" }
                ?: next?.toName?.takeIf { next.isWalk && it != "END" }?.let { "near $it" }
                ?: destination
            val verb = when (leg.mode) {
                "BIKE", "RENTAL" -> "Cycle"
                "CAR" -> "Drive"
                "TAXI" -> "Take a taxi"
                else -> "Walk"
            }
            // Ahead of a turn: a few steps on foot, more by bike, a block by car.
            val ahead = when (leg.mode) {
                "WALK" -> 15.0
                "BIKE", "RENTAL" -> 40.0
                else -> 150.0
            }
            add("depart", null, "$verb to $to", begin, if (i == 0) 0.0 else 10.0)
            var from = begin
            leg.steps.forEach { step ->
                val (type, modifier) = stepManeuver(step.direction) ?: return@forEach
                // Steps come in order along the leg; look onward from the last one.
                val index = (from..end).minByOrNull { metersBetween(points[it], step.start) } ?: from
                if (index <= begin) return@forEach
                from = index
                // The taxi driver knows the way.
                if (leg.mode == "TAXI") return@forEach
                add(type, modifier, stepInstruction(step, type, modifier), index, ahead)
            }
        } else {
            val line = listOfNotNull(leg.line, leg.headsign?.let { "to $it" }).joinToString(" ").ifBlank { leg.agency ?: "the next ride" }
            val board = buildString {
                append("Board ").append(line).append(" at ").append(time.format(Date(leg.departure)))
                leg.fromTrack?.let { append(", platform ").append(it) }
            }
            add("board", leg.mode, board, begin, 30.0)
            // Getting off: said a while before, as stops come quick on a bus and slow on a train.
            val length = along.getOrElse(end) { 0.0 } - along.getOrElse(begin) { 0.0 }
            val off = buildString {
                append("Get off at ").append(leg.toName)
                leg.toTrack?.let { append(", platform ").append(it) }
            }
            add("alight", leg.mode, off, end, minOf(600.0, length * 0.3))
        }
    }
    add("arrive", null, "Arrive at $destination", points.lastIndex, 15.0)
    return Route(
        points = points,
        distance = along.lastOrNull() ?: 0.0,
        duration = (arrival - departure) / 1000.0,
        maneuvers = maneuvers,
        announcements = announcements.sortedBy { it.at },
        legStarts = legStarts.ifEmpty { listOf(0) },
    )
}

/** MOTIS's direction of a step in OSRM's terms, for the icons; null for one that's no turn. */
private fun stepManeuver(direction: String): Pair<String, String?>? = when (direction) {
    "LEFT" -> "turn" to "left"
    "RIGHT" -> "turn" to "right"
    "SLIGHTLY_LEFT" -> "turn" to "slight left"
    "SLIGHTLY_RIGHT" -> "turn" to "slight right"
    "HARD_LEFT" -> "turn" to "sharp left"
    "HARD_RIGHT" -> "turn" to "sharp right"
    "UTURN_LEFT", "UTURN_RIGHT" -> "turn" to "uturn"
    "CIRCLE_CLOCKWISE" -> "roundabout" to "right"
    "CIRCLE_COUNTERCLOCKWISE" -> "roundabout turn" to "left"
    "STAIRS" -> "stairs" to null
    "ELEVATOR" -> "elevator" to null
    else -> null
}

private fun stepInstruction(step: TransitStep, type: String, modifier: String?): String {
    val onto = step.street?.let { " onto $it" }.orEmpty()
    return when (type) {
        "roundabout", "roundabout turn" -> "At the roundabout, take " + (step.exit?.let { "exit $it" } ?: "the exit") + onto
        "stairs" -> "Take the stairs"
        "elevator" -> "Take the elevator"
        else -> when (modifier) {
            "uturn" -> "Make a U-turn$onto"
            "slight left" -> "Keep left$onto"
            "slight right" -> "Keep right$onto"
            else -> "Turn $modifier$onto"
        }
    }
}
