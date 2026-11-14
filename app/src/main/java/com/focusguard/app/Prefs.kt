package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Everything is stored on the phone only: settings, the running session, schedules and stats.
 *
 * Blocking rules belong to a *profile* (Study, Exam week, Sleep ...). The default profile "study"
 * uses the plain key names (so older installs keep their settings); other profiles use the
 * prefix "p_<id>_". [profile] is the profile this instance reads and writes rules for.
 */
class Prefs(context: Context, profileOverride: String? = null) {

    companion object {
        const val MODE_TIMER = 0
        const val MODE_POMODORO = 1

        const val YT_ALLOW = 0
        const val YT_BLOCK = 1
        const val YT_STUDY = 2

        const val NOTIF_OFF = 0
        const val NOTIF_BLOCKED = 1 // silence notifications of blocked apps during focus
        const val NOTIF_ALL = 2     // silence everything except allowed apps during focus

        const val SITE_OFF = 0
        const val SITE_BLOCK = 1    // block the listed websites
        const val SITE_STUDY = 2    // only the listed study websites open

        const val DEFAULT_PROFILE = "study"

        /** Rule settings that are stored per profile. */
        val RULE_KEYS = listOf(
            "blockReels", "blockShorts", "ytMode", "strictBlockSettings", "blockedPackages",
            "allowMode", "allowPackages", "blockedSites", "studySites", "siteMode", "notifMode"
        )

        private val lock = Any()
    }

    private val app: Context = context.applicationContext
    private val sp: SharedPreferences = app.getSharedPreferences("focusguard", Context.MODE_PRIVATE)

    /** Which profile's rules this instance reads and writes. */
    private val fixedProfile: String? = profileOverride

    val profile: String
        get() = fixedProfile ?: (sp.getString("activeProfile", DEFAULT_PROFILE) ?: DEFAULT_PROFILE)

    private fun key(p: String, name: String): String =
        if (p == DEFAULT_PROFILE) name else "p_${p}_$name"

    private fun k(name: String): String = key(profile, name)

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

    var sessionProfile: String
        get() = sp.getString("sessionProfile", DEFAULT_PROFILE) ?: DEFAULT_PROFILE
        set(v) = sp.edit().putString("sessionProfile", v).apply()

    var sessionSubject: String
        get() = sp.getString("sessionSubject", "") ?: ""
        set(v) = sp.edit().putString("sessionSubject", v).apply()

    // ---- blocking rules (per profile) ----

    var blockReels: Boolean
        get() = sp.getBoolean(k("blockReels"), true)
        set(v) = sp.edit().putBoolean(k("blockReels"), v).apply()

    var blockShorts: Boolean
        get() = sp.getBoolean(k("blockShorts"), true)
        set(v) = sp.edit().putBoolean(k("blockShorts"), v).apply()

    /** YT_ALLOW, YT_BLOCK or YT_STUDY. Older versions stored a boolean "allowlistOnly". */
    var ytMode: Int
        get() = sp.getInt(k("ytMode"), if (sp.getBoolean("allowlistOnly", false)) YT_STUDY else YT_ALLOW)
        set(v) = sp.edit().putInt(k("ytMode"), v).apply()

    var strictBlockSettings: Boolean
        get() = sp.getBoolean(k("strictBlockSettings"), true)
        set(v) = sp.edit().putBoolean(k("strictBlockSettings"), v).apply()

