package com.focusguard.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsFragment : Fragment(R.layout.fragment_stats) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val prefs = Prefs(ctx)
        prefs.finalizeIfOver()

        val today = prefs.todayMinutes()
        val total = prefs.totalMinutes() + (if (prefs.isSessionActive()) today - prefs.minutesDaysAgo(0) else 0)
        val streak = prefs.streak()

        view.findViewById<TextView>(R.id.statToday).text = formatMinutes(today)
        view.findViewById<TextView>(R.id.statStreak).text = ctx.daysText(streak)
        view.findViewById<TextView>(R.id.statTotal).text = formatMinutes(total)

        val values = ArrayList<Int>()
        val labels = ArrayList<String>()
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        for (back in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -back)
            values.add(if (back == 0) today else prefs.minutesDaysAgo(back))
            labels.add(fmt.format(cal.time))
        }
        view.findViewById<BarChartView>(R.id.chart).setData(values, labels)

        val focusGoals = listOf(60, 300, 600, 1500, 3000, 6000)
        val focusGoal = focusGoals.firstOrNull { it > total } ?: focusGoals.last()
        view.findViewById<TextView>(R.id.goalFocusText).text =
            getString(R.string.goal_focus, formatMinutes(total), formatMinutes(focusGoal))
        view.findViewById<ProgressBar>(R.id.goalFocusBar).progress =
            (total * 100 / focusGoal).coerceAtMost(100)

        val streakGoals = listOf(3, 7, 14, 30, 60, 100)
        val streakGoal = streakGoals.firstOrNull { it > streak } ?: streakGoals.last()
        view.findViewById<TextView>(R.id.goalStreakText).text = getString(R.string.goal_streak, streak, streakGoal)
        view.findViewById<ProgressBar>(R.id.goalStreakBar).progress =
            (streak * 100 / streakGoal).coerceAtMost(100)

        buildReport(ctx, prefs, view.findViewById(R.id.statsExtra))
        Accent.apply(view)
    }

    private fun card(ctx: android.content.Context): LinearLayout {
        val c = Ui.card(ctx)
        c.setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16))
        val lp = Ui.match()
        lp.topMargin = ctx.dp(14)
        c.layoutParams = lp
        return c
    }

    private fun text(ctx: android.content.Context, t: String, size: Float, bold: Boolean, grey: Boolean): TextView {
        val v = TextView(ctx)
        v.text = t
        v.textSize = size
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        v.setTextColor(ContextCompat.getColor(ctx, if (grey) R.color.text_secondary else R.color.text_primary))
        return v
    }

    private fun buildReport(ctx: android.content.Context, prefs: Prefs, box: LinearLayout) {
        box.removeAllViews()

        // ---- focus score
        val score = prefs.focusScore()
        val scoreCard = card(ctx)
        scoreCard.addView(text(ctx, getString(R.string.score_title), 17f, true, false))
        val big = text(ctx, score.toString() + " / 100", 34f, true, false)
        big.tag = "accent_text"
        big.setTextColor(Accent.color)
        scoreCard.addView(big)
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progress = score
        bar.tag = "accent_tint"
        bar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
        scoreCard.addView(bar, Ui.match())
        scoreCard.addView(text(ctx, getString(R.string.score_help), 13f, false, true))
        box.addView(scoreCard)

        // ---- weekly report
        val thisWeek = prefs.weekMinutes(0)
        val lastWeek = prefs.weekMinutes(7)
        var best = 0
        var bestBack = 0
        var blocks = 0
        for (i in 0..6) {
            val m = if (i == 0) prefs.todayMinutes() else prefs.minutesDaysAgo(i)
            if (m > best) { best = m; bestBack = i }
            blocks += prefs.blocksDaysAgo(i)
        }
        val since = System.currentTimeMillis() - 7 * 86_400_000L
        val sessions = prefs.log().filter { it.startMs >= since }
        val report = card(ctx)
        report.addView(text(ctx, getString(R.string.report_title), 17f, true, false))
        val diff = thisWeek - lastWeek
        val trend = when {
            lastWeek == 0 && thisWeek == 0 -> getString(R.string.report_trend_none)
            diff >= 0 -> getString(R.string.report_trend_up, formatMinutes(diff))
            else -> getString(R.string.report_trend_down, formatMinutes(-diff))
        }
        val lines = ArrayList<String>()
        lines.add(getString(R.string.report_total, formatMinutes(thisWeek), formatMinutes(prefs.weeklyGoalMin)))
        lines.add(trend)
        if (best > 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -bestBack)
            lines.add(
                getString(
                    R.string.report_best, SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time),
                    formatMinutes(best)
                )
            )
        }
        lines.add(getString(R.string.report_sessions, sessions.size))
        lines.add(getString(R.string.report_blocks, blocks))
        for (l in lines) {
            val t = text(ctx, l, 14f, false, true)
            t.setPadding(0, ctx.dp(6), 0, 0)
            report.addView(t)
        }

        // subjects
        val bySubject = sessions.filter { it.subject.isNotEmpty() }.groupBy { it.subject }
            .mapValues { e -> e.value.sumOf { it.minutes } }.entries.sortedByDescending { it.value }
        if (bySubject.isNotEmpty()) {
            val h = text(ctx, getString(R.string.report_subjects), 15f, true, false)
            h.setPadding(0, ctx.dp(14), 0, 0)
            report.addView(h)
            val max = bySubject.first().value.coerceAtLeast(1)
            for ((name, minutes) in bySubject.take(6)) {
                val t = text(ctx, name + "  ·  " + formatMinutes(minutes), 14f, false, false)
                t.setPadding(0, ctx.dp(8), 0, 0)
                report.addView(t)
                val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
                pb.max = 100
                pb.progress = minutes * 100 / max
                pb.tag = "accent_tint"
                pb.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
                report.addView(pb, Ui.match())
            }
        }

        val share = Ui.pillButton(ctx, getString(R.string.report_share)) {
            val body = getString(R.string.report_share_body, score, lines.joinToString("\n"))
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
            startActivity(Intent.createChooser(send, getString(R.string.report_share)))
        }
        val lp = Ui.match()
        lp.topMargin = ctx.dp(16)
        report.addView(share, lp)
        box.addView(report)
        val pad = View(ctx)
        box.addView(pad, ViewGroup.LayoutParams(1, ctx.dp(8)))
    }
}
