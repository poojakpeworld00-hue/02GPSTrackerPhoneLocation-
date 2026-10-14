package com.find.phone.gps.location.tracker.screens.visual

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.find.phone.gps.location.tracker.R
import kotlin.math.floor
import kotlin.math.roundToInt

class TypewriterLabel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {

    private val fullText: String
    private val durationMs: Long
    private var animator: Animator? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.TypewriterLabel)
        fullText = a.getString(R.styleable.TypewriterLabel_typingText) ?: text.toString()
        durationMs = a.getInt(R.styleable.TypewriterLabel_typingDuration, 4000).toLong()
        a.recycle()
        text = fullText
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (VisualTweens.animationsDisabled(context)) {
            text = fullText
            return
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = VisualTweens.LINEAR
            addUpdateListener {
                val t = it.animatedValue as Float
                val stepped = floor(t * STEPS) / STEPS
                val fraction = ((stepped - 0.08f) / 0.54f).coerceIn(0f, 1f)
                val chars = (fraction * fullText.length).roundToInt()
                val shown = fullText.take(chars)
                if (shown != text.toString()) text = shown
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STEPS = 12f
    }
}
