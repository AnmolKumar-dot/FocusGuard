package com.focusguard.app

import android.content.DialogInterface
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Shared look for the bottom sheets: our own rounded dark background, opens fully expanded. */
open class BaseSheet : BottomSheetDialogFragment() {

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog ?: return
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val sheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.setBackgroundColor(Color.TRANSPARENT)
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Let the screens underneath pick up whatever was changed in the sheet.
        try {
            parentFragmentManager.fragments.filterIsInstance<Refreshable>().forEach { it.refresh() }
        } catch (e: Exception) {
            // the screen is already going away
        }
    }
}
