package com.focusguard.app

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The study checklist (subjects, chapters, tasks) and, next to it, the Study Tips. */
class GoalsFragment : Fragment(), Refreshable {

    private var column: LinearLayout? = null
    private var showTips = false

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
        val f = Features(ctx)
        if (!f.tips) showTips = false
        if (!f.goals) showTips = true
        col.addView(Ui.title(ctx, getString(R.string.t_goals)))
        if (f.goals && f.tips) col.addView(segment(ctx))
        if (showTips) buildTips(ctx, col) else buildGoals(ctx, col)
        Accent.apply(col)
    }

    private fun segment(ctx: Context): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.setBackgroundResource(R.drawable.bg_segment)
        box.setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
        fun tab(text: String, selected: Boolean, onClick: () -> Unit): TextView {
            val t = TextView(ctx)
            t.text = text
            t.textSize = 15f
            t.gravity = Gravity.CENTER
            t.setPadding(0, ctx.dp(10), 0, ctx.dp(10))
            t.setTextColor(ContextCompat.getColor(ctx, if (selected) R.color.text_primary else R.color.text_secondary))
            if (selected) t.setBackgroundResource(R.drawable.bg_segment_selected)
            t.setOnClickListener { onClick() }
            return t
        }
        box.addView(tab(getString(R.string.goals_tab), !showTips) { showTips = false; refresh() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(tab(getString(R.string.tips_tab), showTips) { showTips = true; refresh() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val lp = Ui.match()
        lp.bottomMargin = ctx.dp(10)
        box.layoutParams = lp
        return box
    }

    // ------------------------------------------------------------ goals

    private fun buildGoals(ctx: Context, col: LinearLayout) {
        val goals = Goals(ctx)
        val subjects = goals.subjects()

        // ---- progress
        val card = Ui.card(ctx)
        card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        val pct = goals.percent()
        card.addView(StudyUi.smallText(ctx, getString(R.string.goals_progress), 13f, false, true))
        val big = StudyUi.smallText(ctx, if (pct == null) "—" else "$pct%", 34f, true, false)
        big.tag = "accent_text"
        card.addView(big)
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progress = pct ?: 0
        bar.tag = "accent_tint"
        bar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
        card.addView(bar, Ui.match())
        val sub = if (pct == null) getString(R.string.goals_empty) else getString(R.string.dash_goals_sub, goals.checked(), goals.total())
        card.addView(StudyUi.smallText(ctx, sub, 13f, false, true))
        col.addView(card, Ui.match())

        // ---- subjects
        for (s in subjects) addSubject(ctx, col, goals, s)

        col.addView(Ui.pillButton(ctx, getString(R.string.goals_add_subject)) { askSubject(ctx, goals, null) }, StudyUi.topMargin(ctx, 18))

        // ---- list options
        col.addView(Ui.section(ctx, getString(R.string.goals_options)))
        val rows = ArrayList<View>()
        rows.add(
            Ui.switchRow(ctx, getString(R.string.goals_carry), getString(R.string.goals_carry_sub), goals.carryOver) {
                goals.carryOver = it
            }
        )
        rows.add(Ui.row(ctx, getString(R.string.goals_templates), getString(R.string.goals_templates_sub), null) { showTemplates(ctx, goals) })
        col.addView(Ui.cardOf(ctx, rows), Ui.match())
        col.addView(Ui.gap(ctx, 8))
    }

    private fun addSubject(ctx: Context, col: LinearLayout, goals: Goals, s: GSubject) {
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(ctx.dp(4), ctx.dp(20), 0, ctx.dp(6))
        val name = StudyUi.smallText(ctx, s.name, 18f, true, false)
        head.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val done = s.goals.count { it.done }
        head.addView(StudyUi.smallText(ctx, "$done/${s.goals.size}", 14f, false, true))
        val plus = StudyUi.smallText(ctx, "+", 26f, true, false)
        plus.setPadding(ctx.dp(16), 0, ctx.dp(10), 0)
        plus.contentDescription = getString(R.string.goals_add_goal)
        plus.setOnClickListener { goalDialog(ctx, goals, s.id, null) }
        head.addView(plus)
        val more = StudyUi.smallText(ctx, "⋮", 22f, true, false)
        more.setPadding(ctx.dp(10), 0, ctx.dp(10), 0)
        more.contentDescription = getString(R.string.goals_more)
        more.setOnClickListener { subjectMenu(ctx, goals, s) }
        head.addView(more)
        col.addView(head, Ui.match())

        if (s.goals.isEmpty()) {
            val card = Ui.card(ctx)
            card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
            card.addView(StudyUi.smallText(ctx, getString(R.string.goals_subject_empty), 14f, false, true))
            col.addView(card, Ui.match())
            return
        }
        val rows = s.goals.map { goalRow(ctx, goals, s, it) }
        col.addView(Ui.cardOf(ctx, rows), Ui.match())
    }

    private fun goalRow(ctx: Context, goals: Goals, s: GSubject, g: Goal): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(14), ctx.dp(8))

        val cb = CheckBox(ctx)
        cb.isChecked = g.done
        cb.buttonTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Accent.color, 0xFF9A9A9A.toInt())
        )
        cb.setOnClickListener {
            val nowChecked = cb.isChecked
            goals.setDone(g.id, nowChecked)
            refresh()
            if (nowChecked) chapterDone(ctx, goals, g)
        }
        row.addView(cb)

        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        val title = StudyUi.smallText(ctx, g.name, 16f, false, g.done)
        if (g.done) title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        texts.addView(title)
        val bits = ArrayList<String>()
        bits.add(typeLabel(g.type))
        if (g.estMin > 0) bits.add(formatMinutes(g.estMin))
        texts.addView(StudyUi.smallText(ctx, bits.joinToString("  ·  "), 12f, false, true))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.isClickable = true
        row.setOnClickListener { goalDialog(ctx, goals, s.id, g) }
        return row
    }

    private fun typeLabel(type: Int): String = getString(
        when (type) {
            GoalType.PRACTICE -> R.string.type_practice
            GoalType.REVISION -> R.string.type_revision
            else -> R.string.type_concept
        }
    )

    // ------------------------------------------------------------ dialogs

    private fun askSubject(ctx: Context, goals: Goals, existing: GSubject?) {
        ctx.promptText(
            getString(if (existing == null) R.string.goals_add_subject else R.string.goals_rename),
            existing?.name ?: "", getString(R.string.goals_subject_hint)
        ) { text ->
            if (text.isBlank()) {
                ctx.toast(R.string.goals_name_needed)
            } else {
                if (existing == null) goals.addSubject(text) else goals.renameSubject(existing.id, text)
                refresh()
            }
        }
    }

    private fun subjectMenu(ctx: Context, goals: Goals, s: GSubject) {
        val items = arrayOf(
            getString(R.string.goals_add_goal), getString(R.string.goals_rename),
            getString(R.string.goals_move_up), getString(R.string.goals_move_down), getString(R.string.goals_delete)
        )
        MaterialAlertDialogBuilder(ctx).setTitle(s.name).setItems(items) { _, which ->
            when (which) {
                0 -> goalDialog(ctx, goals, s.id, null)
                1 -> askSubject(ctx, goals, s)
                2 -> { goals.moveSubject(s.id, -1); refresh() }
                3 -> { goals.moveSubject(s.id, 1); refresh() }
                4 -> confirmDelete(ctx, s.name) { goals.deleteSubject(s.id); refresh() }
            }
        }.show()
    }

    private fun confirmDelete(ctx: Context, what: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.goals_delete)
            .setMessage(getString(R.string.goals_delete_confirm, what))
            .setPositiveButton(R.string.goals_delete) { _, _ -> onYes() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** Add a chapter/task (existing == null) or edit one: name, type and an optional time estimate. */
    private fun goalDialog(ctx: Context, goals: Goals, subjectId: String, existing: Goal?) {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ctx.dp(24), ctx.dp(12), ctx.dp(24), 0)

        val name = EditText(ctx)
        name.hint = getString(R.string.goals_goal_hint)
        name.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        name.setText(existing?.name ?: "")
        name.setSelection(name.text.length)
        box.addView(name, Ui.match())

        box.addView(StudyUi.smallText(ctx, getString(R.string.goals_type), 13f, false, true), StudyUi.topMargin(ctx, 14))
        val group = RadioGroup(ctx)
        group.orientation = RadioGroup.HORIZONTAL
        val types = listOf(GoalType.CONCEPT, GoalType.PRACTICE, GoalType.REVISION)
        val buttons = types.map { t ->
            val b = RadioButton(ctx)
            b.id = View.generateViewId()
            b.text = typeLabel(t)
            b.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            group.addView(b)
            b
        }
        val currentType = existing?.type ?: GoalType.CONCEPT
        buttons[types.indexOf(currentType).coerceAtLeast(0)].isChecked = true
        box.addView(group, Ui.match())

        val est = EditText(ctx)
        est.hint = getString(R.string.goals_est_hint)
        est.inputType = InputType.TYPE_CLASS_NUMBER
        if (existing != null && existing.estMin > 0) est.setText(existing.estMin.toString())
        box.addView(est, StudyUi.topMargin(ctx, 6))
        Accent.apply(box)

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) R.string.goals_add_goal else R.string.goals_edit)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) {
                    ctx.toast(R.string.goals_name_needed)
                    return@setPositiveButton
                }
                val type = types[buttons.indexOfFirst { it.id == group.checkedRadioButtonId }.coerceAtLeast(0)]
                val minutes = (est.text.toString().trim().toIntOrNull() ?: 0).coerceIn(0, 1440)
                if (existing == null) goals.addGoal(subjectId, n, type, minutes)
                else goals.updateGoal(existing.id, n, type, minutes)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.goals_more) { _, _ -> goalMenu(ctx, goals, existing) }
        }
        builder.show()
    }

    private fun goalMenu(ctx: Context, goals: Goals, g: Goal) {
        val items = arrayOf(getString(R.string.goals_move_up), getString(R.string.goals_move_down), getString(R.string.goals_delete))
        MaterialAlertDialogBuilder(ctx).setTitle(g.name).setItems(items) { _, which ->
            when (which) {
                0 -> { goals.moveGoal(g.id, -1); refresh() }
                1 -> { goals.moveGoal(g.id, 1); refresh() }
                2 -> confirmDelete(ctx, g.name) { goals.deleteGoal(g.id); refresh() }
            }
        }.show()
    }

    private fun showTemplates(ctx: Context, goals: Goals) {
        val templates = goals.templates()
        val items = arrayOf(getString(R.string.goals_template_save)) + templates.map { it.name }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.goals_templates).setItems(items) { _, which ->
            if (which == 0) {
                ctx.promptText(getString(R.string.goals_template_save), "", getString(R.string.goals_template_hint)) { text ->
                    if (text.isBlank()) {
                        ctx.toast(R.string.goals_name_needed)
                    } else {
                        goals.saveTemplate(text)
                        ctx.toast(R.string.goals_template_saved)
                    }
                }
            } else {
                val t = templates[which - 1]
                val actions = arrayOf(getString(R.string.goals_template_use), getString(R.string.goals_delete))
                MaterialAlertDialogBuilder(ctx).setTitle(t.name).setItems(actions) { _, a ->
                    if (a == 0) {
                        goals.applyTemplate(t.name)
                        refresh()
                    } else {
                        goals.deleteTemplate(t.name)
                    }
                }.show()
            }
        }.show()
    }

    // ------------------------------------------------------------ after ticking a chapter

    private fun chapterDone(ctx: Context, goals: Goals, g: Goal) {
        if (goals.allDone()) {
            activity?.supportFragmentManager?.let { CompletionSheet().show(it, "done") }
            return
        }
        var message = getString(R.string.chapter_done_msg)
        if (Features(ctx).tips) message += "\n\n💡 " + getString(Tips.forChapter(ctx).body)
        MaterialAlertDialogBuilder(ctx)
            .setMessage(message)
            .setPositiveButton(R.string.chapter_break) { _, _ ->
                Prefs(ctx).startBreak(5)
                activity?.supportFragmentManager?.let { BreakSheet().show(it, "break") }
            }
            .setNegativeButton(R.string.chapter_continue) { _, _ -> continueWithNext(ctx, goals, g) }
            .show()
    }

    private fun continueWithNext(ctx: Context, goals: Goals, g: Goal) {
        val next = goals.nextUnchecked(g.id)
        if (next == null) {
            ctx.toast(R.string.chapter_no_next)
            return
        }
        val label = next.first.name + " – " + next.second.name
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.chapter_next_title)
            .setMessage(getString(R.string.chapter_next_msg, label))
            .setPositiveButton(R.string.chapter_start) { _, _ ->
                (activity as? MainActivity)?.startFocusFor(next.first.name)
            }
            .setNegativeButton(R.string.chapter_not_now, null)
            .show()
    }

    // ------------------------------------------------------------ tips

    private fun buildTips(ctx: Context, col: LinearLayout) {
        val today = Tips.ofTheDay()
        col.addView(Ui.section(ctx, getString(R.string.dash_tip)))
        col.addView(tipCard(ctx, today, true), Ui.match())

        col.addView(Ui.section(ctx, getString(R.string.tips_schedule_title)))
        val plan = Ui.card(ctx)
        plan.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        plan.addView(StudyUi.smallText(ctx, getString(R.string.tips_schedule_body), 14f, false, true))
        col.addView(plan, Ui.match())

        col.addView(Ui.section(ctx, getString(R.string.tips_all)))
        for (tip in Tips.all) {
            col.addView(tipCard(ctx, tip, false), StudyUi.topMargin(ctx, 8))
        }
        col.addView(Ui.gap(ctx, 8))
    }

    private fun tipCard(ctx: Context, tip: Tip, highlight: Boolean): View {
        val card = Ui.card(ctx)
        card.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        val title = StudyUi.smallText(ctx, getString(tip.title), 16f, true, false)
        if (highlight) title.tag = "accent_text"
        card.addView(title)
        val body = StudyUi.smallText(ctx, getString(tip.body), 14f, false, true)
        body.setPadding(0, ctx.dp(4), 0, 0)
        card.addView(body)
        return card
    }
}
