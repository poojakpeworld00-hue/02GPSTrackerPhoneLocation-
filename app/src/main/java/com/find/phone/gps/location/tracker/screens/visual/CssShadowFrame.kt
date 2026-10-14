package com.find.phone.gps.location.tracker.screens.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import com.find.phone.gps.location.tracker.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class CssShadowFrame @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val corner: Float
    private val oval: Boolean
    private val clipContent: Boolean
    private val shadowColor: Int
    private val shadowDy: Float
    private val shadowBlur: Float
    private val shadowSpread: Float
    private val ringWidth: Float

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val box = RectF()
    private var shadowBitmap: Bitmap? = null
    private var shadowPad = 0f

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CssShadowFrame)
        corner = a.getDimension(R.styleable.CssShadowFrame_frameCorner, 0f)
        oval = a.getBoolean(R.styleable.CssShadowFrame_frameOval, false)
        fillPaint.color = a.getColor(R.styleable.CssShadowFrame_frameFill, 0)
        clipContent = a.getBoolean(R.styleable.CssShadowFrame_frameClipContent, false)
        shadowColor = a.getColor(R.styleable.CssShadowFrame_frameShadowColor, 0)
        shadowDy = a.getDimension(R.styleable.CssShadowFrame_frameShadowDy, 0f)
        shadowBlur = a.getDimension(R.styleable.CssShadowFrame_frameShadowBlur, 0f)
        shadowSpread = a.getDimension(R.styleable.CssShadowFrame_frameShadowSpread, 0f)
        ringPaint.color = a.getColor(R.styleable.CssShadowFrame_frameRingColor, 0)
        ringWidth = a.getDimension(R.styleable.CssShadowFrame_frameRingWidth, 0f)
        a.recycle()
        setWillNotDraw(false)
        clipChildren = true
    }

    private fun radiusFor(w: Float, h: Float): Float = if (oval) minOf(w, h) / 2f else corner

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shadowBitmap?.recycle()
        shadowBitmap = null
        if (w == 0 || h == 0 || shadowColor == 0 || shadowBlur <= 0f) return
        shadowPad = ceil(shadowBlur * 1.5f + abs(shadowDy) + max(0f, shadowSpread))
        val bw = (w + shadowPad * 2).toInt()
        val bh = (h + shadowPad * 2).toInt()
        val sigma = shadowBlur / 2f
        val blurRadius = max(0.5f, (sigma - 0.5f) / 0.57735f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        val rect = RectF(
            shadowPad - shadowSpread,
            shadowPad - shadowSpread + shadowDy,
            shadowPad + w + shadowSpread,
            shadowPad + h + shadowSpread + shadowDy,
        )
        val r = max(0f, radiusFor(w.toFloat(), h.toFloat()) + shadowSpread)
        shadowBitmap = createBitmap(bw, bh).also { Canvas(it).drawRoundRect(rect, r, r, paint) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!clipContent) return
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val r = radiusFor(view.width.toFloat(), view.height.toFloat())
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            child.clipToOutline = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = radiusFor(w, h)
        if (ringWidth > 0f && ringPaint.color != 0) {
            box.set(-ringWidth, -ringWidth, w + ringWidth, h + ringWidth)
            canvas.drawRoundRect(box, r + ringWidth, r + ringWidth, ringPaint)
        }
        shadowBitmap?.let { canvas.drawBitmap(it, -shadowPad, -shadowPad, bitmapPaint) }
        if (fillPaint.color != 0) {
            box.set(0f, 0f, w, h)
            canvas.drawRoundRect(box, r, r, fillPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shadowBitmap?.recycle()
        shadowBitmap = null
    }
}
