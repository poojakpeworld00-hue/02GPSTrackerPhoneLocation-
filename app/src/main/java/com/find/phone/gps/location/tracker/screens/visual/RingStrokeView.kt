package com.find.phone.gps.location.tracker.screens.visual

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.find.phone.gps.location.tracker.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

class RingStrokeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash: Float
    private val gap: Float
    private val inset: Boolean

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RingStrokeView)
        paint.color = a.getColor(R.styleable.RingStrokeView_ringColor, 0xFFB6C6FF.toInt())
        paint.strokeWidth = a.getDimension(R.styleable.RingStrokeView_ringWidth, 1.5f * resources.displayMetrics.density)
        dash = a.getDimension(R.styleable.RingStrokeView_ringDash, 0f)
        gap = a.getDimension(R.styleable.RingStrokeView_ringGap, dash)
        inset = a.getBoolean(R.styleable.RingStrokeView_ringInset, true)
        a.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (dash <= 0f) return
        val circumference = (2 * PI * radius()).toFloat()
        val count = max(1, (circumference / (dash + gap)).roundToInt())
        val unit = circumference / count
        val on = unit * dash / (dash + gap)
        paint.pathEffect = DashPathEffect(floatArrayOf(on, unit - on), 0f)
    }

    private fun radius(): Float {
        val half = minOf(width, height) / 2f
        return if (inset) half - paint.strokeWidth / 2f else half + paint.strokeWidth / 2f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(width / 2f, height / 2f, radius(), paint)
    }
}
