package com.focusguard.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
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

/** Minutes after midnight -> "6:00 PM". */
fun formatTime(min: Int): String {
    val h = min / 60
    val m = min % 60
    val h12 = ((h + 11) % 12) + 1
    return String.format(Locale.US, "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
}

fun daysSummary(mask: Int): String {
    if (mask == 127) return "Every day"
    if (mask == 31) return "Mon - Fri"
    if (mask == 96) return "Weekends"
    val names = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return (0..6).filter { (mask and (1 shl it)) != 0 }.joinToString(", ") { names[it] }
}

fun Context.promptNumber(
    title: String, current: Int, min: Int, max: Int, unit: String, onSet: (Int) -> Unit
) {
    val input = EditText(this)
    input.inputType = InputType.TYPE_CLASS_NUMBER
    input.hint = unit
    input.setText(current.toString())
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
        .setPositiveButton("Save") { _, _ ->
            val v = input.text.toString().toIntOrNull()
            if (v != null && v in min..max) onSet(v) else toast("Enter a number from $min to $max")
        }
        .setNegativeButton("Cancel", null)
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
        .setPositiveButton("Save") { _, _ -> onSet(input.text.toString().trim()) }
        .setNegativeButton("Cancel", null)
        .show()
}
