package com.focusguard.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Today's water: progress ring, +1 glass / undo, the goal, reminder settings and a weekly chart. */
class WaterSheet : ProgSheet() {

    override val sheetTitle = R.string.water_title

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ctx = context ?: return@registerForActivityResult
        if (!granted) ctx.toast(R.string.perm_notif_denied)
        Reminders.scheduleAll(ctx)
        rebuild()
    }

    override fun render(ctx: Context, col: LinearLayout) {
        val w = Water(ctx)
        val have = w.count()

        // ---- ring
        val frame = FrameLayout(ctx)
        val ring = RingView(ctx)
        ring.tag = "accent_custom"
        ring.progress = have.toFloat() / w.goal
        frame.addView(ring, FrameLayout.LayoutParams(ctx.dp(190), ctx.dp(190), Gravity.CENTER))
        val center = LinearLayout(ctx)
        center.orientation = LinearLayout.VERTICAL
        center.gravity = Gravity.CENTER
        val big = StudyUi.smallText(ctx, "$have/${w.goal}", 40f, true, false)
        big.tag = "accent_text"
        center.addView(big)
        center.addView(StudyUi.smallText(ctx, getString(R.string.water_glasses), 14f, false, true))
        center.addView(StudyUi.smallText(ctx, w.formatMl(w.ml(have)) + " / " + w.formatMl(w.ml(w.goal)), 12f, false, true))
        frame.addView(center, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        col.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(210)))

        // ---- buttons
        val buttons = LinearLayout(ctx)
        buttons.orientation = LinearLayout.HORIZONTAL
        val add = Ui.pillButton(ctx, getString(R.string.water_add)) {
            val now = w.add(1)
            if (now == w.goal) ctx.toast(R.string.water_goal_reached)
            rebuild()
        }
        val undo = StudyUi.secondaryButton(ctx, getString(R.string.water_undo)) {
            w.add(-1)
            rebuild()
        }
        val lpAdd = LinearLayout.LayoutParams(0, ctx.dp(52), 2f)
        lpAdd.marginEnd = ctx.dp(8)
        buttons.addView(add, lpAdd)
        buttons.addView(undo, LinearLayout.LayoutParams(0, ctx.dp(52), 1f))
        col.addView(buttons, StudyUi.topMargin(ctx, 12))

        // ---- settings
        col.addView(Ui.section(ctx, getString(R.string.water_settings)))
        val rows = ArrayList<android.view.View>()
        rows.add(
            Ui.row(ctx, getString(R.string.water_goal), getString(R.string.water_goal_sub),
                getString(R.string.water_goal_value, w.goal, w.formatMl(w.ml(w.goal)))) { pickGoal(ctx, w) }
        )
        rows.add(
            Ui.switchRow(ctx, getString(R.string.water_reminders), getString(R.string.water_reminders_sub), w.remindersOn) { on ->
                w.remindersOn = on
                if (on) askPermissionIfNeeded(ctx)
                Reminders.scheduleAll(ctx)
                rebuild()
            }
        )
        if (w.remindersOn) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                rows.add(Ui.row(ctx, getString(R.string.water_allow_notif), getString(R.string.water_allow_notif_sub), null) {
                    askPermissionIfNeeded(ctx)
                })
            }
            rows.add(
                Ui.row(ctx, getString(R.string.water_interval), null, formatMinutes(w.intervalMin)) {
                    ctx.promptNumber(getString(R.string.water_interval), w.intervalMin, 20, 360, getString(R.string.unit_min)) {
                        w.intervalMin = it
                        Reminders.scheduleAll(ctx)
                        rebuild()
                    }
                }
            )
            rows.add(
                Ui.row(ctx, getString(R.string.water_wake), getString(R.string.water_quiet_sub), formatTime(w.wakeMin)) {
                    StudyUi.pickTime(ctx, w.wakeMin) {
                        w.wakeMin = it
                        Reminders.scheduleAll(ctx)
                        rebuild()
                    }
                }
            )
            rows.add(
                Ui.row(ctx, getString(R.string.water_sleep), null, formatTime(w.sleepMin)) {
                    StudyUi.pickTime(ctx, w.sleepMin) {
                        w.sleepMin = it
                        Reminders.scheduleAll(ctx)
                        rebuild()
                    }
                }
            )
            rows.add(
                Ui.row(ctx, getString(R.string.reliable_title), getString(R.string.reliable_sub), StudyUi.batteryStatus(ctx)) { StudyUi.openBatterySettings(ctx) }
            )
        }
        val card = Ui.cardOf(ctx, rows)
        col.addView(card, Ui.match())
        col.addView(Ui.note(ctx, getString(R.string.water_focus_note)))

        // ---- weekly chart
        col.addView(Ui.section(ctx, getString(R.string.water_week)))
        val (chartCard, chart) = StudyUi.chartCard(ctx, getString(R.string.water_week_sub, w.goal))
        chart.valueFormat = { it.toString() }
        chart.setData((6 downTo 0).map { w.count(it) }, StudyUi.weekLabels())
        col.addView(chartCard, Ui.match())
    }

    private fun askPermissionIfNeeded(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The goal can be given in glasses, millilitres or litres; it is stored as whole glasses (250 ml each). */
    private fun pickGoal(ctx: Context, w: Water) {
        val names = arrayOf(getString(R.string.unit_glasses), getString(R.string.unit_ml), getString(R.string.unit_litres))
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.water_goal).setItems(names) { _, which ->
            when (which) {
                0 -> ctx.promptNumber(getString(R.string.water_goal), w.goal, 1, 40, getString(R.string.unit_glasses)) {
                    w.goal = it
                    afterGoal(ctx)
                }
                else -> {
                    val current = if (which == 1) w.ml(w.goal).toString() else (w.ml(w.goal) / 1000.0).toString()
                    ctx.promptText(getString(R.string.water_goal), current, names[which]) { text ->
                        val n = text.replace(',', '.').toDoubleOrNull()
                        if (n == null || n <= 0.0) {
                            ctx.toast(R.string.water_goal_invalid)
                        } else {
                            val ml = if (which == 1) n else n * 1000.0
                            w.goal = Math.round(ml / Water.GLASS_ML).toInt().coerceAtLeast(1)
                            afterGoal(ctx)
                        }
                    }
                }
            }
        }.show()
    }

    private fun afterGoal(ctx: Context) {
        val w = Water(ctx)
        ctx.toast(getString(R.string.water_goal_set, w.goal, w.formatMl(w.ml(w.goal))))
        Reminders.scheduleAll(ctx)
        rebuild()
    }
}
