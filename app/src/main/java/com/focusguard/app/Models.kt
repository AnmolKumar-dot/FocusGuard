package com.focusguard.app

/** One stretch of a session: a focus block or a break. [round] counts from 1. */
data class Phase(val startMs: Long, val endMs: Long, val focus: Boolean, val round: Int)

/** A YouTube channel the user allowed. [thumb] is empty for channels added by typing the name. */
data class Channel(val id: String, val title: String, val thumb: String)

/**
 * A repeating focus window. [days] is a bitmask: Monday = bit 0 ... Sunday = bit 6.
 * [profile] is the rule set used while it runs; empty means "whichever profile is selected".
 */
data class Schedule(
    val id: String,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val days: Int,
    val enabled: Boolean,
    val profile: String = ""
) {
    fun onDay(dayIndex: Int): Boolean = (days and (1 shl dayIndex)) != 0
}

/** A named set of blocking rules (Study, Exam week, Sleep ...). */
data class Profile(val id: String, val name: String)

/** One finished focus session, kept for the weekly report. */
data class LogEntry(val startMs: Long, val minutes: Int, val subject: String, val profile: String)

/** A notification that was silenced during focus, kept for the digest. */
data class Silenced(val pkg: String, val app: String, val title: String, val text: String, val whenMs: Long)

enum class Category { SOCIAL, VIDEO, GAMES, MUSIC, STUDY, NEWS, OTHER }

interface Refreshable {
    fun refresh()
}
