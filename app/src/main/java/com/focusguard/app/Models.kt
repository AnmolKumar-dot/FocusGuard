package com.focusguard.app

/** One stretch of a session: a focus block or a break. [round] counts from 1. */
data class Phase(val startMs: Long, val endMs: Long, val focus: Boolean, val round: Int)

/** A YouTube channel the user allowed. [thumb] is empty for channels added by typing the name. */
data class Channel(val id: String, val title: String, val thumb: String)

/** A repeating focus window. [days] is a bitmask: Monday = bit 0 ... Sunday = bit 6. */
data class Schedule(
    val id: String,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val days: Int,
    val enabled: Boolean
) {
    fun onDay(dayIndex: Int): Boolean = (days and (1 shl dayIndex)) != 0
}

interface Refreshable {
    fun refresh()
}
