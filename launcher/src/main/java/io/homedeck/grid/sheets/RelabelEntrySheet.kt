package io.homedeck.grid.sheets

import android.app.Activity
import android.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import io.homedeck.grid.databinding.HdkDialogRenameItemBinding
import io.homedeck.grid.ext.homeScreenGridItemsDB
import io.homedeck.grid.entities.DeskGridEntry

class RelabelEntrySheet(val activity: Activity, val item: DeskGridEntry, val callback: () -> Unit) {

    init {
        val binding = HdkDialogRenameItemBinding.inflate(activity.layoutInflater)
        val view = binding.root
        binding.renameItemEdittextView.setText(item.title)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, org.fossify.commons.R.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameItemEdittextView)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.renameItemEdittextView.value
                        if (newTitle.isNotEmpty()) {
                            ensureBackgroundThread {
                                val result = activity.homeScreenGridItemsDB.updateItemTitle(newTitle, item.id!!)
                                if (result == 1) {
                                    callback()
                                    alertDialog.dismiss()
                                } else {
                                    activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                                }
                            }
                        } else {
                            activity.toast(org.fossify.commons.R.string.value_cannot_be_empty)
                        }
                    }
                }
            }
    }
}
