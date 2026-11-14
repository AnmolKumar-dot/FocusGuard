package com.focusguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * The permanent notification: today's screen time, top categories and the focus state.
 * Also posts the digest of silenced notifications when focus ends.
 */
class UsageNotificationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wasBlocking = false
    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        try {
            val n = build()
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Prefs(this).permNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val usage = NotificationChannel(CH_USAGE, getString(R.string.channel_usage), NotificationManager.IMPORTANCE_LOW)
        usage.setShowBadge(false)
        nm.createNotificationChannel(usage)
        val digest = NotificationChannel(CH_DIGEST, getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(digest)
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun build(): Notification {
        val prefs = Prefs(this)
        val phase = prefs.currentPhase()
        val blocking = prefs.isBlockingNow()
        val title: String
        if (phase != null) {
            val left = ((phase.endMs - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
            title = getString(if (phase.focus) R.string.notif_focus_left else R.string.notif_break_left, formatMinutes(left))
        } else if (blocking) {
            title = getString(R.string.notif_scheduled_on)
        } else {
            title = getString(R.string.notif_focus_off, formatMinutes(prefs.todayMinutes()))
        }
        val text: String
        if (Usage.hasAccess(this)) {
            val perApp = Usage.today(this)
            val total = perApp.values.sum()
            val cats = Usage.byCategory(this, perApp).entries.sortedByDescending { it.value }.take(3)
                .joinToString("  ") { Usage.categoryLabel(this, it.key) + " " + formatMs(it.value) }
            text = getString(R.string.notif_screen_time, formatMs(total)) + if (cats.isEmpty()) "" else "  ·  $cats"
        } else {
            text = getString(R.string.notif_no_usage_access)
        }
        val deleteIntent = Intent(this, UsageNotificationService::class.java)
        val delete = if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(
                this, 1, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, 1, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, CH_USAGE)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(if (blocking) Accent.RED else Accent.BLUE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open())
            .setDeleteIntent(delete)
            .build()
    }

    private fun update() {
        val prefs = Prefs(this)
        if (!prefs.permNotification) {
            stopSelf()
            return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(ID, build())
        } catch (e: Exception) {
            // notifications switched off
        }
        val blocking = prefs.isBlockingNow()
        if (wasBlocking && !blocking) postDigest(nm, prefs)
        wasBlocking = blocking
        try {
            A11yWatch.check(this)
            FocusWidget.refreshAll(this) // keeps the widget right when a phase or a schedule changes
        } catch (e: Exception) {
            // optional
        }
    }

    private fun postDigest(nm: NotificationManager, prefs: Prefs) {
        val n = prefs.silenced().size
        if (n == 0) return
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DIGEST, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = NotificationCompat.Builder(this, CH_DIGEST)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(getString(R.string.digest_title, n))
            .setContentText(getString(R.string.digest_text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(ID_DIGEST, note)
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        private const val ID = 4201
        private const val ID_DIGEST = 4202
        private const val CH_USAGE = "usage"
        private const val CH_DIGEST = "digest"

        /** Starts or stops the service to match the setting. Safe to call at any time. */
        fun sync(ctx: Context) {
            val intent = Intent(ctx, UsageNotificationService::class.java)
            try {
                if (Prefs(ctx).permNotification) {
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
                } else {
                    ctx.stopService(intent)
                }
            } catch (e: Exception) {
                // Android may refuse to start it from the background; it starts next time the app opens
            }
        }
    }
}
