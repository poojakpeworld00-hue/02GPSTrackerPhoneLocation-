package com.find.phone.gps.location.tracker.screens.visual

import android.animation.Animator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.find.phone.gps.location.tracker.R

class PulseHaloView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val durationMs: Long
    private val delayMs: Long
    private var animator: Animator? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PulseHaloView)
        stroke.color = a.getColor(R.styleable.PulseHaloView_pingColor, 0x733D63FF)
        stroke.strokeWidth = a.getDimension(R.styleable.PulseHaloView_pingStrokeWidth, 2f * resources.displayMetrics.density)
        fill.color = a.getColor(R.styleable.PulseHaloView_pingFillColor, 0)
        durationMs = a.getInt(R.styleable.PulseHaloView_pingDuration, 2800).toLong()
        delayMs = a.getInt(R.styleable.PulseHaloView_pingDelay, 0).toLong()
        a.recycle()

        alpha = 0f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - stroke.strokeWidth / 2f
        if (fill.color != 0) canvas.drawCircle(cx, cy, r, fill)
        if (stroke.strokeWidth > 0f) canvas.drawCircle(cx, cy, r, stroke)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (VisualTweens.animationsDisabled(context)) {
            alpha = 0f
            return
        }
        animator?.cancel()
        animator = VisualTweens.ping(this, durationMs, delayMs)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
