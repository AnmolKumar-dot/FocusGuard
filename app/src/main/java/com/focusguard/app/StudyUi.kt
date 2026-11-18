package com.focusguard.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Small building blocks shared by the Today, Goals and Water screens. */
object StudyUi {

    /** A dashboard tile: small label, big value, small line under it. */
    class StatCard(val root: LinearLayout, val value: TextView, val sub: TextView)

    fun statCard(ctx: Context, label: String, value: String, sub: String, onClick: (() -> Unit)?): StatCard {
        val root = Ui.card(ctx)
        root.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        val l = TextView(ctx)
        l.text = label
        l.textSize = 13f
        l.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        root.addView(l)
        val v = TextView(ctx)
        v.text = value
        v.textSize = 28f
        v.typeface = Typeface.DEFAULT_BOLD
        v.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        v.tag = "accent_text"
        v.setPadding(0, ctx.dp(2), 0, 0)
        root.addView(v)
        val s = TextView(ctx)
        s.text = sub
        s.textSize = 12f
        s.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        root.addView(s)
        if (onClick != null) {
            root.isClickable = true
            root.isFocusable = true
            root.setOnClickListener { onClick() }
        }
        return StatCard(root, v, s)
    }

    /** Puts two tiles side by side. */
    fun pair(ctx: Context, a: View, b: View?): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val lpA = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        lpA.marginEnd = ctx.dp(5)
        row.addView(a, lpA)
        val lpB = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        lpB.marginStart = ctx.dp(5)
        row.addView(b ?: View(ctx), lpB)
        return row
    }

    fun secondaryButton(ctx: Context, text: String, onClick: () -> Unit): MaterialButton {
        val b = MaterialButton(ctx)
        b.text = text
        b.isAllCaps = false
        b.textSize = 16f
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        b.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.card_alt))
        b.cornerRadius = ctx.dp(26)
        b.setOnClickListener { onClick() }
        return b
    }

    fun smallText(ctx: Context, text: String, sizeSp: Float, bold: Boolean, grey: Boolean): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = sizeSp
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(ContextCompat.getColor(ctx, if (grey) R.color.text_secondary else R.color.text_primary))
        return t
    }

    /** A card with a heading and a bar chart under it. */
    fun chartCard(ctx: Context, title: String): Pair<LinearLayout, BarChartView> {
        val card = Ui.card(ctx)
        card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(10))
        card.addView(smallText(ctx, title, 15f, true, false))
        val chart = BarChartView(ctx)
        chart.tag = "accent_custom"
        card.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(170)))
        return Pair(card, chart)
    }

    fun weekLabels(): List<String> {
        val f = SimpleDateFormat("EEE", Locale.getDefault())
        return (6 downTo 0).map {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -it)
            f.format(c.time)
        }
    }

    fun pickTime(ctx: Context, minutes: Int, onSet: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onSet(h * 60 + m) }, minutes / 60, minutes % 60, false).show()
    }

    fun topMargin(ctx: Context, dpv: Int): LinearLayout.LayoutParams {
        val lp = Ui.match()
        lp.topMargin = ctx.dp(dpv)
        return lp
    }

    /** "On" when Android is not limiting FocusGuard's battery use, so reminders arrive on time. */
    fun batteryStatus(ctx: Context): String {
        val ok = try {
            (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (e: Exception) {
            false
        }
        return ctx.getString(if (ok) R.string.state_on else R.string.state_off)
    }

    /** Opens the battery-optimisation list so the person can set FocusGuard to "Unrestricted". */
    fun openBatterySettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                ctx.toast(R.string.cant_open_settings)
            }
        }
    }
}
