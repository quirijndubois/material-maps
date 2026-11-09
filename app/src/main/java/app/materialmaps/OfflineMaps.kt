package app.materialmaps

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.geojson.Geometry

/** A downloaded (or downloading) map area. */
data class OfflineArea(
    val id: Long,
    val name: String,
    val bytes: Long,
    val progress: Float,
    val complete: Boolean,
    val error: String?,
    /** Part of a downloaded region, rather than an area downloaded as it was on screen. */
    val region: Boolean = false,
)

/**
 * Map areas kept on the phone for use without a connection, through MapLibre's offline
 * database: regions from [OfflineRegions], and areas downloaded as they were on screen in
 * earlier versions.
 */
class OfflineMaps(context: Context) {
    private val manager = OfflineManager.getInstance(context).apply {
        // A city with its surroundings is tens of thousands of tiles; the default allows 6000.
        setOfflineMapboxTileCountLimit(MAX_TILES)
    }
    private val regions = mutableMapOf<Long, OfflineRegion>()

    var areas by mutableStateOf(emptyList<OfflineArea>())
        private set

    fun refresh() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions.orEmpty().forEach { region ->
                    regions[region.id] = region
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            status?.let { update(region, it, null) }
                            // Carry on with downloads cut short when the app closed.
                            if (status != null && !status.isComplete) watch(region)
                        }
                        override fun onError(error: String?) {}
                    })
                }
            }
            override fun onError(error: String) {}
        })
    }

    /**
     * Downloads the map inside [outline], from the whole world down to the most detailed
     * tiles of [styleUrl]; [onCreate] gets its id once it's started.
     */
    fun downloadOutline(name: String, styleUrl: String, outline: Geometry, pixelRatio: Float, onCreate: (Long) -> Unit) {
        val definition = OfflineGeometryRegionDefinition(styleUrl, outline, 0.0, MAX_ZOOM, pixelRatio)
        val metadata = JSONObject().put("name", name).put("region", true).toString().toByteArray()
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                regions[offlineRegion.id] = offlineRegion
                areas = areas + OfflineArea(offlineRegion.id, name, 0, 0f, false, null, region = true)
                onCreate(offlineRegion.id)
                watch(offlineRegion)
            }
            override fun onError(error: String) {}
        })
    }

    fun delete(id: Long) {
        val region = regions.remove(id) ?: return
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                areas = areas.filterNot { it.id == id }
            }
            override fun onError(error: String) {}
        })
    }

    private fun watch(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                update(region, status, null)
                if (status.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
            override fun onError(error: OfflineRegionError) = Unit
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                update(region, null, "Too large to download; pick a smaller region")
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    private fun update(region: OfflineRegion, status: OfflineRegionStatus?, error: String?) {
        val metadata = runCatching { JSONObject(String(region.metadata)) }.getOrNull()
        val name = metadata?.optString("name")?.ifBlank { null } ?: "Map area"
        val old = areas.firstOrNull { it.id == region.id }
        val area = OfflineArea(
            id = region.id,
            name = name,
            bytes = status?.completedResourceSize ?: old?.bytes ?: 0,
            progress = status?.let {
                if (it.requiredResourceCount > 0) it.completedResourceCount.toFloat() / it.requiredResourceCount else 0f
            } ?: old?.progress ?: 0f,
            complete = status?.isComplete ?: old?.complete ?: false,
            error = error,
            region = metadata?.optBoolean("region") == true,
        )
        areas = if (old == null) areas + area else areas.map { if (it.id == area.id) area else it }
    }

    private companion object {
        /** As much as the largest region that can be downloaded (see [Region.MAX_TILES]), and some. */
        const val MAX_TILES = 4_000_000L
        /** OpenFreeMap's tiles stop at 14; closer in, the map scales those up. */
        const val MAX_ZOOM = 14.0
    }
}
