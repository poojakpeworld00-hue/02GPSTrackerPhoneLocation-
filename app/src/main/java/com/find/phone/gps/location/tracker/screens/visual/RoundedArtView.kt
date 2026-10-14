package com.find.phone.gps.location.tracker.screens.visual

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.find.phone.gps.location.tracker.R
import kotlin.math.max

class RoundedArtView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val box = RectF()
    private val corner: Float
    private val zoom: Float
    private val artRes: Int

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RoundedArtView)
        corner = a.getDimension(R.styleable.RoundedArtView_artCorner, 0f)
        zoom = a.getFloat(R.styleable.RoundedArtView_artZoom, 1f)
        artRes = a.getResourceId(R.styleable.RoundedArtView_artSrc, 0)
        a.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0 || artRes == 0) return
        val drawable = ContextCompat.getDrawable(context, artRes) ?: return
        val bitmap = drawable.toBitmap()
        val scale = max(w / bitmap.width.toFloat(), h / bitmap.height.toFloat()) * zoom
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - bitmap.width * scale) / 2f, (h - bitmap.height * scale) / 2f)
        }
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
    }

    override fun onDraw(canvas: Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(box, corner, corner, paint)
    }
}
