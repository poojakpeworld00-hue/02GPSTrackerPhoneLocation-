package com.find.phone.gps.location.tracker.screens.visual

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.find.phone.gps.location.tracker.R

class HaloBackdropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerX: Float
    private val centerY: Float
    private val radiusX: Float
    private val radiusY: Float
    private val inner: Int
    private val outer: Int
    private val outerStop: Float

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.HaloBackdropView)
        centerX = a.getFraction(R.styleable.HaloBackdropView_haloCenterX, 1, 1, 0.5f)
        centerY = a.getFraction(R.styleable.HaloBackdropView_haloCenterY, 1, 1, 0.4f)
        radiusX = a.getFraction(R.styleable.HaloBackdropView_haloRadiusX, 1, 1, 0.9f)
        radiusY = a.getFraction(R.styleable.HaloBackdropView_haloRadiusY, 1, 1, 0.55f)
        inner = a.getColor(R.styleable.HaloBackdropView_haloInnerColor, 0xFFE3EBFF.toInt())
        outer = a.getColor(R.styleable.HaloBackdropView_haloOuterColor, 0xFFF4F7FB.toInt())
        outerStop = a.getFloat(R.styleable.HaloBackdropView_haloOuterStop, 0.7f)
        a.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val rx = w * radiusX
        val ry = h * radiusY
        val shader = RadialGradient(
            0f, 0f, rx,
            intArrayOf(inner, outer, outer),
            floatArrayOf(0f, outerStop, 1f),
            Shader.TileMode.CLAMP,
        )
        shader.setLocalMatrix(Matrix().apply {
            setScale(1f, ry / rx)
            postTranslate(w * centerX, h * centerY)
        })
        paint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(outer)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
