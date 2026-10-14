package io.homedeck.grid.lists

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.realScreenSize
import io.homedeck.grid.R
import io.homedeck.grid.sponsor.PaidAppTiles
import com.bumptech.glide.Glide
import io.homedeck.grid.screens.HomeFoundationPane
import io.homedeck.grid.databinding.HdkRowLauncherLabelBinding
import io.homedeck.grid.ext.tweenScale
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.support.GlyphFetcher
import io.homedeck.grid.support.BadgeOutline
import io.homedeck.grid.contracts.InstalledAppsCallback
import io.homedeck.grid.entities.LaunchableApp

class LaunchablesAdapter(
    val activity: HomeFoundationPane,
    val allAppsListener: InstalledAppsCallback,
    private val adSlot: View? = null,
    val itemClick: (Any) -> Unit
) : ListAdapter<LaunchableApp, RecyclerView.ViewHolder>(LaunchableAppDiff()),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private var iconPadding = 0
    private var iconPx = 0

    private val tilePadding = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)

    private var recycler: RecyclerView? = null

    private val gridSizeListener = View.OnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left > 0 && right - left != oldRight - oldLeft) {
            v.post { if (itemCount > 0) notifyItemRangeChanged(0, itemCount) }
        }
    }

    var highlight: String = ""
        set(value) { field = value.trim() }
    private var labelTextSp = 0f

    private val logoPx = activity.resources.getDimensionPixelSize(R.dimen.launcher_icon_cache_size)
    private val logoShape = BadgeOutline(activity.resources, activity.launcherConfig.legacyIconTray)

    var cellHeightPx = 0

    init {
        setHasStableIds(true)
        deriveIconWidth()
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).getLauncherIdentifier().hashCode().toLong()
    }

    override fun getItemViewType(position: Int) =
        if (isAdSlot(getItem(position))) VIEW_TYPE_AD else VIEW_TYPE_LAUNCHER

    fun isAdSlotPosition(position: Int) =
        position in 0 until itemCount && isAdSlot(getItem(position))

    fun fireFirstApp(): Boolean {
        val launcher = currentList.firstOrNull { !isAdSlot(it) } ?: return false
        itemClick(launcher)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_AD) {

            val host = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return AdViewHolder(host)
        }

        val binding = HdkRowLauncherLabelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding.root)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recycler = recyclerView
        recyclerView.addOnLayoutChangeListener(gridSizeListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnLayoutChangeListener(gridSizeListener)
        if (recycler === recyclerView) recycler = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun prewarm(grid: RecyclerView, count: Int) {
        val pool = grid.recycledViewPool
        pool.setMaxRecycledViews(VIEW_TYPE_LAUNCHER, count)
        Looper.myQueue().addIdleHandler {
            if (grid.adapter !== this || pool.getRecycledViewCount(VIEW_TYPE_LAUNCHER) >= count) return@addIdleHandler false
            pool.putRecycledView(createViewHolder(grid, VIEW_TYPE_LAUNCHER))
            true
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AdViewHolder -> holder.wireSlot()
            is ViewHolder -> holder.bindView(getItem(position))
        }
    }

    inner class AdViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun wireSlot() {
            val slot = adSlot ?: return
            val host = itemView as FrameLayout
            if (slot.parent !== host) {
                (slot.parent as? ViewGroup)?.removeView(slot)
                host.addView(
                    slot,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
    }

    override fun submitList(list: MutableList<LaunchableApp>?) {
        deriveIconWidth()
        super.submitList(list)
    }

    private fun deriveIconWidth() {
        labelTextSp = activity.launcherConfig.labelTextSp
        val currentColumnCount = activity.launcherConfig.drawerColumnCount
        val iconWidth = activity.realScreenSize.x / currentColumnCount

        val drawerDp = activity.launcherConfig.drawerIconSizeDp.takeIf { it > 0 } ?: activity.launcherConfig.iconSizeDp
        iconPx = (drawerDp * activity.resources.displayMetrics.density).toInt()

        val padding = 0.5f - 0.35f * activity.launcherConfig.iconScale
        iconPadding = (iconWidth * padding.coerceIn(0f, 0.45f)).toInt()
    }

    private fun padToIconSize(view: View, width: Int) {
        if (width <= 0) return
        val side = ((width - iconPx) / 2).coerceIn(0, (width * 0.45f).toInt())
        val top = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        if (view.paddingLeft != side || view.paddingTop != top || view.paddingRight != side || view.paddingBottom != 0) {
            view.setPadding(side, top, side, 0)
        }
        val height = iconPx + top
        if (view.layoutParams.height != height) {
            view.layoutParams = view.layoutParams.apply { this.height = height }
        }
    }

    private fun squareByWidth(view: View, width: Int) {
        if (width > 0 && view.layoutParams.height != width) {
            view.layoutParams = view.layoutParams.apply { height = width }
        }
    }

    private fun highlightedTitle(title: String): CharSequence {
        if (highlight.isEmpty()) return title
        val start = title.indexOf(highlight, ignoreCase = true)
        if (start < 0) return title
        return SpannableString(title).apply {
            setSpan(ForegroundColorSpan(activity.getColor(R.color.lnch_search_highlight)), start, start + highlight.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private val iconSizeListener = View.OnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft) {
            if (iconPx > 0) padToIconSize(v, right - left) else squareByWidth(v, right - left)
        }
    }

    private fun knownIconWidth(): Int {
        val grid = recycler ?: return 0
        val content = grid.width - grid.paddingLeft - grid.paddingRight
        val spans = (grid.layoutManager as? GridLayoutManager)?.spanCount ?: return 0
        if (content <= 0 || spans <= 0) return 0
        return (content / spans - 2 * tilePadding).coerceAtLeast(0)
    }

    private fun sizeIcon(view: View) {
        val width = knownIconWidth().takeIf { it > 0 } ?: view.width
        if (iconPx > 0) {
            padToIconSize(view, width)
        } else {
            view.setPadding(iconPadding, iconPadding, iconPadding, 0)
            squareByWidth(view, width)
        }
        view.removeOnLayoutChangeListener(iconSizeListener)
        view.addOnLayoutChangeListener(iconSizeListener)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private fun fitToCell() {
            val holder = itemView as? RelativeLayout ?: return
            val height = if (cellHeightPx > 0) cellHeightPx else ViewGroup.LayoutParams.WRAP_CONTENT
            val gravity = if (cellHeightPx > 0) Gravity.CENTER else Gravity.START or Gravity.TOP
            if (holder.gravity != gravity) holder.gravity = gravity
            val params = holder.layoutParams ?: return
            if (params.height != height) holder.layoutParams = params.apply { this.height = height }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bindView(launcher: LaunchableApp): View {
            val binding = HdkRowLauncherLabelBinding.bind(itemView)
            fitToCell()
            itemView.apply {
                val sponsored = PaidAppTiles.isSponsored(launcher)

                binding.launcherLabelView.text =
                    if (sponsored) context.getString(R.string.lnch_sponsored_label, launcher.title)
                    else highlightedTitle(launcher.title)

                binding.launcherLabelView.setTextColor(Color.WHITE)

                labelTextSp.takeIf { it > 0f }?.let { binding.launcherLabelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, it) }
                binding.launcherLabelView.beVisibleIf(activity.launcherConfig.showDrawerAppLabels)
                sizeIcon(binding.launcherIconView)

                val icon = binding.launcherIconView

                Glide.with(activity).clear(icon)
                val ready = if (sponsored) null
                    else launcher.drawable ?: GlyphFetcher.cached(launcher.packageName, launcher.activityName)
                if (sponsored) {

                    icon.tag = null
                    Glide.with(activity)
                        .load(PaidAppTiles.logo(launcher))
                        .override(logoPx)
                        .transform(logoShape)
                        .placeholder(R.drawable.hdk_placeholder_drawable)
                        .error(R.drawable.hdk_placeholder_drawable)
                        .into(icon)
                } else if (ready != null) {

                    launcher.drawable = ready
                    icon.setImageDrawable(ready)
                    icon.tag = null
                } else {

                    val wanted = launcher.packageName + "/" + launcher.activityName
                    icon.tag = wanted
                    icon.setImageDrawable(
                        activity.resources.getColoredDrawableWithColor(
                            drawableId = R.drawable.hdk_placeholder_drawable,
                            color = launcher.thumbnailColor
                        )
                    )
                    GlyphFetcher.load(activity, launcher.packageName, launcher.activityName) { loaded ->
                        if (icon.tag == wanted) {
                            launcher.drawable = loaded
                            icon.setImageDrawable(loaded)
                            icon.tag = null
                        }
                    }
                }

                setOnClickListener { itemClick(launcher) }
                setOnLongClickListener {

                    if (sponsored) return@setOnLongClickListener true
                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    allAppsListener.onAppLauncherLongPressed(
                        x = (location[0] + width / 2).toFloat(),
                        y = location[1].toFloat(),
                        appLauncher = launcher
                    )
                    true
                }

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            binding.launcherIconView.drawable?.alpha = LAUNCHER_ALPHA_PRESSED
                            tweenScale(
                                from = LAUNCHER_SCALE_NORMAL,
                                to = LAUNCHER_SCALE_PRESSED,
                                duration = LAUNCHER_SCALE_UP_DURATION
                            )
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            binding.launcherIconView.drawable?.alpha = LAUNCHER_ALPHA_NORMAL
                            tweenScale(
                                from = LAUNCHER_SCALE_PRESSED,
                                to = LAUNCHER_SCALE_NORMAL,
                                duration = LAUNCHER_SCALE_DOWN_DURATION
                            )
                        }
                    }
                    false
                }
            }

            return itemView
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.getBubbleText() ?: ""

    companion object {
        private const val LAUNCHER_SCALE_NORMAL = 1f
        private const val LAUNCHER_SCALE_PRESSED = 1.15f
        private const val LAUNCHER_SCALE_UP_DURATION = 100L
        private const val LAUNCHER_SCALE_DOWN_DURATION = 50L
        private const val LAUNCHER_ALPHA_NORMAL = 255
        private const val LAUNCHER_ALPHA_PRESSED = 220

        private const val VIEW_TYPE_LAUNCHER = 0
        private const val VIEW_TYPE_AD = 1

        private const val AD_SLOT_PACKAGE = "io.homedeck.grid.ad_slot"

        val AD_SLOT = LaunchableApp(
            id = null,
            title = "",
            packageName = AD_SLOT_PACKAGE,
            activityName = "",
            order = 0,
            thumbnailColor = 0,
            drawable = null
        )

        fun isAdSlot(item: LaunchableApp) = item.packageName == AD_SLOT_PACKAGE
    }
}

class LaunchableAppDiff : DiffUtil.ItemCallback<LaunchableApp>() {
    override fun areItemsTheSame(oldItem: LaunchableApp, newItem: LaunchableApp): Boolean {
        return oldItem.getLauncherIdentifier().hashCode().toLong() ==
                newItem.getLauncherIdentifier().hashCode().toLong()
    }

    override fun areContentsTheSame(oldItem: LaunchableApp, newItem: LaunchableApp): Boolean {

        if (LaunchablesAdapter.isAdSlot(oldItem) || LaunchablesAdapter.isAdSlot(newItem)) {
            return LaunchablesAdapter.isAdSlot(oldItem) && LaunchablesAdapter.isAdSlot(newItem)
        }

        return oldItem.title == newItem.title &&
                oldItem.order == newItem.order &&
                oldItem.thumbnailColor == newItem.thumbnailColor &&
                oldItem.drawable != null &&
                newItem.drawable != null
    }
}
