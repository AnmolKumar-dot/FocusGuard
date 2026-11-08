package com.focusguard.app

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Repeating focus windows. While one is running, blocking is on without starting a session. */
class PlannerFragment : Fragment(R.layout.fragment_planner), Refreshable {

    private lateinit var prefs: Prefs
    private lateinit var weekStrip: LinearLayout
    private lateinit var scheduleList: LinearLayout
    private var selected = todayIndex()

    private fun todayIndex(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        weekStrip = view.findViewById(R.id.weekStrip)
        scheduleList = view.findViewById(R.id.scheduleList)
        view.findViewById<TextView>(R.id.monthText).text =
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        view.findViewById<View>(R.id.btnAdd).setOnClickListener { showDialog(null) }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun refresh() {
        if (!isAdded || view == null) return
        requireView().findViewById<TextView>(R.id.plannerFocus).text = formatMinutes(prefs.todayMinutes())
        val streak = prefs.streak()
        requireView().findViewById<TextView>(R.id.plannerStreak).text =
            requireContext().daysText(streak)
        buildWeek()
        renderList()
    }

    private fun buildWeek() {
        weekStrip.removeAllViews()
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -todayIndex())
        val names = resources.getStringArray(R.array.day_short)
        val green = Accent.color
        val grey = ContextCompat.getColor(ctx, R.color.text_secondary)
        val white = ContextCompat.getColor(ctx, R.color.text_primary)
        for (i in 0..6) {
            val cell = LinearLayout(ctx)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(0, ctx.dp(10), 0, ctx.dp(10))
            if (i == selected) cell.setBackgroundResource(R.drawable.bg_day_selected)

            val name = TextView(ctx)
            name.text = names[i]
            name.textSize = 13f
            name.setTextColor(if (i == selected) green else grey)

            val date = TextView(ctx)
            date.text = cal.get(Calendar.DAY_OF_MONTH).toString()
            date.textSize = 18f
            date.setTextColor(if (i == selected) green else white)

            cell.addView(name)
            cell.addView(date)
            cell.setOnClickListener {
                selected = i
                buildWeek()
                renderList()
            }
            weekStrip.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun bedtimeCard(): View {
        val ctx = requireContext()
        val sw = Ui.switchRow(
            ctx, getString(R.string.bedtime_title),
            getString(R.string.bedtime_sub, formatTime(prefs.bedtimeStart), formatTime(prefs.bedtimeEnd)),
            prefs.bedtimeOn
        ) { on ->
            if (on) {
                prefs.ensureProfile("sleep", getString(R.string.profile_sleep)) {
                    it.allowMode = true
                    it.notifMode = Prefs.NOTIF_ALL
                }
            }
            prefs.bedtimeOn = on
            renderList()
        }
        val rows = ArrayList<View>()
        rows.add(sw)
        if (prefs.bedtimeOn) {
            rows.add(Ui.row(ctx, getString(R.string.bedtime_from), null, formatTime(prefs.bedtimeStart)) {
                TimePickerDialog(ctx, { _, h, m -> prefs.bedtimeStart = h * 60 + m; renderList() },
                    prefs.bedtimeStart / 60, prefs.bedtimeStart % 60, false).show()
            })
            rows.add(Ui.row(ctx, getString(R.string.bedtime_until), null, formatTime(prefs.bedtimeEnd)) {
                TimePickerDialog(ctx, { _, h, m -> prefs.bedtimeEnd = h * 60 + m; renderList() },
                    prefs.bedtimeEnd / 60, prefs.bedtimeEnd % 60, false).show()
            })
            rows.add(Ui.row(ctx, getString(R.string.bedtime_apps), getString(R.string.bedtime_apps_sub), null) {
                AppPickerSheet.newInstance(AppPickerSheet.MODE_ALLOW, "sleep").show(parentFragmentManager, "apps")
            })
        }
        val card = Ui.cardOf(ctx, rows)
        val lp = Ui.match()
        lp.bottomMargin = ctx.dp(12)
        card.layoutParams = lp
        Accent.apply(card)
        return card
    }

    private fun renderList() {
        scheduleList.removeAllViews()
        val ctx = requireContext()
        scheduleList.addView(bedtimeCard())
        val items = prefs.schedules().filter { it.onDay(selected) }.sortedBy { it.startMin }
        if (items.isEmpty()) {
            val empty = TextView(ctx)
            empty.text = getString(R.string.planner_empty)
            empty.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            empty.gravity = Gravity.CENTER
            empty.setPadding(ctx.dp(16), ctx.dp(40), ctx.dp(16), 0)
            scheduleList.addView(empty)
            return
        }
        for (s in items) {
            val row = layoutInflater.inflate(R.layout.item_schedule, scheduleList, false)
            row.findViewById<TextView>(R.id.schedEmoji).text = emojiFor(s.startMin)
            row.findViewById<TextView>(R.id.schedName).text = s.name
            row.findViewById<TextView>(R.id.schedTime).text =
                formatTime(s.startMin) + " - " + formatTime(s.endMin)
            row.findViewById<TextView>(R.id.schedDays).text = daysSummary(ctx, s.days)
            val sw = row.findViewById<MaterialSwitch>(R.id.schedSwitch)
            sw.isChecked = s.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                prefs.saveSchedules(prefs.schedules().map { if (it.id == s.id) it.copy(enabled = checked) else it })
            }
            row.setOnClickListener { showDialog(s) }
            scheduleList.addView(row)
        }
    }

    private fun emojiFor(startMin: Int): String {
        val h = startMin / 60
        return when {
            h < 5 -> "🌙"
            h < 12 -> "🌅"
            h < 17 -> "☀️"
            h < 21 -> "🌆"
            else -> "🌙"
        }
    }

    private fun showDialog(existing: Schedule?) {
        val ctx = requireContext()
        val v = layoutInflater.inflate(R.layout.dialog_schedule, null)
        val nameEt = v.findViewById<EditText>(R.id.etName)
        val startTv = v.findViewById<TextView>(R.id.tvStart)
        val endTv = v.findViewById<TextView>(R.id.tvEnd)
        val dayRow = v.findViewById<LinearLayout>(R.id.dayRow)

        var start = existing?.startMin ?: (18 * 60)
        var end = existing?.endMin ?: (20 * 60)
        var days = existing?.days ?: 31
        nameEt.setText(existing?.name ?: "")
        var profileId = existing?.profile ?: ""
        val profileTv = TextView(ctx)
        profileTv.setBackgroundResource(R.drawable.bg_input)
        profileTv.setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(14))
        profileTv.textSize = 15f
        profileTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        fun renderProfile() {
            val name = if (profileId.isEmpty()) ctx.getString(R.string.profile_current)
            else prefs.profileName(profileId)
            profileTv.text = ctx.getString(R.string.sched_profile, name)
        }
        profileTv.setOnClickListener {
            val list = prefs.profiles()
            val names = (listOf(ctx.getString(R.string.profile_current)) + list.map { it.name }).toTypedArray()
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.sched_profile_title)
                .setItems(names) { _, which ->
                    profileId = if (which == 0) "" else list[which - 1].id
                    renderProfile()
                }.show()
        }
        renderProfile()
        (v as LinearLayout).addView(
            profileTv,
            LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ctx.dp(16) }
        )

        fun renderTimes() {
            startTv.text = ctx.getString(R.string.sched_starts, formatTime(start))
            endTv.text = ctx.getString(R.string.sched_ends, formatTime(end))
        }

        fun renderDays() {
            dayRow.removeAllViews()
            val letters = ctx.resources.getStringArray(R.array.day_letter)
            for (i in 0..6) {
                val on = (days and (1 shl i)) != 0
                val tv = TextView(ctx)
                tv.text = letters[i]
                tv.gravity = Gravity.CENTER
                tv.textSize = 14f
                tv.setBackgroundResource(if (on) R.drawable.bg_day_chip_on else R.drawable.bg_day_chip_off)
                tv.setTextColor(
                    ContextCompat.getColor(ctx, if (on) R.color.on_accent else R.color.text_secondary)
                )
                tv.setOnClickListener {
                    days = days xor (1 shl i)
                    renderDays()
                }
                val lp = LinearLayout.LayoutParams(0, ctx.dp(38), 1f)
                if (i < 6) lp.marginEnd = ctx.dp(6)
                dayRow.addView(tv, lp)
            }
        }

        startTv.setOnClickListener {
            TimePickerDialog(ctx, { _, h, m ->
                start = h * 60 + m
                renderTimes()
            }, start / 60, start % 60, false).show()
        }
        endTv.setOnClickListener {
            TimePickerDialog(ctx, { _, h, m ->
                end = h * 60 + m
                renderTimes()
            }, end / 60, end % 60, false).show()
        }
        renderTimes()
        renderDays()

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) R.string.sched_new else R.string.sched_edit)
            .setView(v)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                prefs.saveSchedules(prefs.schedules().filter { it.id != existing.id })
                refresh()
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (days == 0) {
                    ctx.toast(R.string.pick_day)
                    return@setOnClickListener
                }
                if (start == end) {
                    ctx.toast(R.string.start_end_same)
                    return@setOnClickListener
                }
                val name = nameEt.text.toString().trim().ifEmpty { ctx.getString(R.string.sched_default_name) }
                val s = Schedule(existing?.id ?: UUID.randomUUID().toString(), name, start, end, days, existing?.enabled ?: true, profileId)
                val list = prefs.schedules().toMutableList()
                val idx = list.indexOfFirst { it.id == s.id }
                if (idx >= 0) list[idx] = s else list.add(s)
                prefs.saveSchedules(list)
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }
}
