package com.focusguard.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** Small helpers for the screens that are built in code rather than in XML. */
object Ui {

    private fun white(c: Context) = ContextCompat.getColor(c, R.color.text_primary)
    private fun grey(c: Context) = ContextCompat.getColor(c, R.color.text_secondary)

    fun match() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    /** A vertical scrolling page with 16dp padding. Add content to [column]. */
    class Page(val scroll: ScrollView, val column: LinearLayout)

    fun page(c: Context): Page {
        val scroll = ScrollView(c)
        scroll.setBackgroundColor(ContextCompat.getColor(c, R.color.bg))
        scroll.isFillViewport = true
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(c.dp(16), c.dp(16), c.dp(16), c.dp(16))
        scroll.addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Page(scroll, col)
    }

    fun title(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = 28f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(white(c))
        t.setPadding(0, c.dp(8), 0, c.dp(12))
        return t
    }

    fun section(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = 18f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(white(c))
        t.setPadding(0, c.dp(22), 0, c.dp(8))
        return t
    }

    fun note(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = 13f
        t.setTextColor(grey(c))
        t.setPadding(c.dp(4), c.dp(8), c.dp(4), 0)
        return t
    }

    fun card(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.setBackgroundResource(R.drawable.bg_card)
        return l
    }

    fun divider(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(ContextCompat.getColor(c, R.color.divider))
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        return v
    }

    /** Adds [rows] to a new card with dividers between them. */
    fun cardOf(c: Context, rows: List<View>): LinearLayout {
        val card = card(c)
        rows.forEachIndexed { i, r ->
            if (i > 0) card.addView(divider(c))
            card.addView(r)
        }
        return card
    }

    private fun texts(c: Context, title: String, subtitle: String?): LinearLayout {
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val t = TextView(c)
        t.text = title
        t.textSize = 17f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(white(c))
        col.addView(t)
        if (!subtitle.isNullOrEmpty()) {
            val s = TextView(c)
            s.text = subtitle
            s.textSize = 14f
            s.setTextColor(grey(c))
            s.setPadding(0, c.dp(2), 0, 0)
            col.addView(s)
        }
        return col
    }

    private fun rowBase(c: Context): LinearLayout {
        val r = LinearLayout(c)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(c.dp(18), c.dp(16), c.dp(18), c.dp(16))
        return r
    }

    /** A tappable row: title, optional subtitle, optional value on the right and a chevron. */
    fun row(c: Context, title: String, subtitle: String?, value: String?, onClick: (() -> Unit)?): View {
        val r = rowBase(c)
        r.addView(texts(c, title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!value.isNullOrEmpty()) {
            val v = TextView(c)
            v.text = value
            v.textSize = 16f
            v.setTextColor(grey(c))
            v.setPadding(c.dp(8), 0, 0, 0)
            r.addView(v)
        }
        if (onClick != null) {
            val chev = ImageView(c)
            chev.setImageResource(R.drawable.ic_chevron)
            chev.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val lp = LinearLayout.LayoutParams(c.dp(20), c.dp(20))
            lp.marginStart = c.dp(8)
            r.addView(chev, lp)
            r.isClickable = true
            r.isFocusable = true
            r.setBackgroundResource(android.R.drawable.list_selector_background)
            r.setOnClickListener { onClick() }
        }
        return r
    }

    fun switchRow(
        c: Context, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit
    ): View {
        val r = rowBase(c)
        r.addView(texts(c, title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = MaterialSwitch(c)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        r.addView(sw)
        r.setOnClickListener { sw.toggle() }
        return r
    }

    fun pillButton(c: Context, text: String, onClick: () -> Unit): MaterialButton {
        val b = MaterialButton(c)
        b.text = text
        b.isAllCaps = false
        b.textSize = 16f
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(0xFF0D0D0D.toInt())
        b.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFAFAF5.toInt())
        b.cornerRadius = c.dp(26)
        b.setOnClickListener { onClick() }
        return b
    }

    fun text(c: Context, @StringRes id: Int): String = c.getString(id)

    fun gap(c: Context, dpv: Int): View {
        val v = View(c)
        v.layoutParams = LinearLayout.LayoutParams(1, c.dp(dpv))
        return v
    }
}