    var blockedPackages: Set<String>
        get() = HashSet(sp.getStringSet(k("blockedPackages"), emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet(k("blockedPackages"), HashSet(v)).apply()

    /** Allow-list mode: while blocking, every app except [allowPackages] is blocked. */
    var allowMode: Boolean
        get() = sp.getBoolean(k("allowMode"), false)
        set(v) = sp.edit().putBoolean(k("allowMode"), v).apply()

    var allowPackages: Set<String>
        get() = HashSet(sp.getStringSet(k("allowPackages"), emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet(k("allowPackages"), HashSet(v)).apply()

    /** SITE_OFF, SITE_BLOCK or SITE_STUDY. */
    var siteMode: Int
        get() = sp.getInt(k("siteMode"), SITE_OFF)
        set(v) = sp.edit().putInt(k("siteMode"), v).apply()

    var blockedSites: Set<String>
        get() = HashSet(sp.getStringSet(k("blockedSites"), emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet(k("blockedSites"), HashSet(v)).apply()

    var studySites: Set<String>
        get() = HashSet(sp.getStringSet(k("studySites"), emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet(k("studySites"), HashSet(v)).apply()

    /** NOTIF_OFF, NOTIF_BLOCKED or NOTIF_ALL. */
    var notifMode: Int
        get() = sp.getInt(k("notifMode"), NOTIF_OFF)
        set(v) = sp.edit().putInt(k("notifMode"), v).apply()

    // ---- global settings (not per profile) ----

    var blockSplitScreen: Boolean
        get() = sp.getBoolean("blockSplit", true)
        set(v) = sp.edit().putBoolean("blockSplit", v).apply()

    var limitsEnabled: Boolean
        get() = sp.getBoolean("limitsEnabled", true)
        set(v) = sp.edit().putBoolean("limitsEnabled", v).apply()

    var permNotification: Boolean
        get() = sp.getBoolean("permNotif", false)
        set(v) = sp.edit().putBoolean("permNotif", v).apply()

    var pauseSeconds: Int
        get() = sp.getInt("pauseSeconds", 10)
        set(v) = sp.edit().putInt("pauseSeconds", v).apply()

    var pauseGraceMin: Int
        get() = sp.getInt("pauseGraceMin", 5)
        set(v) = sp.edit().putInt("pauseGraceMin", v).apply()

    /** Apps that show the "pause" screen before they open (outside of focus). */
    var pausePackages: Set<String>
        get() = HashSet(sp.getStringSet("pausePackages", emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet("pausePackages", HashSet(v)).apply()

    var emergencyEnabled: Boolean
        get() = sp.getBoolean("emergencyOn", true)
        set(v) = sp.edit().putBoolean("emergencyOn", v).apply()

    var emergencyCooldownHours: Int
        get() = sp.getInt("emergencyCooldownH", 24)
        set(v) = sp.edit().putInt("emergencyCooldownH", v).apply()

    var lastEmergencyMs: Long
        get() = sp.getLong("lastEmergency", 0L)
        set(v) = sp.edit().putLong("lastEmergency", v).apply()

    /** Milliseconds until the emergency exit can be used again (0 when it is available). */
    fun emergencyWaitMs(): Long {
        val until = lastEmergencyMs + emergencyCooldownHours * 3_600_000L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    var weeklyGoalMin: Int
        get() = sp.getInt("weeklyGoal", 600)
        set(v) = sp.edit().putInt("weeklyGoal", v).apply()

    var bedtimeOn: Boolean
        get() = sp.getBoolean("bedtimeOn", false)
        set(v) = sp.edit().putBoolean("bedtimeOn", v).apply()

    var bedtimeStart: Int
        get() = sp.getInt("bedtimeStart", 23 * 60)
        set(v) = sp.edit().putInt("bedtimeStart", v).apply()

    var bedtimeEnd: Int
        get() = sp.getInt("bedtimeEnd", 6 * 60)
        set(v) = sp.edit().putInt("bedtimeEnd", v).apply()

    /** The subject chosen for the next session (empty = none). */
    var subject: String
        get() = sp.getString("subject", "") ?: ""
        set(v) = sp.edit().putString("subject", v).apply()

    // ---- first-launch survey (used to personalise the block-screen messages) ----

    var surveyDone: Boolean
        get() = sp.getBoolean("surveyDone", false)
        set(v) = sp.edit().putBoolean("surveyDone", v).apply()

    var surveyName: String
        get() = sp.getString("surveyName", "") ?: ""
        set(v) = sp.edit().putString("surveyName", v).apply()

    /** 0 Reels, 1 YouTube, 2 Games, 3 Chats, 4 Everything, -1 not answered. */
    var surveyDistraction: Int
        get() = sp.getInt("surveyDistraction", -1)
        set(v) = sp.edit().putInt("surveyDistraction", v).apply()

    /** 0 School, 1 Competitive exam, 2 College, 3 Skill, -1 not answered. */
    var surveyGoal: Int
        get() = sp.getInt("surveyGoal", -1)
        set(v) = sp.edit().putInt("surveyGoal", v).apply()

    /** 0 under 2h, 1 2-4h, 2 4-6h, 3 over 6h, -1 not answered. */
    var surveyPhone: Int
        get() = sp.getInt("surveyPhone", -1)
        set(v) = sp.edit().putInt("surveyPhone", v).apply()

    /** 0 gentle, 1 funny, 2 savage. */
    var surveyTone: Int
        get() = sp.getInt("surveyTone", 1)
        set(v) = sp.edit().putInt("surveyTone", v).apply()

    var lastJoke: Int
        get() = sp.getInt("lastJoke", 0)
        set(v) = sp.edit().putInt("lastJoke", v).apply()

    // ---- profiles ----

    var activeProfile: String
        get() = sp.getString("activeProfile", DEFAULT_PROFILE) ?: DEFAULT_PROFILE
        set(v) = sp.edit().putString("activeProfile", v).apply()

    fun profiles(): List<Profile> {
        val raw = sp.getString("profiles", null)
        val out = ArrayList<Profile>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(Profile(o.getString("id"), o.getString("name")))
                }
            } catch (e: Exception) {
                out.clear()
            }
        }
        if (out.none { it.id == DEFAULT_PROFILE }) out.add(0, Profile(DEFAULT_PROFILE, "Study"))
        return out
    }

    private fun saveProfiles(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        sp.edit().putString("profiles", arr.toString()).apply()
    }

    fun profileName(id: String): String = profiles().firstOrNull { it.id == id }?.name ?: "Study"

    /** Creates a profile whose rules start as a copy of [copyFrom]. Returns its id. */
    fun addProfile(name: String, copyFrom: String): String {
        val id = "p" + System.currentTimeMillis().toString(36)
        saveProfiles(profiles() + Profile(id, name))
        copyRules(copyFrom, id)
        return id
    }

    /** Makes sure a built-in profile exists, giving a new one sensible starting rules. */
    fun ensureProfile(id: String, name: String, setup: (Prefs) -> Unit) {
        if (profiles().any { it.id == id }) return
        saveProfiles(profiles() + Profile(id, name))
        setup(Prefs(app, id))
    }

    fun renameProfile(id: String, name: String) {
        saveProfiles(profiles().map { if (it.id == id) Profile(id, name) else it })
    }

    fun deleteProfile(id: String) {
        if (id == DEFAULT_PROFILE) return
        if (id == "sleep" && bedtimeOn) return // bedtime mode needs it
        saveProfiles(profiles().filter { it.id != id })
        val e = sp.edit()
        for (name in RULE_KEYS) e.remove(key(id, name))
        if (activeProfile == id) e.putString("activeProfile", DEFAULT_PROFILE)
        e.apply()
        saveSchedules(schedules().map { if (it.profile == id) it.copy(profile = "") else it })
    }

    private fun copyRules(from: String, to: String) {
        val all = sp.all
        val e = sp.edit()
        for (name in RULE_KEYS) {
            val v = all[key(from, name)]
            val dst = key(to, name)
            when (v) {
                is Boolean -> e.putBoolean(dst, v)
                is Int -> e.putInt(dst, v)
                is String -> e.putString(dst, v)
                is Set<*> -> e.putStringSet(dst, v.filterIsInstance<String>().toSet())
                else -> e.remove(dst)
            }
        }
        e.apply()
    }

    /** The profile whose rules apply at this moment (session > schedule > selected profile). */
    fun ruleProfile(): String {
        if (manualFocusActive()) {
            val p = sessionProfile
            return if (p.isEmpty() || profiles().none { it.id == p }) DEFAULT_PROFILE else p
        }
        val s = activeSchedule()
        if (s != null) {
            val p = if (s.profile.isEmpty()) activeProfile else s.profile
            return if (profiles().none { it.id == p }) DEFAULT_PROFILE else p
        }
        return activeProfile
    }

    /** A view of the rules that are in force right now. */
    fun effective(): Prefs = Prefs(app, ruleProfile())

    // ---- subjects ----

    fun subjects(): List<String> {
        val raw = sp.getString("subjects", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSubjects(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp.edit().putString("subjects", arr.toString()).apply()
    }

    // ---- daily limits: keys look like "pkg:com.app" or "cat:SOCIAL"; value = minutes ----

    fun limits(): Map<String, Int> {
        val raw = sp.getString("limits", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = LinkedHashMap<String, Int>()
            val it = o.keys()
            while (it.hasNext()) {
                val name = it.next()
                out[name] = o.getInt(name)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setLimit(limitKey: String, minutes: Int) {
        val map = LinkedHashMap(limits())
        if (minutes <= 0) map.remove(limitKey) else map[limitKey] = minutes
        val o = JSONObject()
        for ((a, b) in map) o.put(a, b)
        sp.edit().putString("limits", o.toString()).apply()
    }

    // ---- silenced notifications (digest) ----

    fun silenced(): List<Silenced> {
        val raw = sp.getString("silenced", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Silenced(
                    o.getString("pkg"), o.optString("app"), o.optString("title"),
                    o.optString("text"), o.optLong("time")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSilenced(item: Silenced) {
        synchronized(lock) {
            val list = (silenced() + item).takeLast(150)
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject().put("pkg", it.pkg).put("app", it.app).put("title", it.title)
                        .put("text", it.text).put("time", it.whenMs)
                )
            }
            sp.edit().putString("silenced", arr.toString()).apply()
        }
    }

    fun clearSilenced() {
        sp.edit().remove("silenced").apply()
    }

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
                    o.getInt("end"), o.getInt("days"), o.optBoolean("enabled", true),
                    o.optString("profile", "")
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
                    .put("profile", s.profile)
            )
        }
        sp.edit().putString("schedules", arr.toString()).apply()
    }

    /** The schedules the user made, plus bedtime mode when it is on. */
    fun allSchedules(): List<Schedule> {
        val list = ArrayList(schedules())
        if (bedtimeOn) {
            // Bedtime uses the "sleep" profile; recreate it if it was deleted or came from a backup.
            ensureProfile("sleep", app.getString(R.string.profile_sleep)) {
                it.allowMode = true
                it.notifMode = NOTIF_ALL
            }
            list.add(Schedule("bedtime", "Bedtime", bedtimeStart, bedtimeEnd, 127, true, "sleep"))
        }
        return list
    }

    /** The enabled schedule that covers this moment (handles windows that pass midnight). */
    fun activeSchedule(): Schedule? {
        val list = allSchedules()
        if (list.isEmpty()) return null
        val cal = Calendar.getInstance()
        val minNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        val yesterday = (today + 6) % 7
        for (s in list) {
            if (!s.enabled) continue
            if (s.endMin > s.startMin) {
                if (s.onDay(today) && minNow >= s.startMin && minNow < s.endMin) return s
            } else {
                if (s.onDay(today) && minNow >= s.startMin) return s
                if (s.onDay(yesterday) && minNow < s.endMin) return s
            }
        }
        return null
    }

    fun scheduleActive(): Boolean = activeSchedule() != null

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
        sessionProfile = activeProfile
        sessionSubject = subject
        sessionStartMs = now
        sessionEndMs = phases().last().endMs
        refreshWidgets()
    }

    private fun refreshWidgets() {
        try {
            FocusWidget.refreshAll(app)
        } catch (e: Exception) {
            // the widget is optional
        }
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
            val minutes = (focusMsUntil(end) / 60_000L).toInt()
            addMinutes(minutes, end)
            addLog(LogEntry(sessionStartMs, minutes, sessionSubject, sessionProfile))
            sessionStartMs = 0L
            sessionEndMs = 0L
            refreshWidgets()
        }
    }

    /** Stop early (only reachable when the session isn't strict). Credits the time done so far. */
    fun stopSession() {
        val now = System.currentTimeMillis()
        if (sessionEndMs > now) {
            val minutes = (focusMsUntil(now) / 60_000L).toInt()
            addMinutes(minutes, now)
            addLog(LogEntry(sessionStartMs, minutes, sessionSubject, sessionProfile))
        }
        sessionStartMs = 0L
        sessionEndMs = 0L
        refreshWidgets()
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

    // ---- session log, distraction count, focus score ----

    fun log(): List<LogEntry> {
        val raw = sp.getString("log", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LogEntry(o.getLong("t"), o.getInt("m"), o.optString("s"), o.optString("p"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun addLog(e: LogEntry) {
        if (e.minutes <= 0) return
        synchronized(lock) {
            val list = (log() + e).takeLast(500)
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("t", it.startMs).put("m", it.minutes).put("s", it.subject).put("p", it.profile))
            }
            sp.edit().putString("log", arr.toString()).apply()
        }
    }

    private fun blockKey(ms: Long): String =
        "blk_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    /** Counts one time FocusGuard stopped you opening something you shouldn't. */
    fun addBlockEvent() {
        synchronized(lock) {
            val key = blockKey(System.currentTimeMillis())
            sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
        }
    }

    fun blocksDaysAgo(n: Int): Int {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return sp.getInt(blockKey(cal.timeInMillis), 0)
    }

    /** Focus minutes over the 7 days ending [endDaysAgo] days back (0 = this week up to today). */
    fun weekMinutes(endDaysAgo: Int): Int {
        var total = 0
        for (i in endDaysAgo until endDaysAgo + 7) {
            total += if (i == 0) todayMinutes() else minutesDaysAgo(i)
        }
        return total
    }

    /**
     * Focus score, 0-100, over the last 7 days:
     * 50 points for reaching the weekly goal, 25 for days with any focus, 15 for the streak
     * (7 days = full), and 10 for few distraction attempts (none = full, 5+ a day = none).
     */
    fun focusScore(): Int {
        val goal = weeklyGoalMin.coerceAtLeast(1)
        val goalPart = 50.0 * minOf(1.0, weekMinutes(0).toDouble() / goal)
        var activeDays = 0
        var blocks = 0
        for (i in 0..6) {
            val m = if (i == 0) todayMinutes() else minutesDaysAgo(i)
            if (m > 0) activeDays++
            blocks += blocksDaysAgo(i)
        }
        val dayPart = 25.0 * activeDays / 7.0
        val streakPart = 15.0 * minOf(streak(), 7) / 7.0
        val calmPart = 10.0 * (1.0 - minOf(1.0, blocks / 35.0))
        return (goalPart + dayPart + streakPart + calmPart).toInt().coerceIn(0, 100)
    }

    // ---- backup and restore ----

    /** All settings and stats as JSON text. Includes the optional API key. */
    fun exportJson(): String {
        val root = JSONObject()
        for ((name, v) in sp.all) {
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                is Set<*> -> {
                    val arr = JSONArray()
                    v.forEach { arr.put(it.toString()) }
                    o.put("t", "ss").put("v", arr)
                }
                else -> continue
            }
            root.put(name, o)
        }
        return JSONObject().put("app", "FocusGuard").put("version", 1).put("data", root).toString()
    }

    /** Replaces everything with a backup made by [exportJson]. Returns false if it isn't one. */
    fun importJson(text: String): Boolean {
        return try {
            val top = JSONObject(text)
            if (top.optString("app") != "FocusGuard") return false
            val data = top.getJSONObject("data")
            val e = sp.edit()
            e.clear()
            val names = data.keys()
            while (names.hasNext()) {
                val name = names.next()
                if (name == "sessionStart" || name == "sessionEnd") continue // never restore a running session
                val o = data.getJSONObject(name)
                when (o.getString("t")) {
                    "b" -> e.putBoolean(name, o.getBoolean("v"))
                    "i" -> e.putInt(name, o.getInt("v"))
                    "l" -> e.putLong(name, o.getLong("v"))
                    "f" -> e.putFloat(name, o.getDouble("v").toFloat())
                    "s" -> e.putString(name, o.getString("v"))
                    "ss" -> {
                        val arr = o.getJSONArray("v")
                        e.putStringSet(name, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            e.commit()
        } catch (ex: Exception) {
            false
        }
    }
}
