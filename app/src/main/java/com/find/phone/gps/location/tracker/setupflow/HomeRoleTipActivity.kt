package com.find.phone.gps.location.tracker.setupflow

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.dp
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.loop
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.pvh
import com.find.phone.gps.location.tracker.helpers.SafeLogger

class HomeRoleTipActivity : AppCompatActivity() {

    private val autoFinish = Runnable { if (!isFinishing) finish() }
    private val animators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_home_hint)
        SafeLogger.log(TAG, "hint shown over the home-app list")

        val root = findViewById<View>(R.id.rowMain)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        runCatching {
            findViewById<TextView>(R.id.guideRowNameTvVw)?.text =
                packageManager.getApplicationLabel(applicationInfo).toString().trim()
        }

        wireDismissOnTouch(root)
        enter(findViewById(R.id.overlayCardNode))
        kickTapLoop()

        visible = this
        root.postDelayed(autoFinish, AUTO_FINISH_MS)
    }

    private fun enter(card: View) {
        if (VisualTweens.animationsDisabled(this)) return
        val rise = dp(12f)
        card.alpha = 0f
        card.translationY = rise
        card.scaleX = 0.97f
        card.scaleY = 0.97f
        animators += ObjectAnimator.ofPropertyValuesHolder(
            card,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, rise, 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.97f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.97f, 1f),
        ).apply {
            duration = 450L
            interpolator = ENTER
            start()
        }
    }

    private fun kickTapLoop() {
        if (VisualTweens.animationsDisabled(this)) {

            findViewById<View>(R.id.tipRadioRingOn)?.alpha = 1f
            findViewById<View>(R.id.tipRadioDot)?.apply { scaleX = 1f; scaleY = 1f }
            return
        }
        val hand = findViewById<View>(R.id.tipHand)
        val ripple = findViewById<View>(R.id.tipRipple)
        val ringOn = findViewById<View>(R.id.tipRadioRingOn)
        val dot = findViewById<View>(R.id.tipRadioDot)
        val glow = findViewById<View>(R.id.tipRowGlow)

        val restX = dp(10f)
        val restY = dp(12f)
        animators += loop(
            hand, CYCLE_MS, 0L,
            pvh(View.TRANSLATION_X, VisualTweens.EASE_IN_OUT, 0f to restX, .38f to 0f, .62f to 0f, 1f to restX),
            pvh(View.TRANSLATION_Y, VisualTweens.EASE_IN_OUT, 0f to restY, .38f to 0f, .62f to 0f, 1f to restY),
            pvh(View.SCALE_X, VisualTweens.EASE_IN_OUT, 0f to 1f, .38f to 1f, .48f to .86f, .62f to 1f, 1f to 1f),
            pvh(View.SCALE_Y, VisualTweens.EASE_IN_OUT, 0f to 1f, .38f to 1f, .48f to .86f, .62f to 1f, 1f to 1f),
        )

        animators += loop(
            ripple, CYCLE_MS, 0L,
            pvh(View.SCALE_X, VisualTweens.EASE_OUT, 0f to .3f, .44f to .3f, .70f to 1.4f, 1f to 1.4f),
            pvh(View.SCALE_Y, VisualTweens.EASE_OUT, 0f to .3f, .44f to .3f, .70f to 1.4f, 1f to 1.4f),
            pvh(View.ALPHA, VisualTweens.EASE_OUT, 0f to 0f, .44f to 0f, .50f to .5f, .70f to 0f, 1f to 0f),
        )

        animators += loop(
            ringOn, CYCLE_MS, 0L,
            pvh(View.ALPHA, VisualTweens.EASE, 0f to 0f, .46f to 0f, .54f to 1f, .90f to 1f, 1f to 0f),
        )

        animators += loop(
            dot, CYCLE_MS, 0L,
            pvh(View.SCALE_X, VisualTweens.EASE, 0f to 0f, .46f to 0f, .54f to 1f, .90f to 1f, 1f to 0f),
            pvh(View.SCALE_Y, VisualTweens.EASE, 0f to 0f, .46f to 0f, .54f to 1f, .90f to 1f, 1f to 0f),
        )

        animators += loop(
            glow, CYCLE_MS, 0L,
            pvh(View.ALPHA, VisualTweens.EASE_IN_OUT, 0f to 0f, .5f to 1f, 1f to 0f),
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireDismissOnTouch(root: View) {
        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !isFinishing) finish()
            true
        }
    }

    override fun onDestroy() {
        findViewById<View>(R.id.rowMain)?.removeCallbacks(autoFinish)
        VisualTweens.cancelAll(animators)
        if (visible === this) visible = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DefaultHomeHint"

        private const val AUTO_FINISH_MS = 12_000L

        private const val CYCLE_MS = 2400L

        private val ENTER = PathInterpolator(0.2f, 0.9f, 0.3f, 1.1f)

        private var visible: HomeRoleTipActivity? = null

        fun intent(context: Context): Intent =
            Intent(context, HomeRoleTipActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun dismiss() {
            visible?.takeUnless { it.isFinishing }?.finish()
            visible = null
        }
    }
}
