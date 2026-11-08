package com.focusguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the permanent notification and the widget back after a restart or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        UsageNotificationService.sync(context)
        try {
            FocusWidget.refreshAll(context)
        } catch (e: Exception) {
            // optional
        }
    }
}
