package com.focusguard.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/** Screen time per app and per category, read from Android's usage statistics. */
object Usage {

    fun hasAccess(ctx: Context): Boolean {
        return try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val mode = ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun openAccessSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            ctx.toast(ctx.getString(R.string.cant_open_settings))
        }
    }

    private fun startOfDay(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return cal.timeInMillis
    }

    private var cacheAt = 0L
    private var cacheDay = -1
    private var cache: Map<String, Long> = emptyMap()

    /** Foreground time today by package, in milliseconds. Cached for a few seconds. */
    fun today(ctx: Context): Map<String, Long> {
        val now = System.currentTimeMillis()
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (day == cacheDay && now - cacheAt < 15_000L) return cache
        val result = compute(ctx, startOfDay(0), now)
        cache = result
        cacheAt = now
        cacheDay = day
        return result
    }

    /** Total foreground time for a whole day [daysAgo] days back (0 = today). */
    fun totalForDay(ctx: Context, daysAgo: Int): Long {
        if (daysAgo == 0) return today(ctx).values.sum()
        val from = startOfDay(daysAgo)
        return compute(ctx, from, from + 86_400_000L).values.sum()
    }

    /** Walks the foreground/background events and adds up how long each app was on screen. */
    @Suppress("DEPRECATION")
    private fun compute(ctx: Context, from: Long, to: Long): Map<String, Long> {
        if (!hasAccess(ctx)) return emptyMap()
        val out = HashMap<String, Long>()
        try {
            val mgr = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = mgr.queryEvents(from, to)
            val e = UsageEvents.Event()
            val openSince = HashMap<String, Long>()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> openSince[pkg] = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = openSince.remove(pkg)
                        if (start != null && e.timeStamp > start) {
                            out[pkg] = (out[pkg] ?: 0L) + (e.timeStamp - start)
                        }
                    }
                }
            }
            for ((pkg, start) in openSince) {
                if (to > start) out[pkg] = (out[pkg] ?: 0L) + (to - start).coerceAtMost(6 * 3_600_000L)
            }
        } catch (ex: Exception) {
            return emptyMap()
        }
        out.remove(ctx.packageName)
        return out
    }

    // ---- categories ----

    private val social = setOf(
        "com.instagram.android", "com.facebook.katana", "com.facebook.orca", "com.snapchat.android",
        "com.twitter.android", "com.zhiliaoapp.musically", "com.reddit.frontpage", "com.pinterest",
        "com.whatsapp", "org.telegram.messenger", "com.discord", "com.linkedin.android",
        "com.tumblr", "com.facebook.lite", "com.instagram.barcelona", "com.whatsapp.w4b"
    )
    private val video = setOf(
        "com.google.android.youtube", "com.netflix.mediaclient", "in.startv.hotstar",
        "com.amazon.avod.thirdpartyclient", "com.mxtech.videoplayer.ad", "tv.twitch.android.app",
        "com.jio.media.ondemand", "com.google.android.apps.youtube.mango", "com.sonyliv",
        "com.graymatrix.did", "com.disney.disneyplus"
    )
    private val music = setOf(
        "com.spotify.music", "com.google.android.apps.youtube.music", "com.gaana",
    )
    private val study = setOf(
        "org.khanacademy.android", "com.duolingo", "com.unacademy.consumption.unacademyapp",
        "com.physicswallah.app", "com.byjus.thelearningapp", "com.google.android.apps.classroom",
        "com.google.android.keep", "com.google.android.apps.docs", "com.microsoft.office.word",
        "com.microsoft.office.onenote", "com.notion.id", "md.obsidian", "com.ichi2.anki",
        "org.coursera.android", "com.udemy.android", "com.google.android.apps.books"
    )

    private val categoryCache = HashMap<String, Category>()

    fun categoryOf(ctx: Context, pkg: String): Category {
        categoryCache[pkg]?.let { return it }
        val result = when {
            pkg in social -> Category.SOCIAL
            pkg in video -> Category.VIDEO
            pkg in music -> Category.MUSIC
            pkg in study -> Category.STUDY
            else -> fromSystem(ctx, pkg)
        }
        categoryCache[pkg] = result
        return result
    }

    private fun fromSystem(ctx: Context, pkg: String): Category {
        return try {
            val info = ctx.packageManager.getApplicationInfo(pkg, 0)
            if ((info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return Category.GAMES
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> Category.GAMES
                ApplicationInfo.CATEGORY_AUDIO -> Category.MUSIC
                ApplicationInfo.CATEGORY_VIDEO -> Category.VIDEO
                ApplicationInfo.CATEGORY_SOCIAL -> Category.SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> Category.NEWS
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.STUDY
                else -> Category.OTHER
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Category.OTHER
        } catch (e: Exception) {
            Category.OTHER
        }
    }

    fun categoryLabel(ctx: Context, c: Category): String = ctx.getString(
        when (c) {
            Category.SOCIAL -> R.string.cat_social
            Category.VIDEO -> R.string.cat_video
            Category.GAMES -> R.string.cat_games
            Category.MUSIC -> R.string.cat_music
            Category.STUDY -> R.string.cat_study
            Category.NEWS -> R.string.cat_news
            Category.OTHER -> R.string.cat_other
        }
    )

    fun categoryColor(c: Category): Int = when (c) {
        Category.SOCIAL -> 0xFFE1306C.toInt()
        Category.VIDEO -> 0xFFFF5A36.toInt()
        Category.GAMES -> 0xFFA66BFF.toInt()
        Category.MUSIC -> 0xFFFFC145.toInt()
        Category.STUDY -> 0xFF35C98B.toInt()
        Category.NEWS -> 0xFF2FB7D6.toInt()
        Category.OTHER -> 0xFF7A8699.toInt()
    }

    /** Minutes per category today. */
    fun byCategory(ctx: Context, perApp: Map<String, Long>): Map<Category, Long> {
        val out = LinkedHashMap<Category, Long>()
        for ((pkg, ms) in perApp) {
            val c = categoryOf(ctx, pkg)
            out[c] = (out[c] ?: 0L) + ms
        }
        return out
    }

    fun appLabel(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /** Minutes used today against a limit key ("pkg:..." or "cat:..."). */
    fun usedMinutes(ctx: Context, limitKey: String): Int {
        val perApp = today(ctx)
        return when {
            limitKey.startsWith("pkg:") -> ((perApp[limitKey.removePrefix("pkg:")] ?: 0L) / 60_000L).toInt()
            limitKey.startsWith("cat:") -> {
                val cat = try { Category.valueOf(limitKey.removePrefix("cat:")) } catch (e: Exception) { return 0 }
                var ms = 0L
                for ((pkg, t) in perApp) if (categoryOf(ctx, pkg) == cat) ms += t
                (ms / 60_000L).toInt()
            }
            else -> 0
        }
    }
}
