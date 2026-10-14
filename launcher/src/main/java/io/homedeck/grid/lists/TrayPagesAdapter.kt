package io.homedeck.grid.lists

import android.view.View
import io.homedeck.grid.R
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.homedeck.grid.screens.HomeFoundationPane
import io.homedeck.grid.contracts.InstalledAppsCallback
import io.homedeck.grid.entities.LaunchableApp

class TrayPagesAdapter(
    private val activity: HomeFoundationPane,
    private val listener: InstalledAppsCallback,
    private val adSlot: View?,
    private val itemClick: (Any) -> Unit,
) : RecyclerView.Adapter<TrayPagesAdapter.PageHolder>() {

    private var pages: List<List<LaunchableApp>> = emptyList()
    private var columns = 4
    private var rows = 6

    var firstPageAdapter: LaunchablesAdapter? = null
        private set

    fun submitPages(newPages: List<List<LaunchableApp>>, columnCount: Int, rowCount: Int) {
        pages = newPages
        columns = columnCount
        rows = rowCount.coerceAtLeast(1)
        notifyDataSetChanged()
    }

    override fun getItemCount() = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val grid = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(
                0, parent.resources.getDimensionPixelSize(R.dimen.launcher_drawer_top_gap),
                0, parent.resources.getDimensionPixelSize(R.dimen.launcher_drawer_page_bottom_gap),
            )
            itemAnimator = null
            isNestedScrollingEnabled = false
        }
        return PageHolder(grid, parent)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val isFirst = position == 0
        holder.bind(pages[position], columns, rows, if (isFirst) adSlot else null)
        if (isFirst) firstPageAdapter = holder.adapter
    }

    inner class PageHolder(private val grid: RecyclerView, private val pager: ViewGroup) : RecyclerView.ViewHolder(grid) {
        var adapter: LaunchablesAdapter? = null
            private set
        private var hostsAdSlot = false
        private var rowCount = 6

        init {

            grid.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) grid.post { syncCellHeight(bottom - top) }
            }
        }

        private fun cellHeightFor(pageHeight: Int) =
            if (pageHeight > 0) (pageHeight - grid.paddingTop - grid.paddingBottom) / rowCount else 0

        private fun syncCellHeight(pageHeight: Int) {
            val lineup = adapter ?: return
            val cell = cellHeightFor(pageHeight)
            if (cell <= 0 || lineup.cellHeightPx == cell) return
            lineup.cellHeightPx = cell
            lineup.notifyItemRangeChanged(0, lineup.itemCount)
        }

        fun bind(apps: List<LaunchableApp>, columnCount: Int, rows: Int, slot: View?) {
            rowCount = rows

            val layoutManager = (grid.layoutManager as? GridLayoutManager)
                ?: object : GridLayoutManager(grid.context, columnCount) {
                    override fun canScrollVertically() = false
                }.also { grid.layoutManager = it }
            layoutManager.spanCount = columnCount

            if (adapter == null || hostsAdSlot != (slot != null)) {
                adapter = LaunchablesAdapter(activity, listener, slot, itemClick)
                hostsAdSlot = slot != null
                grid.adapter = adapter
            }
            val lineup = adapter!!
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (lineup.isAdSlotPosition(position)) layoutManager.spanCount else 1
            }

            cellHeightFor(grid.height.takeIf { it > 0 } ?: pager.height).takeIf { it > 0 }?.let { lineup.cellHeightPx = it }
            lineup.submitList(apps.toMutableList())
        }
    }
}
