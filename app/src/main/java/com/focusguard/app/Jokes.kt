package com.focusguard.app

import android.content.Context
import androidx.annotation.ArrayRes
import java.util.Calendar

/** Picks a random message for the block screen, shaped by the first-launch survey answers. */
object Jokes {

    fun pick(ctx: Context, prefs: Prefs): String {
        val res = ctx.resources
        val pool = ArrayList<String>()
        fun add(@ArrayRes id: Int, times: Int = 1) {
            val items = res.getStringArray(id)
            repeat(times) { pool.addAll(items) }
        }

        when (prefs.surveyTone) {
            0 -> add(R.array.jokes_gentle, 2)
            2 -> add(R.array.jokes_savage, 2)
            else -> add(R.array.jokes_funny, 2)
        }
        when (prefs.surveyDistraction) {
            0 -> add(R.array.jokes_reels)
            1 -> add(R.array.jokes_youtube)
            2 -> add(R.array.jokes_games)
            3 -> add(R.array.jokes_chats)
        }
        when (prefs.surveyGoal) {
            0 -> add(R.array.jokes_goal_school)
            1 -> add(R.array.jokes_goal_comp)
            2 -> add(R.array.jokes_goal_college)
            3 -> add(R.array.jokes_goal_skill)
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 23 || hour < 5) add(R.array.jokes_night)
        if (prefs.surveyPhone >= 2) add(R.array.jokes_phone)

        val last = prefs.lastJoke
        var chosen = pool[pool.indices.random()]
        for (i in 0 until 6) {
            if (chosen.hashCode() != last) break
            chosen = pool[pool.indices.random()]
        }
        prefs.lastJoke = chosen.hashCode()

        val name = prefs.surveyName.trim().ifEmpty { ctx.getString(R.string.joke_name_default) }
        val hoursTexts = res.getStringArray(R.array.survey_phone_text)
        val hours = hoursTexts.getOrElse(prefs.surveyPhone) { hoursTexts[1] }
        return chosen.replace("{name}", name).replace("{hours}", hours)
    }

    fun emoji(ctx: Context): String {
        val list = ctx.resources.getStringArray(R.array.block_emojis)
        return list[list.indices.random()]
    }
}
