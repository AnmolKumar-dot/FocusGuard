package com.focusguard.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView

/** A short break after a chapter. Apps you blocked stay blocked until it ends. */
class BreakSheet : ProgSheet() {

    override val sheetTitle = R.string.break_title

    private val handler = Handler(Looper.getMainLooper())
    private var clock: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!isAdded) return
            val left = Prefs(requireContext()).breakUntilMs - System.currentTimeMillis()
            if (left <= 0L) {
                rebuild()
            } else {
                clock?.text = formatClock(left)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(tick)
        super.onDestroyView()
    }

    private fun startBreak(ctx: Context, minutes: Int) {
        Prefs(ctx).startBreak(minutes)
        rebuild()
    }

    override fun render(ctx: Context, col: LinearLayout) {
        val prefs = Prefs(ctx)
        handler.removeCallbacks(tick)
        clock = null

        if (!prefs.breakActive()) {
            col.addView(StudyUi.smallText(ctx, getString(R.string.break_over), 18f, true, false), StudyUi.topMargin(ctx, 8))
            col.addView(StudyUi.smallText(ctx, getString(R.string.break_over_sub), 14f, false, true), StudyUi.topMargin(ctx, 4))
            col.addView(Ui.pillButton(ctx, getString(R.string.break_back)) { dismiss() }, StudyUi.topMargin(ctx, 16))
            col.addView(StudyUi.secondaryButton(ctx, getString(R.string.break_again_5)) { startBreak(ctx, 5) }, StudyUi.topMargin(ctx, 8))
            return
        }

        val big = StudyUi.smallText(ctx, formatClock(prefs.breakUntilMs - System.currentTimeMillis()), 54f, true, false)
        big.tag = "accent_text"
        big.gravity = android.view.Gravity.CENTER
        clock = big
        col.addView(big, StudyUi.topMargin(ctx, 8))
        val note = StudyUi.smallText(ctx, getString(R.string.break_note), 13f, false, true)
        note.gravity = android.view.Gravity.CENTER
        col.addView(note, Ui.match())

        // a quick idea for the break, the same one for the whole break
        val tips = intArrayOf(R.string.break_tip1, R.string.break_tip2, R.string.break_tip3, R.string.break_tip4)
        val pick = ((prefs.breakUntilMs / 60_000L) % tips.size).toInt()
        val card = Ui.card(ctx)
        card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        card.addView(StudyUi.smallText(ctx, getString(tips[pick]), 16f, false, false))
        col.addView(card, StudyUi.topMargin(ctx, 16))

        if (Features(ctx).water) {
            val water = Water(ctx)
            col.addView(
                StudyUi.secondaryButton(ctx, getString(R.string.break_water, water.count(), water.goal)) {
                    water.add(1)
                    rebuild()
                },
                StudyUi.topMargin(ctx, 12)
            )
        }

        val lengths = LinearLayout(ctx)
        lengths.orientation = LinearLayout.HORIZONTAL
        val five = StudyUi.secondaryButton(ctx, getString(R.string.break_5)) { startBreak(ctx, 5) }
        val ten = StudyUi.secondaryButton(ctx, getString(R.string.break_10)) { startBreak(ctx, 10) }
        val lp1 = LinearLayout.LayoutParams(0, ctx.dp(48), 1f)
        lp1.marginEnd = ctx.dp(6)
        lengths.addView(five, lp1)
        val lp2 = LinearLayout.LayoutParams(0, ctx.dp(48), 1f)
        lp2.marginStart = ctx.dp(6)
        lengths.addView(ten, lp2)
        col.addView(lengths, StudyUi.topMargin(ctx, 12))

        col.addView(Ui.pillButton(ctx, getString(R.string.break_end)) {
            prefs.breakUntilMs = 0L
            dismiss()
        }, StudyUi.topMargin(ctx, 12))

        handler.postDelayed(tick, 1000L)
    }
}
