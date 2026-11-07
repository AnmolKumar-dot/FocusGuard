package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Everything is stored on the phone only: settings, the running session, schedules and stats. */
class Prefs(context: Context) {

    companion object {
        const val MODE_TIMER = 0
        const val MODE_POMODORO = 1

        const val YT_ALLOW = 0
        const val YT_BLOCK = 1
        const val YT_STUDY = 2
    }

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("focusguard", Context.MODE_PRIVATE)

    // ---- what the next session will look like ----

    var planMode: Int
        get() = sp.getInt("planMode", MODE_TIMER)
        set(v) = sp.edit().putInt("planMode", v).apply()

    var timerMin: Int
        get() = sp.getInt("timerMin", 60)
        set(v) = sp.edit().putInt("timerMin", v).apply()

    var pomoFocusMin: Int
        get() = sp.getInt("pomoFocusMin", 25)
        set(v) = sp.edit().putInt("pomoFocusMin", v).apply()

    var pomoShortMin: Int
        get() = sp.getInt("pomoShortMin", 5)
        set(v) = sp.edit().putInt("pomoShortMin", v).apply()

    var pomoLongMin: Int
        get() = sp.getInt("pomoLongMin", 15)
        set(v) = sp.edit().putInt("pomoLongMin", v).apply()

    var pomoRounds: Int
        get() = sp.getInt("pomoRounds", 4)
        set(v) = sp.edit().putInt("pomoRounds", v).apply()

    var strict: Boolean
        get() = sp.getBoolean("strict", false)
        set(v) = sp.edit().putBoolean("strict", v).apply()

    // ---- the running session (a copy of the plan taken when it started) ----

    var sessionStartMs: Long
        get() = sp.getLong("sessionStart", 0L)
        set(v) = sp.edit().putLong("sessionStart", v).apply()

    var sessionEndMs: Long
        get() = sp.getLong("sessionEnd", 0L)
        set(v) = sp.edit().putLong("sessionEnd", v).apply()

    var sessionStrict: Boolean
        get() = sp.getBoolean("sessionStrict", false)
        set(v) = sp.edit().putBoolean("sessionStrict", v).apply()

    var sessionFocusMs: Long
        get() = sp.getLong("sessionFocusMs", 0L)
        set(v) = sp.edit().putLong("sessionFocusMs", v).apply()

    var sessionShortMs: Long
        get() = sp.getLong("sessionShortMs", 0L)
        set(v) = sp.edit().putLong("sessionShortMs", v).apply()

    var sessionLongMs: Long
        get() = sp.getLong("sessionLongMs", 0L)
        set(v) = sp.edit().putLong("sessionLongMs", v).apply()

    var sessionRounds: Int
        get() = sp.getInt("sessionRounds", 1)
        set(v) = sp.edit().putInt("sessionRounds", v).apply()

    // ---- blocking rules ----

    var blockReels: Boolean
        get() = sp.getBoolean("blockReels", true)
        set(v) = sp.edit().putBoolean("blockReels", v).apply()

    var blockShorts: Boolean
        get() = sp.getBoolean("blockShorts", true)
        set(v) = sp.edit().putBoolean("blockShorts", v).apply()

    /** YT_ALLOW, YT_BLOCK or YT_STUDY. Older versions stored a boolean "allowlistOnly". */
    var ytMode: Int
        get() = sp.getInt("ytMode", if (sp.getBoolean("allowlistOnly", false)) YT_STUDY else YT_ALLOW)
        set(v) = sp.edit().putInt("ytMode", v).apply()

    var strictBlockSettings: Boolean
        get() = sp.getBoolean("strictBlockSettings", true)
        set(v) = sp.edit().putBoolean("strictBlockSettings", v).apply()

