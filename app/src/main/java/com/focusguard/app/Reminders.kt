package com.focusguard.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * Water, end-of-day recap and revision reminders.
 *
 * Each kind has its own alarm. When one fires, the receiver posts the notification and sets the
 * next alarm of that kind. The alarms are "allow while idle" ones, which need no special
 * permission; Android may move them by a few minutes in battery saver, which is fine here.
 * FocusGuard's own notifications are never silenced by Focus Mode.
 */
object Reminders {
    const val ACTION_WATER = "com.focusguard.app.REMIND_WATER"
    const val ACTION_EVENING = "com.focusguard.app.REMIND_EVENING"
    const val ACTION_REVISION = "com.focusguard.app.REMIND_REVISION"
    const val ACTION_ADD_GLASS = "com.focusguard.app.ADD_GLASS"

    private const val CH_WATER = "water"
    private const val CH_WATER_URGENT = "water_urgent"
    private const val CH_STUDY = "study"
    private const val ID_WATER = 4301
    private const val ID_EVENING = 4302
    private const val ID_REVISION = 4303

    private fun pending(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAlarm(ctx: Context, action: String, code: Int, atMs: Long?) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(ctx, action, code)
            if (atMs == null) {
                am.cancel(pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            }
        } catch (e: Exception) {
            // Android refused; the next app start tries again
        }
    }

    /** Sets (or cancels) every reminder to match the settings. Safe to call at any time. */
    fun scheduleAll(context: Context) {
        val ctx = context.applicationContext
        val f = Features(ctx)
        val w = Water(ctx)
        setAlarm(ctx, ACTION_WATER, 1, if (f.water && w.remindersOn) nextWater(w) else null)
        setAlarm(ctx, ACTION_EVENING, 2, if (f.eveningOn && (f.goals || f.tips)) nextDaily(f.eveningMin) else null)
        setAlarm(ctx, ACTION_REVISION, 3, if (f.revisionOn && f.goals && f.dashboard) nextDaily(f.revisionMin) else null)
        try {
            DashboardWidget.refreshAll(ctx) // keeps the widget fresh across midnight
        } catch (e: Exception) {
            // optional
        }
    }

    private fun nextDaily(minutes: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, minutes / 60)
        c.set(Calendar.MINUTE, minutes % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** The next water time: wake time + 1 interval, + 2 intervals ... up to bedtime, then tomorrow. */
    fun nextWater(w: Water): Long {
        val now = System.currentTimeMillis()
        val end = w.windowEnd()
        for (dayOffset in -1..1) {
            var t = w.wakeMin + w.intervalMin
            while (t <= end) {
                val c = Calendar.getInstance()
                c.add(Calendar.DAY_OF_YEAR, dayOffset + t / 1440)
                c.set(Calendar.HOUR_OF_DAY, (t % 1440) / 60)
                c.set(Calendar.MINUTE, t % 60)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (c.timeInMillis > now) return c.timeInMillis
                t += w.intervalMin
            }
        }
        return now + w.intervalMin * 60_000L
    }

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_WATER, ctx.getString(R.string.channel_water), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WATER_URGENT, ctx.getString(R.string.channel_water_urgent), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STUDY, ctx.getString(R.string.channel_study), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun open(ctx: Context, code: Int, extra: String): PendingIntent = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).putExtra(extra, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
        } catch (e: Exception) {
            // notifications are switched off
        }
    }

    /** Called by the receiver when an alarm fires. */
    fun handle(context: Context, action: String?) {
        val ctx = context.applicationContext
        when (action) {
            // the next alarm is always set, even if posting this notification fails
            ACTION_WATER -> try { waterReminder(ctx) } finally { scheduleAll(ctx) }
            ACTION_EVENING -> try { eveningReminder(ctx) } finally { scheduleAll(ctx) }
            ACTION_REVISION -> try { revisionReminder(ctx) } finally { scheduleAll(ctx) }
            ACTION_ADD_GLASS -> {
                Water(ctx).add(1)
                try {
                    (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID_WATER)
                } catch (e: Exception) {
                    // nothing to remove
                }
            }
            else -> scheduleAll(ctx) // boot or app update
        }
    }

    private fun waterReminder(ctx: Context) {
        val f = Features(ctx)
        val w = Water(ctx)
        if (!f.water || !w.remindersOn) return
        if (!w.inWindow()) return // quiet hours
        val have = w.count()
        if (have >= w.goal) return
        val behind = w.expectedNow() - have >= 2.0
        val text = if (behind) {
            ctx.getString(R.string.water_behind, have, w.goal)
        } else {
            ctx.getString(R.string.water_sip, have, w.goal)
        }
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, if (behind) CH_WATER_URGENT else CH_WATER)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle(ctx.getString(R.string.water_notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (behind) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open(ctx, 11, MainActivity.EXTRA_OPEN_WATER))
            .addAction(0, ctx.getString(R.string.water_add_action), pending(ctx, ACTION_ADD_GLASS, 4))
            .build()
        notify(ctx, ID_WATER, n)
    }

    private fun eveningReminder(ctx: Context) {
        val f = Features(ctx)
        if (!f.eveningOn || !(f.goals || f.tips)) return
        val prefs = Prefs(ctx)
        val titles = Revisions(ctx).chaptersToday()
        if (titles.isEmpty() && prefs.todayMinutes() == 0) return // nothing was studied today
        val text = if (titles.isEmpty()) {
            ctx.getString(R.string.evening_text_plain)
        } else {
            val shown = titles.take(3).joinToString(", ") + if (titles.size > 3) "…" else ""
            ctx.getString(R.string.evening_text, shown)
        }
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_STUDY)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentTitle(ctx.getString(R.string.evening_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open(ctx, 12, MainActivity.EXTRA_OPEN_GOALS))
            .build()
        notify(ctx, ID_EVENING, n)
    }

    private fun revisionReminder(ctx: Context) {
        val f = Features(ctx)
        if (!f.revisionOn || !f.goals || !f.dashboard) return
        val due = Revisions(ctx).due()
        if (due.isEmpty()) return
        val shown = due.take(3).joinToString(", ") { it.title } + if (due.size > 3) "…" else ""
        val text = ctx.getString(R.string.revision_text, shown)
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_STUDY)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentTitle(ctx.getString(R.string.revision_title, due.size))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open(ctx, 13, MainActivity.EXTRA_OPEN_TODAY))
            .build()
        notify(ctx, ID_REVISION, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            Reminders.handle(context, intent?.action)
        } catch (e: Exception) {
            // a reminder must never crash the app
        }
    }
}
