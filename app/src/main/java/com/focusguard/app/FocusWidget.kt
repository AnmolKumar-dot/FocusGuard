package com.focusguard.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/** Home-screen widget: focus state, a live countdown and a button that opens the app. */
class FocusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun build(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            val phase = prefs.currentPhase()
            val active = phase != null
            val color = if (active && phase!!.focus) Accent.RED else Accent.BLUE

            views.setTextColor(R.id.wTitle, color)
            if (active) {
                views.setTextViewText(
                    R.id.wTitle,
                    context.getString(if (phase!!.focus) R.string.widget_focus else R.string.widget_break)
                )
                val base = SystemClock.elapsedRealtime() + (phase.endMs - System.currentTimeMillis())
                views.setViewVisibility(R.id.wTimer, View.VISIBLE)
                views.setChronometerCountDown(R.id.wTimer, true)
                views.setChronometer(R.id.wTimer, base, null, true)
                views.setTextViewText(R.id.wHint, context.getString(R.string.widget_tap_open))
            } else {
                views.setTextViewText(R.id.wTitle, context.getString(R.string.widget_ready))
                views.setViewVisibility(R.id.wTimer, View.GONE)
                views.setTextViewText(
                    R.id.wHint,
                    context.getString(R.string.widget_today, formatMinutes(prefs.todayMinutes()))
                )
            }
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.wRoot, pi)
            return views
        }
    }
}
