package com.find.phone.gps.location.tracker.screens.dashboard

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.BsheetInvalidNumberBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class BadNumberSheet(
    activity: Activity,
    private val number: String,
    private val country: String,
    private val onEdit: () -> Unit,
    private val onChangeCountry: () -> Unit,
) {
    private val themed = ContextThemeWrapper(activity, R.style.Theme_GpsTracker)
    private val binding = BsheetInvalidNumberBinding.inflate(LayoutInflater.from(themed))
    private val dialog = BottomSheetDialog(themed, R.style.Ds_BottomSheetDialog)

    fun show() {
        binding.lblBody.text = themed.getString(R.string.ds_invalid_body, number.trim(), country)
        VisualTweens.pressScale(binding.padEdit)
        binding.padEdit.setOnClickListener {
            dialog.dismiss()
            onEdit()
        }
        binding.padCountry.setOnClickListener {
            dialog.dismiss()
            onChangeCountry()
        }
        dialog.setContentView(binding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }
}
