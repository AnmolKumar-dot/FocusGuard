package com.focusguard.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

/** The dashboard: today's numbers as tappable tiles, the revision queue, weekly charts and a tip. */
class TodayFragment : Fragment(), Refreshable {

    private var column: LinearLayout? = null

    @Volatile
    private var loadingScreenTime = false

    // the tile and chart currently on screen, and the latest numbers (7 days, oldest first)
    private var tileNow: StudyUi.StatCard? = null
    private var chartNow: BarChartView? = null
    private var screenResult: List<Int>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val page = Ui.page(requireContext())
        column = page.column
        return page.scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun refresh() {
        val col = column ?: return
        if (!isAdded || view == null) return
        val ctx = requireContext()
        col.removeAllViews()
        build(ctx, col)
        Accent.apply(col)
    }

    private fun go(tab: Int) {
        (activity as? MainActivity)?.select(tab)
    }

    private fun build(ctx: android.content.Context, col: LinearLayout) {
        val prefs = Prefs(ctx)
        prefs.finalizeIfOver()
        val f = Features(ctx)
        val water = Water(ctx)
        val goals = Goals(ctx)

        col.addView(Ui.title(ctx, getString(R.string.t_today)))

        // ---- tiles
        val screen = StudyUi.statCard(ctx, getString(R.string.dash_screen), "—", getString(R.string.dash_loading)) { go(2) }
        val focus = StudyUi.statCard(
            ctx, getString(R.string.dash_focus), formatMinutes(prefs.todayMinutes()),
            getString(R.string.dash_focus_week, formatMinutes(prefs.weekMinutes(0)))
        ) { go(4) }
        val streak = StudyUi.statCard(
            ctx, getString(R.string.dash_streak), ctx.daysText(prefs.streak()), getString(R.string.dash_streak_sub)
        ) { go(4) }

        col.addView(StudyUi.pair(ctx, screen.root, focus.root), tileLp(ctx))

        val goalTile = if (f.goals) {
            val pct = goals.percent()
            StudyUi.statCard(
                ctx, getString(R.string.dash_goals), if (pct == null) "—" else "$pct%",
                if (pct == null) getString(R.string.dash_goals_none)
                else getString(R.string.dash_goals_sub, goals.checked(), goals.total())
            ) { go(6) }.root
        } else null
        val waterTile = if (f.water) {
            val have = water.count()
            StudyUi.statCard(
                ctx, getString(R.string.dash_water), "$have/${water.goal}",
                getString(R.string.dash_water_sub, water.formatMl(water.ml(have)))
            ) { WaterSheet().show(parentFragmentManager, "water") }.root
        } else null
        if (goalTile != null || waterTile != null) {
            col.addView(StudyUi.pair(ctx, goalTile ?: waterTile!!, if (goalTile != null) waterTile else null), tileLp(ctx))
        }
        col.addView(StudyUi.pair(ctx, streak.root, null), tileLp(ctx))

        // ---- revision queue
        if (f.goals) addRevisionQueue(ctx, col)

        // ---- tip
        if (f.tips) {
            val tip = Tips.ofTheDay()
            col.addView(Ui.section(ctx, getString(R.string.dash_tip)))
            val card = Ui.card(ctx)
            card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
            card.addView(StudyUi.smallText(ctx, getString(tip.title), 16f, true, false))
            val body = StudyUi.smallText(ctx, getString(tip.body), 14f, false, true)
            body.setPadding(0, ctx.dp(4), 0, 0)
            card.addView(body)
            card.isClickable = true
            card.setOnClickListener { go(6) }
            col.addView(card, Ui.match())
        }

        // ---- charts
        col.addView(Ui.section(ctx, getString(R.string.dash_week)))
        val (focusCard, focusChart) = StudyUi.chartCard(ctx, getString(R.string.dash_chart_focus))
        val minutes = (6 downTo 0).map { if (it == 0) prefs.todayMinutes() else prefs.minutesDaysAgo(it) }
        focusChart.setData(minutes, StudyUi.weekLabels())
        col.addView(focusCard, Ui.match())

        val (screenCard, screenChart) = StudyUi.chartCard(ctx, getString(R.string.dash_chart_screen))
        col.addView(screenCard, StudyUi.topMargin(ctx, 10))
        loadScreenTime(ctx, screen, screenChart)

        if (f.water) {
            val (waterCard, waterChart) = StudyUi.chartCard(ctx, getString(R.string.dash_chart_water))
            waterChart.valueFormat = { it.toString() }
            waterChart.setData((6 downTo 0).map { water.count(it) }, StudyUi.weekLabels())
            col.addView(waterCard, StudyUi.topMargin(ctx, 10))
        }
        col.addView(Ui.gap(ctx, 8))
    }

