package com.focusguard.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Silences notifications during focus, keeps them in a list, and shows them again as a digest.
 * Needs "Notification access" to be switched on for FocusGuard.
 */
class FocusNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val prefs = Prefs(this)
        if (!prefs.isBlockingNow()) return
        val r = prefs.effective()
        if (r.notifMode == Prefs.NOTIF_OFF) return

        val n = sbn.notification ?: return
        if (sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        val cat = n.category
        if (cat == Notification.CATEGORY_CALL || cat == Notification.CATEGORY_ALARM ||
            cat == Notification.CATEGORY_NAVIGATION || cat == Notification.CATEGORY_TRANSPORT ||
            cat == Notification.CATEGORY_PROGRESS || cat == Notification.CATEGORY_EMERGENCY ||
            cat == Notification.CATEGORY_REMINDER
        ) return
        if (pkg in FocusAccessibilityService.ALWAYS_OK) return

        val silence = when (r.notifMode) {
            Prefs.NOTIF_BLOCKED -> pkg in r.blockedPackages
            Prefs.NOTIF_ALL -> pkg !in r.allowPackages
            else -> false
        }
        if (!silence) return

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        try {
            cancelNotification(sbn.key)
            prefs.addSilenced(Silenced(pkg, Usage.appLabel(this, pkg), title, text, System.currentTimeMillis()))
        } catch (e: Exception) {
            // could not remove it; leave it be
        }
    }
}
