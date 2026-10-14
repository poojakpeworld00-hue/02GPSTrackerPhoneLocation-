package com.find.phone.gps.location.tracker.screens.visual

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.PartPopupMenuBinding
import com.find.phone.gps.location.tracker.databinding.CardPopupRowBinding

class VisualPopup(private val context: Context) {

    private val binding = PartPopupMenuBinding.inflate(LayoutInflater.from(context))
    private val window = PopupWindow(
        binding.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true,
    ).apply {
        elevation = dp(16f)

        setBackgroundDrawable(ContextCompat.getDrawable(context, android.R.color.transparent))
        isOutsideTouchable = true
    }

    fun add(
        label: CharSequence,
        @DrawableRes icon: Int,
        badge: CharSequence? = null,
        selected: Boolean = false,
        onClick: () -> Unit,
    ): VisualPopup {
        val row = CardPopupRowBinding.inflate(LayoutInflater.from(context), binding.rowsVw, false)
        row.lblLabel.text = label
        row.picIcon.setImageResource(icon)
        row.lblBadge.text = badge
        row.lblBadge.isVisible = !badge.isNullOrEmpty()
        row.picCheck.isVisible = selected
        row.iconTileVw.setBackgroundResource(if (selected) R.drawable.vs_tile_primary_r12 else R.drawable.vs_tile_tint_r12)
        row.picIcon.imageTintList = ContextCompat.getColorStateList(context, if (selected) R.color.white else R.color.ds_primary)
        row.root.setOnClickListener {
            window.dismiss()
            onClick()
        }
        binding.rowsVw.addView(row.root)
        return this
    }

    fun show(anchor: View, gravity: Int = Gravity.START) {
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val xOff = if (gravity == Gravity.END) anchor.width - binding.root.measuredWidth else 0
        window.showAsDropDown(anchor, xOff, dp(8f).toInt())
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}
