package com.focusguard.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** A 2x2 home-screen widget: focus time today, goals done and water. Tap it to open Today. */
class DashboardWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DashboardWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun build(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val f = Features(context)
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
            val blocking = prefs.isBlockingNow()
            views.setTextColor(R.id.wdTitle, if (blocking) Accent.RED else Accent.BLUE)
            views.setTextViewText(R.id.wdTitle, context.getString(R.string.widget_dash_title))

            views.setTextViewText(R.id.wdFocusV, formatMinutes(prefs.todayMinutes()))
            views.setTextViewText(R.id.wdFocusL, context.getString(R.string.widget_dash_focus))

            if (f.goals) {
                val pct = Goals(context).percent()
                views.setViewVisibility(R.id.wdGoalsRow, View.VISIBLE)
                views.setTextViewText(R.id.wdGoalsV, if (pct == null) "—" else "$pct%")
                views.setTextViewText(R.id.wdGoalsL, context.getString(R.string.widget_dash_goals))
            } else {
                views.setViewVisibility(R.id.wdGoalsRow, View.GONE)
            }

            if (f.water) {
                val w = Water(context)
                views.setViewVisibility(R.id.wdWaterRow, View.VISIBLE)
                views.setTextViewText(R.id.wdWaterV, "${w.count()}/${w.goal}")
                views.setTextViewText(R.id.wdWaterL, context.getString(R.string.widget_dash_water))
            } else {
                views.setViewVisibility(R.id.wdWaterRow, View.GONE)
            }

            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                context, 5, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.wdRoot, pi)
            return views
        }
    }
}
