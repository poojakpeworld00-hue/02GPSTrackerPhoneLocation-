package com.find.phone.gps.location.tracker.screens.finder

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration as UiConfiguration
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.repo.finder.ZoneAccuracy
import com.find.phone.gps.location.tracker.repo.finder.MapMarkerPoint
import com.find.phone.gps.location.tracker.repo.finder.NumberSummary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import kotlin.math.log2

object OpenStreetTiles {

    private var configured = false

    fun init(context: Context) {
        if (configured) return
        val app = context.applicationContext
        Configuration.getInstance().apply {
            load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = app.packageName
            osmdroidBasePath = File(app.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        configured = true
    }

    @SuppressLint("ClickableViewAccessibility")
    fun style(map: MapView, interactive: Boolean) = map.apply {
        setTileSource(TileSourceFactory.MAPNIK)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 2.5
        maxZoomLevel = 18.0
        setMultiTouchControls(interactive)
        if (!interactive) setOnTouchListener { _, _ -> true }

        val night = resources.configuration.uiMode and UiConfiguration.UI_MODE_NIGHT_MASK
        if (night == UiConfiguration.UI_MODE_NIGHT_YES) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
    }

    fun place(map: MapView, report: NumberSummary, coarse: Boolean): ZoneRippleOverlay? {
        val pin = report.pin ?: return null
        val precision = if (coarse && report.precision.zoom > ZoneAccuracy.STATE.zoom) ZoneAccuracy.STATE else report.precision
        return placeAt(map, pin, precision)
    }

    fun cardZoom(precision: ZoneAccuracy): Double {
        if (precision.radiusMeters <= 0.0) return precision.zoom
        val fit = ZoneAccuracy.STATE.zoom + log2(ZoneAccuracy.STATE.radiusMeters / precision.radiusMeters)
        return minOf(precision.zoom, fit)
    }

    fun placeAt(map: MapView, pin: MapMarkerPoint, precision: ZoneAccuracy, zoom: Double = precision.zoom): ZoneRippleOverlay {
        val point = GeoPoint(pin.lat, pin.lng)
        map.controller.setZoom(zoom)
        map.controller.setCenter(point)
        val context = map.context
        return ZoneRippleOverlay(
            map, point, precision.radiusMeters,
            ContextCompat.getColor(context, R.color.ds_primary), context.resources.displayMetrics.density,
        ).also {
            map.overlays.add(it)
            it.start()
        }
    }
}
