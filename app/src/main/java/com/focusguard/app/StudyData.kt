package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Date helpers. A "day key" is a text like 2026-11-17, which sorts correctly as text. */
object Days {
    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun key(ms: Long = System.currentTimeMillis()): String = fmt().format(Date(ms))

    /** The day key [n] days ago (0 = today). */
    fun ago(n: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -n)
        return fmt().format(c.time)
    }

    fun plus(dayKey: String, n: Int): String {
        val c = Calendar.getInstance()
        c.time = fmt().parse(dayKey) ?: Date()
        c.add(Calendar.DAY_OF_YEAR, n)
        return fmt().format(c.time)
    }

    /** Short label such as "Tue 18 Nov". */
    fun label(dayKey: String): String {
        val d = try { fmt().parse(dayKey) } catch (e: Exception) { null } ?: return dayKey
        return SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(d)
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun prefsOf(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("focusguard", Context.MODE_PRIVATE)
}

/** Switches for the study tools. Everything is on by default. */
class Features(context: Context) {
    private val sp = Days.prefsOf(context)

    var dashboard: Boolean
        get() = sp.getBoolean("f_dash", true)
        set(v) = sp.edit().putBoolean("f_dash", v).apply()

    var water: Boolean
        get() = sp.getBoolean("f_water", true)
        set(v) = sp.edit().putBoolean("f_water", v).apply()

    var goals: Boolean
        get() = sp.getBoolean("f_goals", true)
        set(v) = sp.edit().putBoolean("f_goals", v).apply()

    var tips: Boolean
        get() = sp.getBoolean("f_tips", true)
        set(v) = sp.edit().putBoolean("f_tips", v).apply()

    /** End-of-day recap reminder. */
    var eveningOn: Boolean
        get() = sp.getBoolean("ev_on", true)
        set(v) = sp.edit().putBoolean("ev_on", v).apply()

    var eveningMin: Int
        get() = sp.getInt("ev_time", 21 * 60)
        set(v) = sp.edit().putInt("ev_time", v).apply()

    /** Morning reminder about revisions that are due. */
    var revisionOn: Boolean
        get() = sp.getBoolean("rv_on", true)
        set(v) = sp.edit().putBoolean("rv_on", v).apply()

    var revisionMin: Int
        get() = sp.getInt("rv_time", 8 * 60)
        set(v) = sp.edit().putInt("rv_time", v).apply()
}

// ---------------------------------------------------------------- water

class Water(context: Context) {
    private val app = context.applicationContext
    private val sp = Days.prefsOf(app)

    companion object {
        const val GLASS_ML = 250
    }

    var remindersOn: Boolean
        get() = sp.getBoolean("w_on", true)
        set(v) = sp.edit().putBoolean("w_on", v).apply()

    /** Daily goal in glasses of 250 ml. 8 glasses = 2 litres. */
    var goal: Int
        get() = sp.getInt("w_goal", 8).coerceIn(1, 40)
        set(v) = sp.edit().putInt("w_goal", v.coerceIn(1, 40)).apply()

    /** Minutes after midnight when the day starts and ends; no reminders outside this window. */
    var wakeMin: Int
        get() = sp.getInt("w_wake", 7 * 60)
        set(v) = sp.edit().putInt("w_wake", v).apply()

    var sleepMin: Int
        get() = sp.getInt("w_sleep", 22 * 60)
        set(v) = sp.edit().putInt("w_sleep", v).apply()

    var intervalMin: Int
        get() = sp.getInt("w_int", 90).coerceIn(20, 360)
        set(v) = sp.edit().putInt("w_int", v.coerceIn(20, 360)).apply()

    fun count(daysAgo: Int = 0): Int = sp.getInt("wd_" + Days.ago(daysAgo), 0)

    /** Adds (or with a negative number removes) glasses for today. Never goes below zero. */
    fun add(n: Int): Int {
        val key = "wd_" + Days.key()
        val v = (sp.getInt(key, 0) + n).coerceIn(0, 99)
        sp.edit().putInt(key, v).apply()
        refreshWidgets()
        return v
    }

    fun ml(glasses: Int): Int = glasses * GLASS_ML

    fun formatMl(ml: Int): String {
        if (ml < 1000) return "$ml ml"
        val litres = String.format(Locale.US, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.')
        return "$litres L"
    }

    /** How many glasses you should have had by now to be on track (0 outside the day window). */
    fun expectedNow(): Double {
        val span = (windowEnd() - wakeMin).coerceAtLeast(60)
        val f = ((minutesIntoWindow() - wakeMin).toDouble() / span).coerceIn(0.0, 1.0)
        return goal * f
    }

    /** End of the day window in minutes after the wake day's midnight; past 1440 if bedtime is after midnight. */
    fun windowEnd(): Int = if (sleepMin <= wakeMin) sleepMin + 1440 else sleepMin

    /** Now, as minutes on the same scale as [windowEnd]: 00:30 counts as 1470 when the day ends at 01:00. */
    fun minutesIntoWindow(): Int {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (now < wakeMin) now + 1440 else now
    }

    fun inWindow(): Boolean = minutesIntoWindow() <= windowEnd() + 10

    private fun refreshWidgets() {
        try {
            DashboardWidget.refreshAll(app)
        } catch (e: Exception) {
            // the widget is optional
        }
    }
}

// ---------------------------------------------------------------- goals

data class Goal(val id: String, val name: String, val type: Int, val estMin: Int, val done: Boolean)

data class GSubject(val id: String, val name: String, val goals: List<Goal>)

data class GTemplate(val name: String, val subjects: List<GSubject>)

data class RevItem(
    val id: String, val goalId: String, val title: String, val dueDay: String,
    val step: Int, val done: Boolean, val createdDay: String
)

object GoalType {
    const val CONCEPT = 0
    const val PRACTICE = 1
    const val REVISION = 2
}

class Goals(context: Context) {
    private val app = context.applicationContext
    private val sp = Days.prefsOf(app)

    /** true = unfinished items move to the next day and finished ones are cleared; false = the whole list resets. */
    var carryOver: Boolean
        get() = sp.getBoolean("g_carry", true)
        set(v) = sp.edit().putBoolean("g_carry", v).apply()

    // ---- reading and writing the list ----

    fun subjects(): List<GSubject> {
        rolloverIfNeeded()
        return read()
    }

    private fun read(): List<GSubject> {
        val raw = sp.getString("g_data", null) ?: return emptyList()
        return try {
            parseSubjects(JSONArray(raw))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<GSubject>) {
        sp.edit().putString("g_data", subjectsToJson(list).toString()).apply()
        try {
            DashboardWidget.refreshAll(app)
        } catch (e: Exception) {
            // optional
        }
    }

    private fun goalToJson(g: Goal) = JSONObject().put("id", g.id).put("n", g.name).put("t", g.type)
        .put("e", g.estMin).put("d", g.done)

    private fun subjectsToJson(list: List<GSubject>): JSONArray {
        val arr = JSONArray()
        for (s in list) {
            val goals = JSONArray()
            s.goals.forEach { goals.put(goalToJson(it)) }
            arr.put(JSONObject().put("id", s.id).put("n", s.name).put("g", goals))
        }
        return arr
    }

    private fun parseSubjects(arr: JSONArray): List<GSubject> {
        val out = ArrayList<GSubject>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ga = o.optJSONArray("g") ?: JSONArray()
            val goals = ArrayList<Goal>()
            for (j in 0 until ga.length()) {
                val g = ga.getJSONObject(j)
                goals.add(Goal(g.getString("id"), g.getString("n"), g.optInt("t", 0), g.optInt("e", 0), g.optBoolean("d", false)))
            }
            out.add(GSubject(o.getString("id"), o.getString("n"), goals))
        }
        return out
    }

    /** At the start of a new day: clear finished items (carry over) or untick everything (reset). */
    private fun rolloverIfNeeded() {
        val today = Days.key()
        val last = sp.getString("g_day", null)
        if (last == today) return
        sp.edit().putString("g_day", today).apply()
        if (last == null) return
        val list = read()
        if (list.isEmpty()) return
        val next = if (carryOver) {
            list.map { s -> s.copy(goals = s.goals.filter { !it.done }) }
        } else {
            list.map { s -> s.copy(goals = s.goals.map { it.copy(done = false) }) }
        }
        write(next)
    }

    // ---- numbers for the dashboard ----

    fun total(): Int = subjects().sumOf { it.goals.size }

    fun checked(): Int = subjects().sumOf { s -> s.goals.count { it.done } }

    /** Checked items / all items, or null when there are no goals at all. */
    fun percent(): Int? {
        val t = total()
        if (t == 0) return null
        return checked() * 100 / t
    }

    fun allDone(): Boolean {
        val t = total()
        return t > 0 && checked() == t
    }

    /** Chapters ticked off today (counts ones cleared by the carry-over too). */
    fun doneToday(): Int = sp.getInt("gc_" + Days.key(), 0)

    private fun bumpDone(delta: Int) {
        val key = "gc_" + Days.key()
        sp.edit().putInt(key, (sp.getInt(key, 0) + delta).coerceAtLeast(0)).apply()
    }

    // ---- editing ----

    private fun edit(change: (List<GSubject>) -> List<GSubject>) {
        write(change(subjects()))
    }

    fun addSubject(name: String): String {
        val id = Days.newId()
        edit { it + GSubject(id, name, emptyList()) }
        return id
    }

    fun renameSubject(id: String, name: String) =
        edit { l -> l.map { if (it.id == id) it.copy(name = name) else it } }

    fun deleteSubject(id: String) {
        val doomed = subjects().firstOrNull { it.id == id }?.goals?.map { it.id }.orEmpty()
        edit { l -> l.filter { it.id != id } }
        Revisions(app).removeForGoals(doomed)
    }

    fun moveSubject(id: String, dir: Int) = edit { l -> moved(l, l.indexOfFirst { it.id == id }, dir) }

    fun addGoal(subjectId: String, name: String, type: Int, estMin: Int) {
        edit { l ->
            l.map { s ->
                if (s.id == subjectId) s.copy(goals = s.goals + Goal(Days.newId(), name, type, estMin, false)) else s
            }
        }
    }

    fun updateGoal(goalId: String, name: String, type: Int, estMin: Int) {
        edit { l ->
            l.map { s ->
                s.copy(goals = s.goals.map { if (it.id == goalId) it.copy(name = name, type = type, estMin = estMin) else it })
            }
        }
    }

    fun deleteGoal(goalId: String) {
        edit { l -> l.map { s -> s.copy(goals = s.goals.filter { it.id != goalId }) } }
        Revisions(app).removeForGoals(listOf(goalId))
    }

    fun moveGoal(goalId: String, dir: Int) {
        edit { l ->
            l.map { s ->
                val i = s.goals.indexOfFirst { it.id == goalId }
                if (i < 0) s else s.copy(goals = moved(s.goals, i, dir))
            }
        }
    }

    private fun <T> moved(list: List<T>, index: Int, dir: Int): List<T> {
        val to = index + dir
        if (index < 0 || to < 0 || to >= list.size) return list
        val m = list.toMutableList()
        val item = m.removeAt(index)
        m.add(to, item)
        return m
    }

    fun findGoal(goalId: String): Pair<GSubject, Goal>? {
        for (s in subjects()) {
            val g = s.goals.firstOrNull { it.id == goalId }
            if (g != null) return Pair(s, g)
        }
        return null
    }

    /**
     * Ticks or unticks an item. Ticking schedules the spaced revisions (same day, then 1, 3, 7, 14
     * and 30 days later); unticking removes the ones made today.
     */
    fun setDone(goalId: String, done: Boolean) {
        val found = findGoal(goalId) ?: return
        if (found.second.done == done) return
        edit { l ->
            l.map { s -> s.copy(goals = s.goals.map { if (it.id == goalId) it.copy(done = done) else it }) }
        }
        val rev = Revisions(app)
        if (done) {
            bumpDone(1)
            rev.scheduleFor(goalId, found.first.name + " – " + found.second.name)
        } else {
            bumpDone(-1)
            rev.removeCreatedToday(goalId)
        }
    }

    /** The next unticked item after [goalId], searching on from it and then from the start. */
    fun nextUnchecked(goalId: String): Pair<GSubject, Goal>? {
        val flat = ArrayList<Pair<GSubject, Goal>>()
        for (s in subjects()) for (g in s.goals) flat.add(Pair(s, g))
        if (flat.isEmpty()) return null
        val at = flat.indexOfFirst { it.second.id == goalId }
        val order = if (at < 0) flat else flat.drop(at + 1) + flat.take(at + 1)
        return order.firstOrNull { !it.second.done }
    }

    // ---- templates ----

    fun templates(): List<GTemplate> {
        val raw = sp.getString("g_templates", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                GTemplate(o.getString("n"), parseSubjects(o.getJSONArray("s")))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeTemplates(list: List<GTemplate>) {
        val arr = JSONArray()
        for (t in list) arr.put(JSONObject().put("n", t.name).put("s", subjectsToJson(t.subjects)))
        sp.edit().putString("g_templates", arr.toString()).apply()
    }

    /** Saves the current list (all unticked) as a template; a template with the same name is replaced. */
    fun saveTemplate(name: String) {
        val clean = subjects().map { s ->
            GSubject(Days.newId(), s.name, s.goals.map { it.copy(id = Days.newId(), done = false) })
        }
        writeTemplates(templates().filter { !it.name.equals(name, true) } + GTemplate(name, clean))
    }

    fun deleteTemplate(name: String) = writeTemplates(templates().filter { it.name != name })

    /** Adds the template's subjects and items to the list. */
    fun applyTemplate(name: String) {
        val t = templates().firstOrNull { it.name == name } ?: return
        val add = t.subjects.map { s ->
            GSubject(Days.newId(), s.name, s.goals.map { it.copy(id = Days.newId(), done = false) })
        }
        edit { it + add }
    }
}

// ---------------------------------------------------------------- revision queue

class Revisions(context: Context) {
    private val app = context.applicationContext
    private val sp = Days.prefsOf(app)

    companion object {
        /** Days after finishing a chapter: same day, then 1, 3, 7, 14 and 30. */
        val STEPS = listOf(0, 1, 3, 7, 14, 30)
    }

    fun all(): List<RevItem> {
        val raw = sp.getString("rv_data", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                RevItem(
                    o.getString("id"), o.getString("g"), o.getString("t"), o.getString("d"),
                    o.optInt("s", 0), o.optBoolean("x", false), o.optString("c", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<RevItem>) {
        val arr = JSONArray()
        for (r in list) {
            arr.put(
                JSONObject().put("id", r.id).put("g", r.goalId).put("t", r.title).put("d", r.dueDay)
                    .put("s", r.step).put("x", r.done).put("c", r.createdDay)
            )
        }
        sp.edit().putString("rv_data", arr.toString()).apply()
        try {
            DashboardWidget.refreshAll(app)
        } catch (e: Exception) {
            // optional
        }
    }

    fun scheduleFor(goalId: String, title: String) {
        val today = Days.key()
        val current = all().filter { !(it.goalId == goalId && it.createdDay == today) }
        val added = STEPS.map { RevItem(Days.newId(), goalId, title, Days.plus(today, it), it, false, today) }
        // keep the file small: drop finished items older than 60 days
        val cutoff = Days.ago(60)
        write((current + added).filter { !(it.done && it.dueDay < cutoff) })
    }

    fun removeCreatedToday(goalId: String) {
        val today = Days.key()
        write(all().filter { !(it.goalId == goalId && it.createdDay == today) })
    }

    fun removeForGoals(goalIds: List<String>) {
        if (goalIds.isEmpty()) return
        write(all().filter { it.goalId !in goalIds })
    }

    /** Not done and due today or earlier, oldest first. */
    fun due(): List<RevItem> {
        val today = Days.key()
        return all().filter { !it.done && it.dueDay <= today }.sortedBy { it.dueDay }
    }

    fun upcoming(): List<RevItem> {
        val today = Days.key()
        return all().filter { !it.done && it.dueDay > today }.sortedBy { it.dueDay }
    }

    fun complete(id: String) {
        write(all().map { if (it.id == id) it.copy(done = true) else it })
    }

    /** Titles of the chapters finished today (from their same-day recap item). */
    fun chaptersToday(): List<String> {
        val today = Days.key()
        return all().filter { it.step == 0 && it.createdDay == today }.map { it.title }
    }
}

// ---------------------------------------------------------------- tips

class Tip(val id: Int, val category: Int, val title: Int, val body: Int)

object Tips {
    const val PRACTICE = 0
    const val RECALL = 1
    const val SPACED = 2
    const val EVENING = 3
    const val MATHS = 4
    const val POMODORO = 5
    const val BODY = 6

    val all: List<Tip> = listOf(
        Tip(1, PRACTICE, R.string.tip1_t, R.string.tip1_b),
        Tip(2, RECALL, R.string.tip2_t, R.string.tip2_b),
        Tip(3, SPACED, R.string.tip3_t, R.string.tip3_b),
        Tip(4, EVENING, R.string.tip4_t, R.string.tip4_b),
        Tip(5, MATHS, R.string.tip5_t, R.string.tip5_b),
        Tip(6, POMODORO, R.string.tip6_t, R.string.tip6_b),
        Tip(7, BODY, R.string.tip7_t, R.string.tip7_b),
        Tip(8, BODY, R.string.tip8_t, R.string.tip8_b),
        Tip(9, PRACTICE, R.string.tip9_t, R.string.tip9_b),
        Tip(10, RECALL, R.string.tip10_t, R.string.tip10_b),
        Tip(11, MATHS, R.string.tip11_t, R.string.tip11_b),
        Tip(12, POMODORO, R.string.tip12_t, R.string.tip12_b)
    )

    /** Tips that fit "I just finished a chapter". */
    private val afterChapter = setOf(PRACTICE, RECALL, SPACED, MATHS)

    /** Tips that fit "I just finished a focus session". */
    private val afterSession = setOf(POMODORO, BODY, RECALL)

    private fun next(context: Context, pool: List<Tip>): Tip {
        val sp = Days.prefsOf(context)
        val n = sp.getInt("tp_idx", 0)
        sp.edit().putInt("tp_idx", n + 1).apply()
        return pool[n % pool.size]
    }

    fun forChapter(context: Context): Tip = next(context, all.filter { it.category in afterChapter })

    fun forSession(context: Context): Tip = next(context, all.filter { it.category in afterSession })

    /** The same tip all day, a different one tomorrow. */
    fun ofTheDay(): Tip {
        val c = Calendar.getInstance()
        return all[(c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)) % all.size]
    }
}