    var blockedPackages: Set<String>
        get() = HashSet(sp.getStringSet("blockedPackages", emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet("blockedPackages", HashSet(v)).apply()

    /** The user's own free YouTube Data API key, used only for the channel search. */
    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    // ---- study channels ----

    fun channels(): List<Channel> {
        val raw = sp.getString("channels", null)
        if (raw == null) {
            // Older versions kept a plain comma separated list of names.
            val legacy = sp.getString("studyChannels", "") ?: ""
            return legacy.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
                .map { Channel("typed:" + it.lowercase(), it, "") }
        }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Channel(o.getString("id"), o.getString("title"), o.optString("thumb", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveChannels(list: List<Channel>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("title", c.title).put("thumb", c.thumb))
        }
        sp.edit().putString("channels", arr.toString()).apply()
    }

    /** Lower-case names the accessibility service compares against what YouTube shows. */
    fun allowedChannelNames(): List<String> =
        channels().map { it.title.trim().lowercase() }.filter { it.isNotEmpty() }

    // ---- schedules ----

    private var cachedRaw: String? = null
    private var cachedSchedules: List<Schedule> = emptyList()

    fun schedules(): List<Schedule> {
        val raw = sp.getString("schedules", null) ?: return emptyList()
        if (raw == cachedRaw) return cachedSchedules
        val parsed = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Schedule(
                    o.getString("id"), o.getString("name"), o.getInt("start"),
                    o.getInt("end"), o.getInt("days"), o.optBoolean("enabled", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        cachedRaw = raw
        cachedSchedules = parsed
        return parsed
    }

    fun saveSchedules(list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("name", s.name).put("start", s.startMin)
                    .put("end", s.endMin).put("days", s.days).put("enabled", s.enabled)
            )
        }
        sp.edit().putString("schedules", arr.toString()).apply()
    }

    /** True while an enabled schedule covers this moment (handles windows that pass midnight). */
    fun scheduleActive(): Boolean {
        val list = schedules()
        if (list.isEmpty()) return false
        val cal = Calendar.getInstance()
        val minNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        val yesterday = (today + 6) % 7
        for (s in list) {
            if (!s.enabled) continue
            if (s.endMin > s.startMin) {
                if (s.onDay(today) && minNow >= s.startMin && minNow < s.endMin) return true
            } else {
                if (s.onDay(today) && minNow >= s.startMin) return true
                if (s.onDay(yesterday) && minNow < s.endMin) return true
            }
        }
        return false
    }

    // ---- sessions ----

    fun startSession() {
        val now = System.currentTimeMillis()
        sessionStrict = strict
        if (planMode == MODE_POMODORO) {
            sessionFocusMs = pomoFocusMin * 60_000L
            sessionShortMs = pomoShortMin * 60_000L
            sessionLongMs = pomoLongMin * 60_000L
            sessionRounds = pomoRounds
        } else {
            sessionFocusMs = timerMin * 60_000L
            sessionShortMs = 0L
            sessionLongMs = 0L
            sessionRounds = 1
        }
        sessionStartMs = now
        sessionEndMs = phases().last().endMs
    }

    /** The focus blocks and breaks of the running session, in order. */
    fun phases(): List<Phase> {
        val out = ArrayList<Phase>()
        var t = sessionStartMs
        val rounds = sessionRounds.coerceAtLeast(1)
        for (i in 1..rounds) {
            out.add(Phase(t, t + sessionFocusMs, true, i))
            t += sessionFocusMs
            if (i < rounds) {
                val b = if (i % 4 == 0) sessionLongMs else sessionShortMs
                out.add(Phase(t, t + b, false, i))
                t += b
            }
        }
        return out
    }

    fun phaseAt(now: Long): Phase? = phases().firstOrNull { now >= it.startMs && now < it.endMs }

    private fun focusMsUntil(until: Long): Long {
        var total = 0L
        for (p in phases()) {
            if (p.focus) total += (minOf(p.endMs, until) - p.startMs).coerceAtLeast(0L)
        }
        return total
    }

    fun isSessionActive(): Boolean {
        finalizeIfOver()
        return sessionEndMs > System.currentTimeMillis()
    }

    /** The current block of the running session, or null when nothing is running. */
    fun currentPhase(): Phase? {
        if (!isSessionActive()) return null
        return phaseAt(System.currentTimeMillis())
    }

    /** Apps are blocked during focus blocks of a manual session, not during its breaks. */
    fun manualFocusActive(): Boolean = currentPhase()?.focus == true

    /** True when a session or a schedule says distractions should be blocked right now. */
    fun isBlockingNow(): Boolean = manualFocusActive() || scheduleActive()

    /** Strict sessions lock the settings so they can't be loosened mid-session. */
    fun isLocked(): Boolean = isSessionActive() && sessionStrict

    /** Credits the focus minutes once the timer has run out. */
    fun finalizeIfOver() {
        val end = sessionEndMs
        if (end != 0L && end <= System.currentTimeMillis()) {
            addMinutes((focusMsUntil(end) / 60_000L).toInt(), end)
            sessionStartMs = 0L
            sessionEndMs = 0L
        }
    }

    /** Stop early (only reachable when the session isn't strict). Credits the time done so far. */
    fun stopSession() {
        val now = System.currentTimeMillis()
        if (sessionEndMs > now) {
            addMinutes((focusMsUntil(now) / 60_000L).toInt(), now)
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

    fun minutesDaysAgo(n: Int): Int {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return minutesOn(cal.timeInMillis)
    }

    /** Minutes focused today, including the part of a running session done so far. */
    fun todayMinutes(): Int {
        var m = minutesOn(System.currentTimeMillis())
        if (isSessionActive()) m += (focusMsUntil(System.currentTimeMillis()) / 60_000L).toInt()
        return m
    }

    fun totalMinutes(): Int {
        var total = 0
        for ((k, v) in sp.all) {
            if (k.startsWith("min_") && v is Int) total += v
        }
        return total
    }

    /** Consecutive days with at least one focused minute; an empty today doesn't break it. */
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
