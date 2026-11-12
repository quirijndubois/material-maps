package app.materialmaps

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How fast a fling has to be, in pixels a second, to go on to the next resting height. */
const val FLICK = 900f

/**
 * How high a sheet from the bottom stands, in pixels: drag it (or scroll what's in it) between
 * its resting heights, low, middle and high; let go and it eases to the nearest, or further on
 * with a flick. With [onDismiss], pulled down from the lowest it goes away. Give it its heights
 * with [rest] on every composition; it keeps to the one it rests at as they change.
 */
@Stable
class SheetHeights(private val scope: CoroutineScope) {
    var height by mutableFloatStateOf(0f)
        private set
    private var anchors = listOf(0f, 0f, 0f)
    /** Which of the [anchors] it rests at, or is on its way to. */
    private var at by mutableIntStateOf(1)
    private var held = false
    private var settling: Job? = null
    var onDismiss: (() -> Unit)? = null

    /** For [dragsSheet]. */
    val drag = DraggableState { moveBy(-it) }

    private val lowest get() = anchors.first()
    private val highest get() = anchors.last()

    /** Sets the resting heights, low to high; unless it's being moved, it goes along to its own. */
    fun rest(low: Float, middle: Float, high: Float) {
        val new = listOf(low, middle.coerceIn(low, high.coerceAtLeast(low)), high.coerceAtLeast(low))
        if (new == anchors) return
        val first = anchors.all { it == 0f }
        anchors = new
        when {
            first -> height = new[at]
            !held -> glideTo(at)
        }
    }

    /** Eases to the resting height [index] (0 low, 1 middle, 2 high). */
    fun glideTo(index: Int, velocity: Float = 0f) {
        at = index
        // No heights yet: it starts there once it has them.
        if (anchors.all { it == 0f }) return
        glide(anchors[index], velocity)
    }

    private fun glide(target: Float, velocity: Float, then: (() -> Unit)? = null) {
        settling?.cancel()
        settling = scope.launch {
            animate(height, target, initialVelocity = -velocity, animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) { value, _ ->
                height = value.coerceAtMost(highest)
            }
            then?.invoke()
        }
    }

    fun moveBy(delta: Float): Float {
        settling?.cancel()
        held = true
        val before = height
        height = (height + delta).coerceIn(if (onDismiss != null) 0f else lowest, highest)
        return height - before
    }

    /** Eases to a resting height, or away; [velocity] is the finger's, positive going down. */
    fun settle(velocity: Float) {
        held = false
        val dismiss = onDismiss
        if (dismiss != null && (height < lowest * 0.6f || velocity > FLICK && height <= lowest + 1)) {
            glide(0f, velocity, dismiss)
            return
        }
        val target = when {
            velocity < -FLICK -> anchors.indexOfFirst { it > height + 1 }.takeIf { it >= 0 } ?: 2
            velocity > FLICK -> anchors.indexOfLast { it < height - 1 }.takeIf { it >= 0 } ?: 0
            else -> anchors.indices.minBy { abs(anchors[it] - height) }
        }
        glideTo(target, velocity)
    }

    /** Tapping the handle: up to the middle from low, otherwise down. */
    fun toggle() = settle(if (height < anchors[1] - 1) -FLICK * 2 else FLICK * 2)

    private fun resting() = anchors.any { abs(it - height) < 1f }

    /**
     * For what scrolls in the sheet: scrolling moves the sheet first, up until it's as high as
     * it goes, and down once the content is back at its top.
     */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0 && height < highest) Offset(0f, -moveBy(-available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0 && source == NestedScrollSource.UserInput) Offset(0f, -moveBy(-available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (resting() && !held) return Velocity.Zero
            settle(available.y)
            return available
        }
    }
}

@Composable
fun rememberSheetHeights(): SheetHeights {
    val scope = rememberCoroutineScope()
    return remember { SheetHeights(scope) }
}

/** The sheet's handle, inside what [dragsSheet]: tap it to switch heights. */
@Composable
fun SheetHandle(sheet: SheetHeights) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null) { sheet.toggle() }
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
    }
}

/** Dragging here moves the [sheet]: for its handle and header. */
fun Modifier.dragsSheet(sheet: SheetHeights): Modifier =
    draggable(sheet.drag, Orientation.Vertical, onDragStopped = { sheet.settle(it) })
