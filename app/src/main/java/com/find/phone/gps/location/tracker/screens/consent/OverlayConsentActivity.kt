package com.find.phone.gps.location.tracker.screens.consent

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverlayConsentActivity : AppCompatActivity() {

    companion object {
        private const val AUTO_DISMISS_MS = 3_000L
        private const val POLL_MS = 500L
        private const val TAP_PERIOD_MS = 600L
    }

    private val prefs by lazy { SecurePrefsKeeper(this) }

    private var pollJob: Job? = null
    private var autoDismissJob: Job? = null
    private var handAnim: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_overlay_permission)

        if (prefs.isOverlayTutorialShown) {
            finish()
            return
        }
        prefs.isOverlayTutorialShown = true

        findViewById<View>(R.id.rowMain)?.setOnClickListener { finish() }

        kickHandHint()

        autoDismissJob = lifecycleScope.launch {
            delay(AUTO_DISMISS_MS)
            if (!isFinishing && !isDestroyed) finish()
        }
    }

    override fun onResume() {
        super.onResume()

        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                if (OverlayToolbox.isGranted(this@OverlayConsentActivity)) {
                    finish()
                    return@launch
                }
                delay(POLL_MS)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pollJob?.cancel()
    }

    override fun onDestroy() {
        handAnim?.cancel()
        handAnim = null
        super.onDestroy()
    }

    private fun kickHandHint() {
        val hand = findViewById<ImageView>(R.id.picHand) ?: return
        val up = dp(2f)
        val down = dp(12f)

        val translate = ObjectAnimator.ofFloat(hand, View.TRANSLATION_Y, down, up)
        val scaleX = ObjectAnimator.ofFloat(hand, View.SCALE_X, 1f, 0.82f)
        val scaleY = ObjectAnimator.ofFloat(hand, View.SCALE_Y, 1f, 0.82f)

        handAnim = AnimatorSet().apply {
            duration = TAP_PERIOD_MS
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(translate, scaleX, scaleY)
            childAnimations.forEach {
                (it as ObjectAnimator).repeatCount = ValueAnimator.INFINITE
                it.repeatMode = ValueAnimator.REVERSE
            }
            start()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
