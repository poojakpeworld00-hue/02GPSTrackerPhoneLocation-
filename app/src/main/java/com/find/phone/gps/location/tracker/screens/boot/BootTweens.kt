package com.find.phone.gps.location.tracker.screens.boot

import android.animation.Animator
import android.animation.TimeInterpolator
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.PageSplashBinding
import com.find.phone.gps.location.tracker.databinding.PartOrbitPillBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.dp
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.loop
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.once
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.pvh

class BootTweens(private val binding: PageSplashBinding) {

    private val animators = mutableListOf<Animator>()
    private val context get() = binding.root.context
    private val orbit get() = binding.orbitStageVw
    private val brand get() = binding.brandBlockVw
    private val messages get() = binding.messageBoxVw

    private val pills: List<Triple<PartOrbitPillBinding, Pair<Float, Float>, Pair<Int, Int>>>
        get() = listOf(
            Triple(orbit.pillPhoneVw, 0f to -98f, R.drawable.so_dot_phone to R.drawable.so_ic_phone),
            Triple(orbit.pillPinVw, 85f to 49f, R.drawable.so_dot_pin to R.drawable.so_ic_pin),
            Triple(orbit.pillHashVw, -85f to 49f, R.drawable.so_dot_hash to R.drawable.so_ic_hash),
        )

    fun start() {
        cancel()
        dressPills()
        binding.barFillVw.pivotX = 0f
        if (VisualTweens.animationsDisabled(context)) {
            presentFinalFrame()
            return
        }
        iconIn()
        orbitIn(orbit.ringSolidVw, 400L)
        orbitIn(orbit.ringDashedVw, 600L)
        sweep()
        spinOrbit()
        pills.forEachIndexed { i, (pill, _, _) -> pop(pill.pillBodyVw, 1000L + i * 200L) }
        rise(brand.brandTitleVw, 1900L)
        rise(brand.brandSubtitleVw, 2050L)
        rise(brand.brandTaglineVw, 2200L)
        bar()
        cycleMessages()
    }

    fun cancel() = VisualTweens.cancelAll(animators)

    private fun dressPills() {
        pills.forEach { (pill, offset, art) ->
            pill.root.translationX = context.dp(offset.first)
            pill.root.translationY = context.dp(offset.second)
            pill.pillDotVw.background = ContextCompat.getDrawable(context, art.first)
            pill.pillGlyphVw.setImageResource(art.second)
        }
    }

    private fun presentFinalFrame() {
        listOf(
            orbit.iconTileVw, orbit.ringSolidVw, orbit.ringDashedVw, orbit.sweepRingVw,
            brand.brandTitleVw, brand.brandSubtitleVw, brand.brandTaglineVw,
            binding.barTrackVw, messages.root, messages.msgSyncVw,
        ).forEach { it.alpha = 1f }
        pills.forEach { (pill, _, _) -> pill.pillBodyVw.scaleX = 1f; pill.pillBodyVw.scaleY = 1f }
        binding.barFillVw.scaleX = 1f
    }

    private fun iconIn() {
        animators += once(
            orbit.iconTileVw, 700L, 150L,
            pvh(View.ALPHA, ICON_IN, 0f to 0f, .7f to 1f, 1f to 1f),
            pvh(View.SCALE_X, ICON_IN, 0f to .4f, .7f to 1.06f, 1f to 1f),
            pvh(View.SCALE_Y, ICON_IN, 0f to .4f, .7f to 1.06f, 1f to 1f),
        )
    }

    private fun orbitIn(ring: View, delayMs: Long) {
        animators += once(
            ring, 500L, delayMs,
            pvh(View.ALPHA, VisualTweens.EASE_OUT, 0f to 0f, 1f to 1f),
            pvh(View.SCALE_X, VisualTweens.EASE_OUT, 0f to .7f, 1f to 1f),
            pvh(View.SCALE_Y, VisualTweens.EASE_OUT, 0f to .7f, 1f to 1f),
        )
    }

    private fun sweep() {
        animators += VisualTweens.spin(orbit.sweepRingVw, 2400L)
        animators += once(orbit.sweepRingVw, 400L, 500L, pvh(View.ALPHA, VisualTweens.EASE, 0f to 0f, 1f to 1f))
    }

    private fun spinOrbit() {
        animators += VisualTweens.spin(orbit.orbitSpinVw, ORBIT_MS)
        pills.forEach { (pill, _, _) -> animators += VisualTweens.spin(pill.root, ORBIT_MS, reverse = true) }
    }

    private fun pop(view: View, delayMs: Long) {
        animators += once(
            view, 450L, delayMs,
            pvh(View.SCALE_X, POP, 0f to 0f, 1f to 1f),
            pvh(View.SCALE_Y, POP, 0f to 0f, 1f to 1f),
        )
    }

    private fun rise(view: View, delayMs: Long) {
        val from = context.dp(14f)
        animators += once(
            view, 500L, delayMs,
            pvh(View.ALPHA, RISE, 0f to 0f, 1f to 1f),
            pvh(View.TRANSLATION_Y, RISE, 0f to from, 1f to 0f),
        )
    }

    private fun bar() {
        animators += once(binding.barTrackVw, 300L, 200L, pvh(View.ALPHA, VisualTweens.EASE, 0f to 0f, 1f to 1f))
        animators += once(binding.barFillVw, 2900L, 300L, pvh(View.SCALE_X, BAR, 0f to 0f, 1f to 1f))
    }

    private fun cycleMessages() {
        animators += once(messages.root, 400L, 400L, pvh(View.ALPHA, VisualTweens.EASE, 0f to 0f, 1f to 1f))
        val shift = context.dp(8f)
        listOf(messages.msgSyncVw, messages.msgFetchVw, messages.msgMapVw).forEachIndexed { i, msg ->
            animators += loop(
                msg, MESSAGE_MS, i * 1200L,
                pvh(View.ALPHA, VisualTweens.EASE_IN_OUT, 0f to 0f, .06f to 1f, .27f to 1f, .33f to 0f, 1f to 0f),
                pvh(View.TRANSLATION_Y, VisualTweens.EASE_IN_OUT, 0f to shift, .06f to 0f, .27f to 0f, .33f to -shift, 1f to -shift),
            )
        }
    }

    companion object {

        const val CYCLE_MS = 3200L

        private const val ORBIT_MS = 22_000L
        private const val MESSAGE_MS = 3600L

        private val ICON_IN: TimeInterpolator = PathInterpolator(.3f, 1.3f, .5f, 1f)
        private val POP: TimeInterpolator = PathInterpolator(.3f, 1.4f, .5f, 1f)
        private val RISE: TimeInterpolator = PathInterpolator(.2f, .8f, .2f, 1f)
        private val BAR: TimeInterpolator = PathInterpolator(.5f, 0f, .3f, 1f)
    }
}
