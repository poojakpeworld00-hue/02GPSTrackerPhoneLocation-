package io.homedeck.grid.lists

import io.homedeck.grid.support.GlyphFetcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.homedeck.grid.R
import io.homedeck.grid.entities.LaunchableApp

/**
 * Minimal launcher list used by the left panel, one instance per section. The row layouts all
 * share the same two ids, so a single adapter covers grids and lists alike.
 */
class PaneAppsAdapter(
    private val layoutRes: Int,
    private val itemClick: (LaunchableApp) -> Unit,
) : ListAdapter<LaunchableApp, PaneAppsAdapter.ViewHolder>(LaunchableAppDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(launcher: LaunchableApp) {
            itemView.findViewById<TextView>(R.id.item_titleView).text = launcher.title
            // the icons are already loaded in memory by the drawer, no need for Glide here; one that
            // is not yet (a cold start) is decoded on its own instead of waiting for the full scan
            val icon = itemView.findViewById<ImageView>(R.id.item_iconView)
            val ready = launcher.drawable ?: GlyphFetcher.cached(launcher.packageName, launcher.activityName)
            icon.setImageDrawable(ready)
            if (ready == null) {
                val wanted = launcher.packageName + "/" + launcher.activityName
                icon.tag = wanted
                GlyphFetcher.load(itemView.context, launcher.packageName, launcher.activityName) { loaded ->
                    if (icon.tag == wanted) icon.setImageDrawable(loaded)
                }
            } else {
                icon.tag = null
            }
            itemView.setOnClickListener { itemClick(launcher) }
        }
    }
}
