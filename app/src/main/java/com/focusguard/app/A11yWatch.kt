package com.focusguard.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * Blocking only works while the Accessibility service is on, and some phones (or Android itself, after
 * an update or a crash) switch it off without telling anyone. An app cannot turn it back on, so this
 * notices and shows a notification that opens the right Settings screen.
 */
object A11yWatch {
    private const val ID = 4203
    private const val CH = "alert"
    private const val EVERY_MS = 15 * 60_000L

    /** True when a session, a schedule, bedtime or a daily limit is waiting on the service. */
    private fun needed(p: Prefs): Boolean =
        p.isBlockingNow() || p.allSchedules().any { it.enabled } || (p.limitsEnabled && p.limits().isNotEmpty())

    /** Shows the warning when the service is off and needed; removes it otherwise. Never throws. */
    fun check(context: Context) {
        try {
            val ctx = context.applicationContext
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (isAccessibilityServiceEnabled(ctx) || !needed(Prefs(ctx))) {
                nm.cancel(ID)
                return
            }
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CH, ctx.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val open = PendingIntent.getActivity(
                ctx, 3,
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(R.drawable.ic_block)
                .setContentTitle(ctx.getString(R.string.a11y_off_title))
                .setContentText(ctx.getString(R.string.a11y_off_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.a11y_off_text)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
            nm.notify(ID, n)
        } catch (e: Exception) {
            // notifications may be switched off; nothing else to do
        }
    }

    /** Checks every ~15 minutes even when the permanent notification is off. Safe to call often. */
    fun scheduleAlarm(context: Context) {
        try {
            val ctx = context.applicationContext
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 4, Intent(ctx, A11yReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + EVERY_MS, EVERY_MS, pi)
        } catch (e: Exception) {
            // optional
        }
    }
}

class A11yReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        A11yWatch.check(context)
    }
}
