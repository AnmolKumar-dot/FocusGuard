package com.focusguard.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Screen time today, grouped by category, with the busiest apps and a 7-day chart. */
class UsageFragment : Fragment(), Refreshable {

    private lateinit var column: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 30_000L)
        }
    }

    private class Data(
        val perApp: Map<String, Long>,
        val week: List<Int>,
        val labels: List<String>
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val page = Ui.page(requireContext())
        column = page.column
        return page.scroll
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun refresh() {
        if (!isAdded || view == null) return
        val app = requireContext().applicationContext
        if (!Usage.hasAccess(app)) {
            render(null)
            return
        }
        Thread {
            val perApp = Usage.today(app)
            val week = ArrayList<Int>()
            val labels = ArrayList<String>()
            val fmt = SimpleDateFormat("EEE", Locale.getDefault())
            for (back in 6 downTo 0) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -back)
                labels.add(fmt.format(cal.time))
                week.add((Usage.totalForDay(app, back) / 60_000L).toInt())
            }
            val data = Data(perApp, week, labels)
            handler.post { if (isAdded && view != null) render(data) }
        }.start()
    }

    private fun render(data: Data?) {
        val ctx = requireContext()
        column.removeAllViews()
        column.addView(Ui.title(ctx, getString(R.string.usage_title)))

        if (data == null) {
            val card = Ui.cardOf(
                ctx, listOf(
                    Ui.row(ctx, getString(R.string.usage_access_needed), getString(R.string.usage_access_why), null) {
                        Usage.openAccessSettings(ctx)
                    })
            )
            column.addView(card, Ui.match())
            column.addView(Ui.gap(ctx, 12))
            column.addView(Ui.pillButton(ctx, getString(R.string.usage_grant)) { Usage.openAccessSettings(ctx) })
            addLimitsRow(ctx)
            Accent.apply(column)
            return
        }

        val total = data.perApp.values.sum()
        val byCat = Usage.byCategory(ctx, data.perApp).entries.sortedByDescending { it.value }

        val summary = Ui.card(ctx)
        summary.setPadding(ctx.dp(18), ctx.dp(16), ctx.dp(18), ctx.dp(18))
        val label = TextView(ctx)
        label.text = getString(R.string.usage_today)
        label.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        label.textSize = 13f
        summary.addView(label)
        val big = TextView(ctx)
        big.text = formatMs(total)
        big.textSize = 34f
        big.typeface = android.graphics.Typeface.DEFAULT_BOLD
        big.tag = "accent_text"
        big.setTextColor(Accent.color)
        summary.addView(big)
        val bar = StackBarView(ctx)
        bar.setParts(byCat.map { Pair(it.value, Usage.categoryColor(it.key)) })
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(14))
        blp.topMargin = ctx.dp(12)
        summary.addView(bar, blp)
        column.addView(summary, Ui.match())

        column.addView(Ui.section(ctx, getString(R.string.usage_categories)))
        if (byCat.isEmpty()) {
            column.addView(Ui.note(ctx, getString(R.string.usage_none)))
        } else {
            val rows = byCat.map { (cat, ms) -> dotRow(ctx, Usage.categoryLabel(ctx, cat), formatMs(ms), Usage.categoryColor(cat)) }
            column.addView(Ui.cardOf(ctx, rows), Ui.match())
        }

        column.addView(Ui.section(ctx, getString(R.string.usage_top_apps)))
        val top = data.perApp.entries.sortedByDescending { it.value }.filter { it.value >= 60_000L }.take(8)
        if (top.isEmpty()) {
            column.addView(Ui.note(ctx, getString(R.string.usage_none)))
        } else {
            val rows = top.map { appRow(ctx, it.key, it.value) }
            column.addView(Ui.cardOf(ctx, rows), Ui.match())
        }

        column.addView(Ui.section(ctx, getString(R.string.usage_week)))
        val chartCard = Ui.card(ctx)
        chartCard.setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
        val chart = BarChartView(ctx)
        chart.tag = "accent_custom"
        chart.setData(data.week, data.labels)
        chartCard.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(190)))
        column.addView(chartCard, Ui.match())

        addLimitsRow(ctx)
        Accent.apply(column)
    }

    private fun addLimitsRow(ctx: android.content.Context) {
        column.addView(Ui.gap(ctx, 16))
        val prefs = Prefs(ctx)
        val n = prefs.limits().size
        column.addView(
            Ui.cardOf(
                ctx, listOf(
                    Ui.row(ctx, getString(R.string.limits_title), getString(R.string.limits_row_sub),
                        if (n == 0) null else n.toString()) {
                        LimitsSheet().show(parentFragmentManager, "limits")
                    })
            ), Ui.match()
        )
    }

    private fun dotRow(ctx: android.content.Context, name: String, value: String, color: Int): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = android.view.Gravity.CENTER_VERTICAL
        r.setPadding(ctx.dp(18), ctx.dp(14), ctx.dp(18), ctx.dp(14))
        val dot = View(ctx)
        val shape = android.graphics.drawable.GradientDrawable()
        shape.shape = android.graphics.drawable.GradientDrawable.OVAL
        shape.setColor(color)
        dot.background = shape
        r.addView(dot, LinearLayout.LayoutParams(ctx.dp(12), ctx.dp(12)))
        val n = TextView(ctx)
        n.text = name
        n.textSize = 16f
        n.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        n.setPadding(ctx.dp(12), 0, 0, 0)
        r.addView(n, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = TextView(ctx)
        v.text = value
        v.textSize = 16f
        v.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        r.addView(v)
        return r
    }

    private fun appRow(ctx: android.content.Context, pkg: String, ms: Long): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = android.view.Gravity.CENTER_VERTICAL
        r.setPadding(ctx.dp(18), ctx.dp(10), ctx.dp(18), ctx.dp(10))
        val icon = ImageView(ctx)
        try {
            icon.setImageDrawable(ctx.packageManager.getApplicationIcon(pkg))
        } catch (e: Exception) {
            icon.setImageResource(R.drawable.ic_channel_placeholder)
        }
        r.addView(icon, LinearLayout.LayoutParams(ctx.dp(38), ctx.dp(38)))
        val n = TextView(ctx)
        n.text = Usage.appLabel(ctx, pkg)
        n.textSize = 16f
        n.maxLines = 1
        n.ellipsize = android.text.TextUtils.TruncateAt.END
        n.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        n.setPadding(ctx.dp(14), 0, 0, 0)
        r.addView(n, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = TextView(ctx)
        v.text = formatMs(ms)
        v.textSize = 16f
        v.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        r.addView(v)
        return r
    }
}
