package com.find.phone.gps.location.tracker.screens.walkthrough

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens

/**
 * Wires an inflated onboarding scene to the design's element loops by view id and returns
 * the started animators so the adapter can cancel them on recycle:
 *
 *  - A1: phone mock `floaty 4s`, chips at +.8s / +1.6s, search tile at +2.4s
 *  - A2: details card `fadein .8s ease both`
 *  - A3: cursor `blink 1s step-end`, search tile `glow 2s`
 *  - A4: orbit `spin 18s linear`, each tile `spinr 18s` so its icon stays upright
 *
 * Ping rings, the route draw and the typing number animate themselves on attach.
 */
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

        // A3: the result area cross-fades from the "Searching…" shimmer to the result card
        // (the design's `srload` / `srres` on one 4 s loop).
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

    /**
     * `srload` / `srres`: on a 4 s loop the shimmer holds, fades out around 55%, the result card
     * fades in and holds, then both reset. Drives alpha on both views from one animator.
     */
    private fun crossFade(load: View, res: View): Animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = VisualTweens.LINEAR
            addUpdateListener {
                val t = it.animatedValue as Float
                // shimmer: 1 until .52, →0 by .58, 0 until .94, →1 by 1.0
                load.alpha = when {
                    t < 0.52f -> 1f
                    t < 0.58f -> 1f - (t - 0.52f) / 0.06f
                    t < 0.94f -> 0f
                    else -> (t - 0.94f) / 0.06f
                }
                // result: 0 until .52, →1 by .60, 1 until .92, →0 by .98
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
