package com.focusguard.app

import android.content.Context
import android.widget.LinearLayout

/** Shown when every goal on the list is ticked: a summary and a revision plan. */
class CompletionSheet : ProgSheet() {

    override val sheetTitle = R.string.done_title

    override fun render(ctx: Context, col: LinearLayout) {
        val prefs = Prefs(ctx)
        val goals = Goals(ctx)
        col.addView(StudyUi.smallText(ctx, getString(R.string.done_sub), 16f, false, true))

        col.addView(Ui.section(ctx, getString(R.string.done_summary)))
        addCard(
            col,
            Ui.row(ctx, getString(R.string.done_focused), null, formatMinutes(prefs.todayMinutes()), null),
            Ui.row(ctx, getString(R.string.done_chapters), null, goals.doneToday().toString(), null)
        )

        col.addView(Ui.section(ctx, getString(R.string.done_plan)))
        val card = Ui.card(ctx)
        card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        card.addView(StudyUi.smallText(ctx, getString(R.string.done_plan_tonight), 14f, false, false))
        val today = Days.key()
        for (step in Revisions.STEPS.filter { it > 0 }) {
            val line = StudyUi.smallText(
                ctx, getString(R.string.done_plan_line, step, Days.label(Days.plus(today, step))), 14f, false, true
            )
            line.setPadding(0, ctx.dp(6), 0, 0)
            card.addView(line)
        }
        card.addView(StudyUi.smallText(ctx, getString(R.string.done_plan_queue), 13f, false, true), StudyUi.topMargin(ctx, 10))
        col.addView(card, Ui.match())

        col.addView(Ui.pillButton(ctx, getString(R.string.done_close)) { dismiss() }, StudyUi.topMargin(ctx, 16))
    }
}
