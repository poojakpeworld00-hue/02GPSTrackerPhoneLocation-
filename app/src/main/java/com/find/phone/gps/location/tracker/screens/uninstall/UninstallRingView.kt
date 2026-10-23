package com.find.phone.gps.location.tracker.screens.uninstall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.R

/**
 * The progress screen's ring: a 10dp track with a round-capped arc from 12 o'clock.
 * [progress] is 0..100; [arcColor] turns green when the flow is ready.
 */
class UninstallRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val stroke = 10f * resources.displayMetrics.density
    private val bounds = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = ContextCompat.getColor(context, R.color.ds_track)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ds_primary)
    }

    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var arcColor: Int
        get() = arcPaint.color
        set(value) {
            arcPaint.color = value
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = stroke / 2f
        bounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawOval(bounds, trackPaint)
        if (progress > 0) canvas.drawArc(bounds, -90f, 360f * progress / 100f, false, arcPaint)
    }
}
