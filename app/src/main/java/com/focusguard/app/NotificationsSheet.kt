package com.focusguard.app

import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date

/** Notification management: what to silence during focus, and the digest of what was silenced. */
class NotificationsSheet : ProgSheet() {

    override val sheetTitle = R.string.notif_title

    override fun render(ctx: Context, col: LinearLayout) {
        val prefs = Prefs(ctx)
        col.addView(Ui.note(ctx, getString(R.string.notif_help)))
        col.addView(Ui.gap(ctx, 8))

        if (!isNotificationListenerEnabled(ctx)) {
            addCard(
                col,
                Ui.row(ctx, getString(R.string.notif_access_needed), getString(R.string.notif_access_why), null) {
                    openNotificationListenerSettings(ctx)
                }
            )
            col.addView(Ui.gap(ctx, 10))
        }

        val modes = arrayOf(
            getString(R.string.notif_mode_off), getString(R.string.notif_mode_blocked), getString(R.string.notif_mode_all)
        )
        val rows = ArrayList<android.view.View>()
        rows.add(Ui.row(ctx, getString(R.string.notif_mode), null, modes[prefs.notifMode.coerceIn(0, 2)]) {
            if (lockedToast(ctx)) return@row
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.notif_mode)
                .setSingleChoiceItems(modes, prefs.notifMode) { d, which ->
                    prefs.notifMode = which
                    d.dismiss()
                    rebuild()
                }.show()
        })
        if (prefs.notifMode == Prefs.NOTIF_ALL) {
            rows.add(Ui.row(ctx, getString(R.string.notif_allowed_apps), getString(R.string.notif_allowed_sub), null) {
                AppPickerSheet.newInstance(AppPickerSheet.MODE_ALLOW).show(parentFragmentManager, "apps")
            })
        }
        addCard(col, *rows.toTypedArray())
        col.addView(Ui.note(ctx, getString(R.string.notif_exceptions)))

        val items = prefs.silenced().sortedByDescending { it.whenMs }
        col.addView(Ui.section(ctx, getString(R.string.digest_header, items.size)))
        if (items.isEmpty()) {
            col.addView(Ui.note(ctx, getString(R.string.digest_empty)))
            return
        }
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        val list = items.take(60).map { n ->
            val title = if (n.title.isEmpty()) n.app else n.app + " · " + n.title
            Ui.row(ctx, title, n.text.ifEmpty { null }, fmt.format(Date(n.whenMs))) {
                val launch = ctx.packageManager.getLaunchIntentForPackage(n.pkg)
                if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        addCard(col, *list.toTypedArray())
        col.addView(Ui.gap(ctx, 12))
        col.addView(Ui.pillButton(ctx, getString(R.string.digest_clear)) {
            prefs.clearSilenced()
            rebuild()
        })
    }
}
