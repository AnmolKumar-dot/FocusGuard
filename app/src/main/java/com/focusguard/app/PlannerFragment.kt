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
            streak.toString() + (if (streak == 1) " day" else " days")
        buildWeek()
        renderList()
    }

    private fun buildWeek() {
        weekStrip.removeAllViews()
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -todayIndex())
        val names = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val green = ContextCompat.getColor(ctx, R.color.green)
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

    private fun renderList() {
        scheduleList.removeAllViews()
        val ctx = requireContext()
        val items = prefs.schedules().filter { it.onDay(selected) }.sortedBy { it.startMin }
        if (items.isEmpty()) {
            val empty = TextView(ctx)
            empty.text = "No focus schedules on this day.\nTap Add Schedule to block distractions automatically."
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
            row.findViewById<TextView>(R.id.schedDays).text = daysSummary(s.days)
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

        fun renderTimes() {
            startTv.text = "Starts  " + formatTime(start)
            endTv.text = "Ends  " + formatTime(end)
        }

        fun renderDays() {
            dayRow.removeAllViews()
            val letters = arrayOf("M", "T", "W", "T", "F", "S", "S")
            for (i in 0..6) {
                val on = (days and (1 shl i)) != 0
                val tv = TextView(ctx)
                tv.text = letters[i]
                tv.gravity = Gravity.CENTER
                tv.textSize = 14f
                tv.setBackgroundResource(if (on) R.drawable.bg_day_chip_on else R.drawable.bg_day_chip_off)
                tv.setTextColor(
                    ContextCompat.getColor(ctx, if (on) R.color.on_green else R.color.text_secondary)
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
            .setTitle(if (existing == null) "New schedule" else "Edit schedule")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                prefs.saveSchedules(prefs.schedules().filter { it.id != existing.id })
                refresh()
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (days == 0) {
                    ctx.toast("Pick at least one day")
                    return@setOnClickListener
                }
                if (start == end) {
                    ctx.toast("Start and end can not be the same")
                    return@setOnClickListener
                }
                val name = nameEt.text.toString().trim().ifEmpty { "Study session" }
                val s = Schedule(existing?.id ?: UUID.randomUUID().toString(), name, start, end, days, existing?.enabled ?: true)
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
