package com.focusguard.app

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsFragment : Fragment(R.layout.fragment_stats) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())
        prefs.finalizeIfOver()

        val today = prefs.todayMinutes()
        val total = prefs.totalMinutes() + (if (prefs.isSessionActive()) today - prefs.minutesDaysAgo(0) else 0)
        val streak = prefs.streak()

        view.findViewById<TextView>(R.id.statToday).text = formatMinutes(today)
        view.findViewById<TextView>(R.id.statStreak).text = streak.toString() + (if (streak == 1) " day" else " days")
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
            "Focus goal: " + formatMinutes(total) + " / " + formatMinutes(focusGoal)
        view.findViewById<ProgressBar>(R.id.goalFocusBar).progress =
            (total * 100 / focusGoal).coerceAtMost(100)

        val streakGoals = listOf(3, 7, 14, 30, 60, 100)
        val streakGoal = streakGoals.firstOrNull { it > streak } ?: streakGoals.last()
        view.findViewById<TextView>(R.id.goalStreakText).text =
            "Streak goal: $streak / $streakGoal days"
        view.findViewById<ProgressBar>(R.id.goalStreakBar).progress =
            (streak * 100 / streakGoal).coerceAtMost(100)
    }
}
