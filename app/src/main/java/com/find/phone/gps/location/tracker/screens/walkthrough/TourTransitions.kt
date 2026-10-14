package com.find.phone.gps.location.tracker.screens.walkthrough

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens

object TourTransitions {

    fun attach(root: View): List<Animator> {
        if (VisualTweens.animationsDisabled(root.context)) return emptyList()
        val out = ArrayList<Animator>(8)

        root.findViewById<View?>(R.id.obPhoneMockVw)?.let { out += VisualTweens.floaty(it) }
        root.findViewById<View?>(R.id.obChipDialVw)?.let { out += VisualTweens.floaty(it, delayMs = 800L) }
        root.findViewById<View?>(R.id.obChipGpsVw)?.let { out += VisualTweens.floaty(it, delayMs = 1600L) }
        root.findViewById<View?>(R.id.obSearchTileVw)?.let { out += VisualTweens.floaty(it, delayMs = 2400L) }

        root.findViewById<View?>(R.id.obDetailCardVw)?.let { out += VisualTweens.fadeIn(it, durationMs = 800L) }

        root.findViewById<View?>(R.id.obCursorVw)?.let { out += VisualTweens.blink(it) }
        root.findViewById<View?>(R.id.obGlowVw)?.let { out += VisualTweens.glow(it, durationMs = 2000L) }

        val srLoad = root.findViewById<View?>(R.id.obSrLoadVw)
        val srRes = root.findViewById<View?>(R.id.obSrResVw)
        if (srLoad != null && srRes != null) out += crossFade(srLoad, srRes)

        root.findViewById<View?>(R.id.obOrbitVw)?.let { out += VisualTweens.spin(it, ORBIT_MS) }
        listOf(R.id.obTile1Vw, R.id.obTile2Vw, R.id.obTile3Vw, R.id.obTile4Vw).forEach { id ->
            root.findViewById<View?>(id)?.let { out += VisualTweens.spin(it, ORBIT_MS, reverse = true) }
        }
        return out
    }

    private const val ORBIT_MS = 18_000L

    private fun crossFade(load: View, res: View): Animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = VisualTweens.LINEAR
            addUpdateListener {
                val t = it.animatedValue as Float

                load.alpha = when {
                    t < 0.52f -> 1f
                    t < 0.58f -> 1f - (t - 0.52f) / 0.06f
                    t < 0.94f -> 0f
                    else -> (t - 0.94f) / 0.06f
                }

                res.alpha = when {
                    t < 0.52f -> 0f
                    t < 0.60f -> (t - 0.52f) / 0.08f
                    t < 0.92f -> 1f
                    t < 0.98f -> 1f - (t - 0.92f) / 0.06f
                    else -> 0f
                }
            }
            start()
        }
}
