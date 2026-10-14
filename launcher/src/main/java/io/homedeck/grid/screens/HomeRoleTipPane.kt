package io.homedeck.grid.screens

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import io.homedeck.grid.R
import io.homedeck.grid.bridge.HomeServiceLocator
import timber.log.Timber

class HomeRoleTipPane : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var switchAnimator: Animator? = null
    private lateinit var kind: Kind

    enum class Kind(
        val title: Int,
        val description: Int,

        val position: Position,

        val control: Control,
        val dismissAfterMs: Long,
    ) {
        OVERLAY(
            R.string.launcher_hint_overlay_title,
            R.string.launcher_hint_overlay_description,
            Position.BOTTOM,
            Control.SWITCH,
            3_000L,
        ),
        DEFAULT_HOME(
            R.string.launcher_hint_default_home_title,
            R.string.launcher_hint_default_home_description,
            Position.BOTTOM,
            Control.RADIO,
            3_000L,
        ),
        DEFAULT_SMS(
            R.string.launcher_hint_default_sms_title,
            R.string.launcher_hint_default_sms_description,
            Position.TOP,
            Control.NONE,
            2_500L,
        ),
        ;

        fun isSatisfied(context: Context): Boolean = when (this) {
            OVERLAY ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

            DEFAULT_HOME -> runCatching {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager.resolveActivity(home, 0)
                    ?.activityInfo?.packageName == context.packageName
            }.getOrDefault(false)

            DEFAULT_SMS -> runCatching {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }.getOrDefault(false)
        }
    }

    enum class Position { TOP, BOTTOM }

    enum class Control { SWITCH, RADIO, NONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = runCatching { Kind.valueOf(intent?.getStringExtra(EXTRA_KIND).orEmpty()) }
            .getOrElse {
                finish()
                return
            }

        setContentView(R.layout.hdk_default_home_hint_panel)
        applyKind()

        findViewById<View>(R.id.rowMainUi)?.setOnClickListener { finish() }
        when (kind.control) {
            Control.SWITCH -> kickSwitchAnimation()
            Control.RADIO -> kickRadioAnimation()
            Control.NONE -> Unit
        }

        handler.postDelayed({
            if (!isFinishing) finish()
        }, kind.dismissAfterMs)
        handler.post(watchForDone)
    }

    private fun applyKind() {

        val label = runCatching { applicationInfo.loadLabel(packageManager).toString() }
            .getOrElse { getString(R.string.launcher_app_launcher_name) }
        findViewById<android.widget.TextView>(R.id.guideTitleView)?.text = getString(kind.title, label)
        findViewById<android.widget.TextView>(R.id.guideDescriptionView)?.setText(kind.description)
        findViewById<android.widget.TextView>(R.id.guideMockAppLabelView)?.text = label
        findViewById<android.widget.TextView>(R.id.guideMockOverlayLabelView)?.text = label

        findViewById<View>(R.id.guideHomeMockView)?.visibility =
            if (kind.control == Control.RADIO) View.VISIBLE else View.GONE
        findViewById<View>(R.id.guideOverlayMockView)?.visibility =
            if (kind.control == Control.SWITCH) View.VISIBLE else View.GONE
        findViewById<View>(R.id.guideRowView)?.visibility =
            if (kind.control == Control.NONE) View.VISIBLE else View.GONE

        if (kind.position == Position.TOP) {
            findViewById<View>(R.id.guideSpacerView)?.visibility = View.GONE
            window.setGravity(Gravity.TOP)
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )

            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    private val watchForDone = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (kind.isSatisfied(this@HomeRoleTipPane)) {
                finish()
                return
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    private fun kickSwitchAnimation() {
        val track = findViewById<FrameLayout>(R.id.guideSwitchTrackView) ?: return
        val thumb = findViewById<View>(R.id.guideSwitchThumbView) ?: return
        val row = findViewById<View>(R.id.guideMockOverlayRowView)

        val travel =
            (TRACK_WIDTH_DP - THUMB_SIZE_DP - THUMB_INSET_DP * 2) * resources.displayMetrics.density

        val slide = ObjectAnimator.ofFloat(thumb, View.TRANSLATION_X, 0f, travel).apply {
            duration = FLIP_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val tint = ValueAnimator.ofObject(ArgbEvaluator(), getColor(R.color.hint_switch_off), radioOn()).apply {
            duration = FLIP_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                track.backgroundTintList = ColorStateList.valueOf(animator.animatedValue as Int)
            }
        }

        switchAnimator = AnimatorSet().apply {

            startDelay = HOLD_MS
            playTogether(listOf(slide, tint) + lift(row))
            start()
        }
    }

    private fun lift(row: View?): List<Animator> = row?.let {
        listOf(
            ObjectAnimator.ofFloat(it, View.SCALE_X, 1f, ROW_LIFT_SCALE),
            ObjectAnimator.ofFloat(it, View.SCALE_Y, 1f, ROW_LIFT_SCALE),
            ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, 0f, -ROW_LIFT_DP * resources.displayMetrics.density),
        ).map { animator ->
            animator.apply {
                duration = FLIP_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }
    }.orEmpty()

    private fun kickRadioAnimation() {
        val ring = findViewById<View>(R.id.guideRadioRingView) ?: return
        val dot = findViewById<View>(R.id.guideRadioDotView) ?: return
        val row = findViewById<View>(R.id.guideMockAppRowView)

        dot.scaleX = 0f
        dot.scaleY = 0f
        dot.alpha = 0f

        val grow = listOf(
            ObjectAnimator.ofFloat(dot, View.SCALE_X, 0f, 1f),
            ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0f, 1f),
            ObjectAnimator.ofFloat(dot, View.ALPHA, 0f, 1f),
        ).map { animator ->
            animator.apply {
                duration = FLIP_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }

        val tint = ValueAnimator.ofObject(ArgbEvaluator(), radioOff(), radioOn()).apply {
            duration = FLIP_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val color = ColorStateList.valueOf(animator.animatedValue as Int)
                ring.backgroundTintList = color
                dot.backgroundTintList = color
            }
        }

        switchAnimator = AnimatorSet().apply {

            startDelay = HOLD_MS
            playTogether(grow + tint + lift(row))
            start()
        }
    }

    private fun radioOff(): Int = getColor(R.color.hint_mock_row_ring)
    private fun radioOn(): Int = getColor(R.color.hint_accent)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        switchAnimator?.cancel()
        switchAnimator = null
        super.onDestroy()
    }

    companion object {

        private const val TAG = "OverlayFlow"
        private const val EXTRA_KIND = "hint_kind"

        fun presentForOverlay(activity: Activity) = show(activity, Kind.OVERLAY)

        fun presentForDefaultHome(activity: Activity) = show(activity, Kind.DEFAULT_HOME)

        fun show(activity: Activity) = show(activity, Kind.DEFAULT_HOME)

        fun presentForDefaultSms(activity: Activity) = show(activity, Kind.DEFAULT_SMS)

        fun show(activity: Activity, kind: Kind) {

            if (kind.isSatisfied(activity)) return

            val handler = Handler(Looper.getMainLooper())
            val startedAt = SystemClock.elapsedRealtime()
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (activity.isFinishing || activity.isDestroyed) return
                    if (kind.isSatisfied(activity)) return

                    val waited = SystemClock.elapsedRealtime() - startedAt
                    if (activity.hasWindowFocus() && waited < GIVE_UP_MS) {
                        handler.postDelayed(this, POLL_MS)
                        return
                    }
                    if (activity.hasWindowFocus()) {
                        Timber.tag(TAG).w("%s screen never came up — no hint", kind)
                        return
                    }
                    handler.postDelayed({ launch(activity, kind) }, SETTLE_MS)
                }
            }, FIRST_CHECK_MS)
        }

        private fun launch(activity: Activity, kind: Kind) {
            if (activity.isFinishing || activity.isDestroyed) return
            if (kind.isSatisfied(activity)) return
            runCatching {

                val hostHint = if (kind == Kind.DEFAULT_HOME) {
                    HomeServiceLocator.bridge.defaultHomeHintIntent(activity)
                } else {
                    null
                }
                activity.startActivity(
                    (hostHint ?: Intent(activity, HomeRoleTipPane::class.java)
                        .putExtra(EXTRA_KIND, kind.name))
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
                Timber.tag(TAG).d("hint shown: %s", kind)
            }.onFailure { Timber.tag(TAG).w(it, "could not show the %s hint", kind) }
        }

        private const val FIRST_CHECK_MS = 250L
        private const val POLL_MS = 150L

        private const val SETTLE_MS = 350L

        private const val GIVE_UP_MS = 6_000L

        private const val CHECK_MS = 300L

        private const val FLIP_MS = 700L
        private const val HOLD_MS = 250L

        private const val TRACK_WIDTH_DP = 52f
        private const val THUMB_SIZE_DP = 26f
        private const val THUMB_INSET_DP = 3f

        private const val ROW_LIFT_SCALE = 1.04f
        private const val ROW_LIFT_DP = 3f
    }
}
