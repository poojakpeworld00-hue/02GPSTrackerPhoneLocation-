package com.find.phone.gps.location.tracker.screens.finder

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.BsheetLookupFailedBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class LookupFailedSheet(
    activity: Activity,
    private val onRetry: () -> Unit,
    private val onCancel: () -> Unit,
) {
    private val themed = ContextThemeWrapper(activity, R.style.Theme_GpsTracker)
    private val binding = BsheetLookupFailedBinding.inflate(LayoutInflater.from(themed))
    private val dialog = BottomSheetDialog(themed, R.style.Ds_BottomSheetDialog)

    fun show() {
        VisualTweens.pressScale(binding.padRetry)
        binding.padRetry.setOnClickListener {
            dialog.dismiss()
            onRetry()
        }
        binding.padCancel.setOnClickListener {
            dialog.dismiss()
            onCancel()
        }
        dialog.setContentView(binding.root)
        // Hard failure: force Retry or Cancel rather than dismissing onto a stuck loading screen.
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }
}
