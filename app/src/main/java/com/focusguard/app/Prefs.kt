package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** All settings, the running session and focus stats live here, on the phone only. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("focusguard", Context.MODE_PRIVATE)

    var sessionStartMs: Long
        get() = sp.getLong("sessionStart", 0L)
        set(v) = sp.edit().putLong("sessionStart", v).apply()

    var sessionEndMs: Long
        get() = sp.getLong("sessionEnd", 0L)
        set(v) = sp.edit().putLong("sessionEnd", v).apply()

    /** Strict flag captured when the session started, so it can't be toggled mid-session. */
    var sessionStrict: Boolean
        get() = sp.getBoolean("sessionStrict", false)
        set(v) = sp.edit().putBoolean("sessionStrict", v).apply()

    var strict: Boolean
        get() = sp.getBoolean("strict", false)
        set(v) = sp.edit().putBoolean("strict", v).apply()

    var blockReels: Boolean
        get() = sp.getBoolean("blockReels", true)
        set(v) = sp.edit().putBoolean("blockReels", v).apply()

    var blockShorts: Boolean
        get() = sp.getBoolean("blockShorts", true)
        set(v) = sp.edit().putBoolean("blockShorts", v).apply()

    var allowlistOnly: Boolean
        get() = sp.getBoolean("allowlistOnly", false)
        set(v) = sp.edit().putBoolean("allowlistOnly", v).apply()

    var studyChannels: String
        get() = sp.getString("studyChannels", "") ?: ""
        set(v) = sp.edit().putString("studyChannels", v).apply()

    var blockedPackages: Set<String>
        get() = HashSet(sp.getStringSet("blockedPackages", emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet("blockedPackages", HashSet(v)).apply()

    fun allowedChannels(): List<String> =
        studyChannels.split(",", "\n").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    // ---- sessions ----

    fun isSessionActive(): Boolean {
        finalizeIfOver()
        return sessionEndMs > System.currentTimeMillis()
    }

    /** Called from the app and the service; credits the minutes once the timer has run out. */
    fun finalizeIfOver() {
        val end = sessionEndMs
        if (end != 0L && end <= System.currentTimeMillis()) {
            addMinutes(((end - sessionStartMs) / 60_000L).toInt(), end)
            sessionStartMs = 0L
            sessionEndMs = 0L
        }
    }

    /** Stop early (only reachable when the session isn't strict). Credits the time done so far. */
    fun stopSession() {
        val now = System.currentTimeMillis()
        if (sessionEndMs > now) {
            addMinutes(((now - sessionStartMs) / 60_000L).toInt(), now)
        }
        sessionStartMs = 0L
        sessionEndMs = 0L
    }

    // ---- stats ----

    private fun dayKey(ms: Long): String =
        "min_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    private fun addMinutes(minutes: Int, atMs: Long) {
        if (minutes <= 0) return
        val key = dayKey(atMs)
        sp.edit().putInt(key, sp.getInt(key, 0) + minutes).apply()
    }

    private fun minutesOn(ms: Long): Int = sp.getInt(dayKey(ms), 0)

    fun todayMinutes(): Int = minutesOn(System.currentTimeMillis())

    /** Consecutive days with at least one focused minute; today doesn't break it if still empty. */
    fun streak(): Int {
        val cal = Calendar.getInstance()
        if (minutesOn(cal.timeInMillis) == 0) cal.add(Calendar.DAY_OF_YEAR, -1)
        var days = 0
        while (minutesOn(cal.timeInMillis) > 0) {
            days++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return days
    }
}
