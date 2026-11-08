package com.focusguard.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any {
        it.startsWith(context.packageName + "/") && it.endsWith("FocusAccessibilityService")
    }
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** 7200000 ms -> "02:00:00" (rounds partial seconds up, like a countdown). */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

fun formatMinutes(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}

fun formatMs(ms: Long): String = formatMinutes((ms / 60_000L).toInt())

/** Minutes after midnight -> "6:00 PM". */
fun formatTime(min: Int): String {
    val h = min / 60
    val m = min % 60
    val h12 = ((h + 11) % 12) + 1
    return String.format(Locale.US, "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
}

fun daysSummary(ctx: Context, mask: Int): String {
    if (mask == 127) return ctx.getString(R.string.every_day)
    if (mask == 31) return ctx.getString(R.string.mon_fri)
    if (mask == 96) return ctx.getString(R.string.weekends)
    val names = ctx.resources.getStringArray(R.array.day_short)
    return (0..6).filter { (mask and (1 shl it)) != 0 }.joinToString(", ") { names[it] }
}

private fun Context.wheel(min: Int, max: Int, value: Int, labels: Array<String>? = null): NumberPicker {
    val p = NumberPicker(this)
    p.minValue = min
    p.maxValue = max
    if (labels != null) p.displayedValues = labels
    p.value = value.coerceIn(min, max)
    p.wrapSelectorWheel = labels != null && max - min >= 11
    p.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
    return p
}

private fun Context.wheelLabel(text: String): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = 15f
    t.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    t.setPadding(dp(6), 0, dp(14), 0)
    return t
}

/** One scroll wheel, like a modern timer app. */
fun Context.promptNumber(
    title: String, current: Int, min: Int, max: Int, unit: String, onSet: (Int) -> Unit
) {
    val picker = wheel(min, max, current)
    val box = LinearLayout(this)
    box.orientation = LinearLayout.HORIZONTAL
    box.gravity = Gravity.CENTER
    box.setPadding(dp(16), dp(8), dp(16), dp(8))
    box.addView(picker)
    box.addView(wheelLabel(unit))
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(R.string.set_value) { _, _ ->
            picker.clearFocus()
            onSet(picker.value)
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

/**
 * Hours + minutes scroll wheels with quick presets, like a modern timer app.
 * [onSet] receives the total in minutes (at least 1).
 */
fun Context.promptDuration(title: String, currentMin: Int, maxMin: Int, onSet: (Int) -> Unit) {
    val maxHours = (maxMin / 60).coerceAtLeast(0)
    val hours = wheel(0, maxHours, currentMin / 60)
    val minuteLabels = Array(60) { String.format(Locale.US, "%02d", it) }
    val minutes = wheel(0, 59, currentMin % 60, minuteLabels)

    val wheels = LinearLayout(this)
    wheels.orientation = LinearLayout.HORIZONTAL
    wheels.gravity = Gravity.CENTER
    wheels.addView(hours)
    wheels.addView(wheelLabel(getString(R.string.unit_h)))
    wheels.addView(minutes)
    wheels.addView(wheelLabel(getString(R.string.unit_min)))

    val presetRow = LinearLayout(this)
    presetRow.orientation = LinearLayout.HORIZONTAL
    presetRow.setPadding(dp(4), dp(8), dp(4), dp(4))
    for (m in intArrayOf(15, 25, 30, 45, 60, 90, 120, 180)) {
        if (m > maxMin) continue
        val chip = TextView(this)
        chip.text = formatMinutes(m)
        chip.textSize = 14f
        chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        chip.setBackgroundResource(R.drawable.bg_chip)
        chip.setPadding(dp(14), dp(8), dp(14), dp(8))
        chip.setOnClickListener {
            hours.value = m / 60
            minutes.value = m % 60
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = dp(8)
        presetRow.addView(chip, lp)
    }
    val scroll = HorizontalScrollView(this)
    scroll.isHorizontalScrollBarEnabled = false
    scroll.addView(presetRow)

    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(dp(16), dp(8), dp(16), dp(0))
    box.addView(wheels)
    box.addView(scroll)

    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(R.string.set_value) { _, _ ->
            hours.clearFocus()
            minutes.clearFocus()
            val total = hours.value * 60 + minutes.value
            if (total in 1..maxMin) onSet(total) else toast(getString(R.string.pick_valid_duration))
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

fun Context.promptText(title: String, current: String, hint: String, onSet: (String) -> Unit) {
    val input = EditText(this)
    input.inputType = InputType.TYPE_CLASS_TEXT
    input.hint = hint
    input.setText(current)
    input.setSelection(input.text.length)
    val box = FrameLayout(this)
    box.setPadding(dp(24), dp(8), dp(24), 0)
    box.addView(
        input,
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    )
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(R.string.save) { _, _ -> onSet(input.text.toString().trim()) }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

fun Context.toast(@StringRes id: Int) {
    Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
}

fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

fun openNotificationListenerSettings(context: Context) {
    try {
        context.startActivity(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        context.toast(R.string.cant_open_settings)
    }
}

fun Context.daysText(n: Int): String =
    getString(if (n == 1) R.string.day_one else R.string.days_many, n)
