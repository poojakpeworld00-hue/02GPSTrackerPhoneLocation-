package com.find.phone.gps.location.tracker.screens.visual

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import com.find.phone.gps.location.tracker.R

class SweepArcRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val color: Int
    private val peakAlpha: Float
    private val peakAt: Float
    private val endAt: Float
    private val thickness: Float

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SweepArcRingView)
        color = a.getColor(R.styleable.SweepArcRingView_arcColor, 0xFF315BFF.toInt())
        peakAlpha = a.getFloat(R.styleable.SweepArcRingView_arcPeakAlpha, 0.55f)
        peakAt = a.getFloat(R.styleable.SweepArcRingView_arcPeakAt, 0.2f)
        endAt = a.getFloat(R.styleable.SweepArcRingView_arcEndAt, 0.4f)
        thickness = a.getFraction(R.styleable.SweepArcRingView_arcThickness, 1, 1, 0.07f)
        a.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val r = minOf(w, h) / 2f
        paint.strokeWidth = r * thickness
        val clear = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        val peak = Color.argb((peakAlpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        val shader = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(clear, peak, clear, clear),
            floatArrayOf(0f, peakAt, endAt, 1f),
        )
        shader.setLocalMatrix(Matrix().apply { setRotate(-90f, w / 2f, h / 2f) })
        paint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        canvas.drawCircle(width / 2f, height / 2f, r - paint.strokeWidth / 2f, paint)
    }
}
