package com.find.phone.gps.location.tracker.screens.uninstall

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.ViewDataBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.helpers.followAdContainer

/** Shared shell of the funnel screens: insets, the page ad, the interstitial preload and Back. */
abstract class UninstallPage<DB : ViewDataBinding> : FoundationActivity<DB>() {

    protected abstract val page: String

    protected val advanced: Boolean get() = intent.getBooleanExtra(UninstallFlow.EXTRA_ADVANCED, false)

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val frame = binding.root.findViewById<FrameLayout>(R.id.adNativeFrameVw)
        UninstallAds.showPageAd(this, page, frame, binding.root.findViewById<ShimmerFrameLayout>(R.id.adShimmerVw))
        binding.root.findViewById<View>(R.id.adNativeDividerVw).followAdContainer(frame)
        UninstallAds.preloadInter(this)
        // the user asked to uninstall: Back moves the funnel forward (overrides the back-inter callback)
        onBackPressedDispatcher.addCallback(this) { onFunnelBack() }
        initPage()
    }

    protected abstract fun initPage()

    protected abstract fun onFunnelBack()

    protected fun track(event: String) = UninstallFlow.track(this, event, advanced)

    protected fun enterUp(view: View, delay: Long) {
        view.alpha = 0f
        view.translationY = 10f * resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(400)
            .setInterpolator(DecelerateInterpolator()).start()
    }
}
