package com.focusguard.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/** A bottom sheet whose content is built in code. Subclasses fill [render]; call [rebuild] after a change. */
abstract class ProgSheet : BaseSheet(), Refreshable {

    private var column: LinearLayout? = null

    @get:StringRes
    abstract val sheetTitle: Int

    abstract fun render(ctx: android.content.Context, col: LinearLayout)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_sheet)

        val handle = View(ctx)
        handle.setBackgroundResource(R.drawable.bg_handle)
        val hlp = LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(4))
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL
        hlp.topMargin = ctx.dp(12)
        root.addView(handle, hlp)

        val title = TextView(ctx)
        title.setText(sheetTitle)
        title.textSize = 22f
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        title.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        title.setPadding(ctx.dp(20), ctx.dp(16), ctx.dp(20), ctx.dp(4))
        root.addView(title)

        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(24))
        scroll.addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column = col
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    override fun refresh() = rebuild()

    fun rebuild() {
        val col = column ?: return
        if (!isAdded || view == null) return
        col.removeAllViews()
        render(requireContext(), col)
        Accent.apply(col)
    }

    protected fun addCard(col: LinearLayout, vararg rows: View) {
        val card = Ui.cardOf(col.context, rows.toList())
        val lp = Ui.match()
        lp.bottomMargin = col.context.dp(6)
        col.addView(card, lp)
    }

    protected fun lockedToast(ctx: android.content.Context): Boolean {
        if (Prefs(ctx).isLocked()) {
            ctx.toast(R.string.locked_strict)
            return true
        }
        return false
    }
}
