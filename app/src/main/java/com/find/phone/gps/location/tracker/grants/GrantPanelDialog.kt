package com.find.phone.gps.location.tracker.grants

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Outline
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.grants.panel.PanelArtwork
import com.find.phone.gps.location.tracker.grants.panel.PanelSlide
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.dp
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilSettings
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilRules
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GrantPanelDialog : BottomSheetDialogFragment() {

    var onFinished: (() -> Unit)? = null

    private enum class Status { IDLE, REQUESTING, ALLOWED, DENIED, ALREADY }

    private lateinit var pages: List<PanelSlide>
    private lateinit var statuses: Array<Status>
    private lateinit var pageViews: List<View>
    private val running = mutableMapOf<Int, MutableList<Animator>>()

    private var root: View? = null
    private var pager: ViewPager2? = null
    private var current = 0
    private var requestingIndex = -1
    private var pageAnimator: ValueAnimator? = null
    private val dotAnimators = mutableListOf<ValueAnimator>()

    private var sequenceRunning = false
    private var lastAsked = -1
    private val continueSequence = Runnable { promptNext(after = lastAsked) }

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (perm, granted) -> context?.trackPermissionResult(perm, granted) }
        onRequestResult()
    }

    override fun getTheme(): Int = R.style.Ds_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        val view = inflater.inflate(R.layout.overlay_permission_sheet, container, false)
        root = view

        pages = PanelSlide.all(ctx)
        statuses = Array(pages.size) { if (pages[it].isGranted(ctx)) Status.ALREADY else Status.IDLE }
        current = pages.indices.firstOrNull { !pages[it].isGranted(ctx) } ?: 0
        pageViews = pages.map { composePage(inflater, it) }

        val pager = view.findViewById<ViewPager2>(R.id.pagerVw)
        this.pager = pager
        pager.adapter = PagesAdapter(pageViews)
        pager.offscreenPageLimit = pages.size.coerceAtLeast(1)
        pager.setCurrentItem(current, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = onPageShown(position)
        })

        pager.post { fitPagerHeight() }

        composeDots(view.findViewById(R.id.dotsVw))
        view.findViewById<View>(R.id.padPrimary).apply {
            VisualTweens.pressScale(this)
            setOnClickListener { onPrimaryClicked() }
        }
        view.findViewById<View>(R.id.padSkip).setOnClickListener { onSkipClicked() }

        val basePadding = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = basePadding + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }

        onPageShown(current, animate = false)
        ctx.trackEvent("PermissionSheet_Show")
        return view
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED

            d.window?.setDimAmount(0.55f)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::pages.isInitialized || pages.isEmpty()) return

        val ctx = context ?: return
        statuses.indices.forEach { i ->
            if (statuses[i] == Status.IDLE && pages[i].isGranted(ctx)) statuses[i] = Status.ALREADY
        }
        render(animate = false)
    }

    override fun onDestroyView() {
        root?.removeCallbacks(continueSequence)
        pageAnimator?.cancel()
        dotAnimators.forEach { it.cancel() }
        if (::pageViews.isInitialized) {
            pageViews.forEachIndexed { i, v -> PanelArtwork.stop(pages[i], v, running.getOrPut(i) { mutableListOf() }) }
        }
        pager = null
        root = null
        super.onDestroyView()
    }

    private fun composePage(inflater: LayoutInflater, page: PanelSlide): View {
        val v = inflater.inflate(R.layout.gp_page, null, false)
        v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        v.findViewById<TextView>(R.id.lblTitle).setText(page.title)
        v.findViewById<TextView>(R.id.lblBody).setText(page.body)
        val host = v.findViewById<FrameLayout>(R.id.illHostVw)
        val ill = inflater.inflate(page.illustration, host, false)
        host.addView(ill, 0)

        val radius = v.context.dp(24f)
        host.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        host.clipToOutline = true
        PanelArtwork.bind(page, ill)
        return v
    }

    private fun fitPagerHeight() {
        val pager = pager ?: return
        if (pager.width == 0) return
        val wSpec = View.MeasureSpec.makeMeasureSpec(pager.width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val tallest = pageViews.maxOfOrNull { it.measure(wSpec, hSpec); it.measuredHeight } ?: return
        if (tallest > 0 && pager.layoutParams.height != tallest) {
            pager.layoutParams = pager.layoutParams.apply { height = tallest }
        }
    }

    private class PagesAdapter(private val views: List<View>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) = position
        override fun getItemCount() = views.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(views[viewType]) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }

    private fun onPageShown(position: Int, animate: Boolean = true) {
        if (position !in pages.indices) return
        val ctx = context ?: return

        pageViews.forEachIndexed { i, v ->
            if (i != position) PanelArtwork.stop(pages[i], v, running.getOrPut(i) { mutableListOf() })
        }
        val list = running.getOrPut(position) { mutableListOf() }
        if (list.isEmpty()) list += PanelArtwork.start(pages[position], pageViews[position])

        current = position
        if (statuses[position] == Status.IDLE && pages[position].isGranted(ctx)) statuses[position] = Status.ALREADY
        render(animate)
    }

    private fun goTo(target: Int) {
        val pager = pager ?: return
        if (target == pager.currentItem || target !in pages.indices) return
        pageAnimator?.cancel()
        val ctx = pager.context
        val canFakeDrag = !VisualTweens.animationsDisabled(ctx) &&
            pager.layoutDirection == View.LAYOUT_DIRECTION_LTR && pager.width > 0
        if (!canFakeDrag || !pager.beginFakeDrag()) {
            pager.setCurrentItem(target, !VisualTweens.animationsDisabled(ctx))
            return
        }
        val distance = (target - pager.currentItem) * pager.width.toFloat()
        var dragged = 0f
        pageAnimator = ValueAnimator.ofFloat(0f, distance).apply {
            duration = PAGE_MS
            interpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
            addUpdateListener {
                val v = it.animatedValue as Float
                if (pager.isFakeDragging) pager.fakeDragBy(-(v - dragged))
                dragged = v
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (pager.isFakeDragging) pager.endFakeDrag()
                }
            })
            start()
        }
    }

    private fun needsAsking(index: Int): Boolean {
        val ctx = context ?: return false
        return !pages[index].isGranted(ctx) && !isPagePermanentlyDenied(index)
    }

    private fun nextToAsk(after: Int): Int? = (after + 1 until pages.size).firstOrNull { needsAsking(it) }

    private fun onPrimaryClicked() {
        if (sequenceRunning) return
        if (pages.isEmpty()) { finishFlow(); return }
        sequenceRunning = true
        pager?.isUserInputEnabled = false
        context?.trackEvent("PermissionSheet_AllowAll")
        promptNext(after = -1)
    }

    private fun promptNext(after: Int) {
        root?.removeCallbacks(continueSequence)
        if (!isAdded) return
        val next = nextToAsk(after)
        if (next == null) {
            val ctx = context
            if (ctx != null && pages.all { it.isGranted(ctx) }) ctx.trackEvent("PermissionSheet_AllDone")
            finishFlow()
            return
        }
        lastAsked = next
        if (next == current) {
            request(next)
            return
        }
        goTo(next)

        root?.postDelayed({ if (isAdded && sequenceRunning) request(next) }, PAGE_MS + 80L)
    }

    private fun onSkipClicked() {
        context?.trackEvent("PermissionSheet_Skip")
        finishFlow()
    }

    private fun request(index: Int) {
        val act = activity ?: return
        val page = pages[index]
        statuses[index] = Status.REQUESTING
        requestingIndex = index
        render(animate = true)
        if (page == PanelSlide.NOTIFICATIONS) {

            GrantOrchestrator.request(act, page.key) { if (isAdded) onRequestResult() }
            return
        }
        val vault = SecurePrefsKeeper(act)
        page.permissions.forEach { vault.markPermissionRequested(it) }
        act.trackEvent("PermissionSheet_Ask_${page.key}")
        runCatching { requestPerms.launch(page.permissions) }.onFailure {
            SafeLogger.error(TAG, "Permission request failed for ${page.key}", it)
            onRequestResult()
        }
    }

    private fun onRequestResult() {
        val i = requestingIndex
        requestingIndex = -1
        val ctx = context ?: return
        if (i !in pages.indices) return
        val granted = pages[i].isGranted(ctx)
        statuses[i] = if (granted) Status.ALLOWED else Status.DENIED
        if (i == current) {
            render(animate = true)
            if (granted) popCheck(pageViews[i].findViewById(R.id.checkBadgeVw))
        }

        root?.removeCallbacks(continueSequence)
        root?.postDelayed(continueSequence, ADVANCE_DELAY_MS)
    }

    private fun isPagePermanentlyDenied(index: Int): Boolean {
        val act = activity ?: return false
        val perm = pages[index].primaryPermission ?: return false
        return isPermanentlyDenied(act, pages[index].key, perm)
    }

    private fun render(animate: Boolean) {
        val view = root ?: return
        val ctx = view.context
        if (pages.isEmpty()) return
        val i = current
        val page = pages[i]
        val status = statuses[i]

        view.findViewById<TextView>(R.id.lblCounterCurrent).text = (i + 1).toString()
        view.findViewById<TextView>(R.id.lblCounterTotal).text = getString(R.string.ps_counter_total, pages.size)
        view.findViewById<View>(R.id.padSkip).visibility = if (sequenceRunning) View.INVISIBLE else View.VISIBLE
        paintDots(view.findViewById(R.id.dotsVw), animate)

        pageViews.forEachIndexed { idx, pv ->
            val badge = pv.findViewById<View>(R.id.checkBadgeVw)
            val show = statuses[idx] == Status.ALLOWED || statuses[idx] == Status.ALREADY
            if (!show) badge.visibility = View.INVISIBLE
            else if (badge.visibility != View.VISIBLE) { badge.visibility = View.VISIBLE; badge.scaleX = 1f; badge.scaleY = 1f }
        }

        val name = getString(page.shortName)
        when (status) {
            Status.IDLE, Status.REQUESTING -> presentChip(null)
            Status.ALLOWED -> presentChip(
                Chip.SUCCESS,
                getString(if (nextToAsk(i) != null) R.string.ps_allowed_moving_on else R.string.ps_allowed_done, name),
            )
            Status.DENIED -> presentChip(Chip.WARNING, getString(R.string.ps_not_allowed, name))
            Status.ALREADY -> presentChip(Chip.INFO, getString(R.string.ps_already_allowed))
        }

        paintPrimary(
            label = getString(if (sequenceRunning) R.string.ps_requesting else R.string.ps_allow),
            spinner = sequenceRunning,
            animate = animate && !VisualTweens.animationsDisabled(ctx),
        )
    }

    private enum class Chip(val bg: Int, val icon: Int, val iconTint: Int, val text: Int) {
        SUCCESS(R.drawable.gp_bg_status_success, R.drawable.gp_ic_check, R.color.ps_success, R.color.ps_success_ink),
        WARNING(R.drawable.gp_bg_status_warning, R.drawable.gp_ic_warning, R.color.ps_warning, R.color.ps_warning_ink),
        INFO(R.drawable.gp_bg_status_info, R.drawable.gp_ic_check, R.color.ps_accent_ink, R.color.ps_info_ink),
    }

    private fun presentChip(kind: Chip?, text: String? = null) {
        val view = root ?: return
        val chip = view.findViewById<LinearLayout>(R.id.statusChipVw)
        if (kind == null || text == null) { chip.isVisible = false; return }
        val ctx = view.context
        chip.setBackgroundResource(kind.bg)
        view.findViewById<ImageView>(R.id.picStatus).apply {
            setImageResource(kind.icon)
            imageTintList = ContextCompat.getColorStateList(ctx, kind.iconTint)
        }
        view.findViewById<TextView>(R.id.lblStatus).apply {
            setTextColor(ContextCompat.getColor(ctx, kind.text))
            this.text = text
        }
        if (!chip.isVisible) {
            chip.isVisible = true
            if (!VisualTweens.animationsDisabled(ctx)) {
                chip.alpha = 0f
                chip.translationY = ctx.dp(6f)
                chip.animate().alpha(1f).translationY(0f).setDuration(200L).start()
            }
        }
    }

    private fun paintPrimary(label: String, spinner: Boolean, animate: Boolean) {
        val view = root ?: return
        val btn = view.findViewById<View>(R.id.padPrimary)
        val content = view.findViewById<View>(R.id.primaryContentVw)
        val tv = view.findViewById<TextView>(R.id.lblPrimary)
        val apply = {
            tv.text = label
            view.findViewById<ProgressBar>(R.id.pbPrimaryVw).isVisible = spinner
        }

        btn.isEnabled = !spinner
        btn.alpha = if (spinner) 0.8f else 1f
        if (!animate || tv.text.isNullOrEmpty() || tv.text == label) {
            content.animate().cancel()
            content.alpha = 1f
            apply()
            return
        }

        content.animate().alpha(0f).setDuration(100L).withEndAction {
            apply()
            content.animate().alpha(1f).setDuration(100L).start()
        }.start()
    }

    private fun composeDots(container: LinearLayout) {
        val ctx = container.context
        container.removeAllViews()
        pages.indices.forEach { i ->
            container.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ctx.dp(6f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    if (i > 0) marginStart = ctx.dp(6f).toInt()
                }
            })
        }
    }

    private fun paintDots(container: LinearLayout, animate: Boolean) {
        val ctx = container.context
        val small = ctx.dp(6f).toInt()
        val wide = ctx.dp(22f).toInt()
        dotAnimators.forEach { it.cancel() }
        dotAnimators.clear()
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            if (i == current) {
                dot.setBackgroundResource(R.drawable.gp_bg_gradient_r3)
                dot.backgroundTintList = null
            } else {
                dot.setBackgroundResource(R.drawable.gp_bg_dot)
                dot.backgroundTintList = ContextCompat.getColorStateList(
                    ctx, if (i < current) R.color.ps_dot_past else R.color.ps_dot_future,
                )
            }
            val target = if (i == current) wide else small
            val from = dot.layoutParams.width
            if (from == target) continue
            if (!animate || VisualTweens.animationsDisabled(ctx)) {
                dot.layoutParams = dot.layoutParams.apply { width = target }
                continue
            }
            dotAnimators += ValueAnimator.ofInt(from, target).apply {
                duration = 250L
                interpolator = VisualTweens.EASE
                addUpdateListener { a -> dot.layoutParams = dot.layoutParams.apply { width = a.animatedValue as Int } }
                start()
            }
        }
    }

    private fun popCheck(badge: View) {
        badge.visibility = View.VISIBLE
        if (VisualTweens.animationsDisabled(badge.context)) return
        badge.scaleX = 0f
        badge.scaleY = 0f
        badge.animate().scaleX(1f).scaleY(1f).setDuration(420L).setInterpolator(OvershootInterpolator(3f)).start()
    }

    private fun finishFlow() {
        onFinished?.invoke()
        onFinished = null
        runCatching { dismissAllowingStateLoss() }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)

        onFinished?.invoke()
        onFinished = null
    }

    companion object {
        const val TAG = "permission_sheet"
        private const val ADVANCE_DELAY_MS = 600L
        private const val PAGE_MS = 450L

        fun isShowing(activity: FragmentActivity): Boolean =
            activity.supportFragmentManager.findFragmentByTag(TAG) != null

        fun retireIfShowing(activity: FragmentActivity) {
            val fm = activity.supportFragmentManager
            (fm.findFragmentByTag(TAG) as? GrantPanelDialog)
                ?.let { runCatching { it.dismissAllowingStateLoss() } }
        }

        @JvmStatic
        fun hasPending(activity: FragmentActivity): Boolean =
            PanelSlide.all(activity).any { page ->
                val perm = page.primaryPermission ?: return@any false
                !page.isGranted(activity) && !isPermanentlyDenied(activity, page.key, perm)
            }

        @JvmStatic
        fun isPermanentlyDenied(activity: FragmentActivity, key: String, perm: String): Boolean {
            if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) {
                return false
            }
            val askedAnywhere = GrantLedger(activity).wasAsked(key) ||
                SecurePrefsKeeper(activity).hasRequestedPermission(perm)
            if (!askedAnywhere) return false
            return !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }

        @JvmStatic
        fun shouldAutoShow(activity: FragmentActivity): Boolean {
            if (!hasPending(activity)) return false
            return TourUnveilRules.shouldShowPermissionSheet(activity)
        }

        @JvmStatic
        @JvmOverloads
        fun show(activity: FragmentActivity, onFinished: (() -> Unit)? = null) {
            val fm = activity.supportFragmentManager
            if (fm.isStateSaved || fm.findFragmentByTag(TAG) != null) return

            TourUnveilRules.markShown(activity, TourUnveilSettings.PERMISSION_SHEET)
            GrantPanelDialog().apply { this.onFinished = onFinished }
                .show(fm, TAG)
        }
    }
}