    private fun tileLp(ctx: android.content.Context): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = ctx.dp(10)
        return lp
    }

    private fun addRevisionQueue(ctx: android.content.Context, col: LinearLayout) {
        val rev = Revisions(ctx)
        val due = rev.due()
        col.addView(Ui.section(ctx, getString(R.string.dash_revision)))
        if (due.isEmpty()) {
            val upcoming = rev.upcoming()
            val msg = if (upcoming.isEmpty()) getString(R.string.dash_revision_none)
            else getString(R.string.dash_revision_next, Days.label(upcoming.first().dueDay))
            val card = Ui.card(ctx)
            card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
            card.addView(StudyUi.smallText(ctx, msg, 14f, false, true))
            col.addView(card, Ui.match())
            return
        }
        val today = Days.key()
        val rows = ArrayList<View>()
        for (item in due.take(5)) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(12), ctx.dp(12))
            val texts = LinearLayout(ctx)
            texts.orientation = LinearLayout.VERTICAL
            texts.addView(StudyUi.smallText(ctx, item.title, 15f, true, false))
            val step = if (item.step == 0) getString(R.string.rev_step_same) else getString(R.string.rev_step_day, item.step)
            val late = if (item.dueDay < today) "  ·  " + getString(R.string.rev_overdue, Days.label(item.dueDay)) else ""
            texts.addView(StudyUi.smallText(ctx, step + late, 12f, false, true))
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val done = StudyUi.secondaryButton(ctx, getString(R.string.rev_done)) {
                rev.complete(item.id)
                refresh()
            }
            done.textSize = 14f
            row.addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(44)))
            rows.add(row)
        }
        col.addView(Ui.cardOf(ctx, rows), Ui.match())
        if (due.size > 5) col.addView(Ui.note(ctx, getString(R.string.dash_revision_more, due.size - 5)))
    }

    /** Screen time is read off the main thread: it walks a whole day of usage events per day. */
    private fun loadScreenTime(ctx: android.content.Context, tile: StudyUi.StatCard, chart: BarChartView) {
        tileNow = tile
        chartNow = chart
        if (!Usage.hasAccess(ctx)) {
            tile.sub.text = getString(R.string.dash_screen_allow)
            chart.setData(List(7) { 0 }, StudyUi.weekLabels())
            return
        }
        screenResult?.let { showScreenTime(it) }
        if (loadingScreenTime) return
        loadingScreenTime = true
        val app = ctx.applicationContext
        Thread {
            var minutes: List<Int> = emptyList()
            try {
                minutes = (6 downTo 0).map { back ->
                    val key = Days.ago(back)
                    if (back == 0) {
                        (Usage.totalForDay(app, 0) / 60_000L).toInt()
                    } else {
                        synchronized(pastCache) {
                            pastCache.getOrPut(key) { (Usage.totalForDay(app, back) / 60_000L).toInt() }
                        }
                    }
                }
            } catch (e: Exception) {
                // leave the tile as it is
            }
            val result = minutes
            val host = activity
            if (host == null) {
                loadingScreenTime = false
                return@Thread
            }
            host.runOnUiThread {
                loadingScreenTime = false
                if (!isAdded || view == null || result.size != 7) return@runOnUiThread
                screenResult = result
                showScreenTime(result)
            }
        }.start()
    }

    /** Writes the numbers into whichever tile and chart are on screen now. */
    private fun showScreenTime(result: List<Int>) {
        val tile = tileNow ?: return
        val chart = chartNow ?: return
        val today = result[6]
        val yesterday = result[5]
        tile.value.text = formatMinutes(today)
        tile.sub.text = when {
            yesterday <= 0 -> getString(R.string.dash_vs_none)
            today > yesterday -> getString(R.string.dash_vs_up, (today - yesterday) * 100 / yesterday)
            today < yesterday -> getString(R.string.dash_vs_down, (yesterday - today) * 100 / yesterday)
            else -> getString(R.string.dash_vs_same)
        }
        chart.setData(result, StudyUi.weekLabels())
    }

    companion object {
        /** Finished days never change, so their screen time is only worked out once. */
        private val pastCache = HashMap<String, Int>()
    }
}
