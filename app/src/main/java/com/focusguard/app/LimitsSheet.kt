package com.focusguard.app

import android.content.Context
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Daily time limits per app or per category. Reaching one blocks the app until tomorrow. */
class LimitsSheet : ProgSheet() {

    override val sheetTitle = R.string.limits_title

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // The app picker reports the chosen app back here.
        parentFragmentManager.setFragmentResultListener(AppPickerSheet.RESULT_KEY, this) { _, b ->
            val pkg = b.getString("pkg") ?: return@setFragmentResultListener
            askLimit(requireContext(), "pkg:$pkg", Usage.appLabel(requireContext(), pkg), 30)
        }
    }

    override fun render(ctx: Context, col: LinearLayout) {
        val prefs = Prefs(ctx)
        col.addView(Ui.note(ctx, getString(R.string.limits_help)))
        col.addView(Ui.gap(ctx, 8))
        addCard(
            col,
            Ui.switchRow(ctx, getString(R.string.limits_enabled), null, prefs.limitsEnabled) {
                prefs.limitsEnabled = it
            }
        )
        if (!Usage.hasAccess(ctx)) {
            col.addView(Ui.gap(ctx, 10))
            addCard(
                col,
                Ui.row(ctx, getString(R.string.usage_access_needed), getString(R.string.usage_access_why), null) {
                    Usage.openAccessSettings(ctx)
                }
            )
        }

        val limits = prefs.limits()
        col.addView(Ui.section(ctx, getString(R.string.limits_current)))
        if (limits.isEmpty()) {
            col.addView(Ui.note(ctx, getString(R.string.limits_none)))
        } else {
            val rows = limits.entries.map { (key, minutes) ->
                val name = nameOf(ctx, key)
                val used = if (Usage.hasAccess(ctx)) Usage.usedMinutes(ctx, key) else 0
                Ui.row(
                    ctx, name, getString(R.string.limits_used, formatMinutes(used)),
                    formatMinutes(minutes)
                ) { edit(ctx, key, name, minutes) }
            }
            addCard(col, *rows.toTypedArray())
        }
        col.addView(Ui.gap(ctx, 12))
        col.addView(Ui.pillButton(ctx, getString(R.string.limits_add_app)) {
            AppPickerSheet.newInstance(AppPickerSheet.MODE_PICK).show(parentFragmentManager, "pick")
        })
        col.addView(Ui.gap(ctx, 8))
        col.addView(Ui.pillButton(ctx, getString(R.string.limits_add_cat)) { pickCategory(ctx) })
    }

    private fun nameOf(ctx: Context, key: String): String = when {
        key.startsWith("pkg:") -> Usage.appLabel(ctx, key.removePrefix("pkg:"))
        else -> try {
            getString(R.string.limits_cat_name, Usage.categoryLabel(ctx, Category.valueOf(key.removePrefix("cat:"))))
        } catch (e: Exception) {
            key
        }
    }

    private fun pickCategory(ctx: Context) {
        val cats = Category.values().filter { it != Category.OTHER }
        val names = cats.map { Usage.categoryLabel(ctx, it) }.toTypedArray()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.limits_add_cat)
            .setItems(names) { _, which ->
                val c = cats[which]
                askLimit(ctx, "cat:" + c.name, getString(R.string.limits_cat_name, names[which]), 60)
            }.show()
    }

    private fun askLimit(ctx: Context, key: String, name: String, def: Int) {
        val current = Prefs(ctx).limits()[key] ?: def
        ctx.promptDuration(getString(R.string.limits_daily_for, name), current, 24 * 60) {
            Prefs(ctx).setLimit(key, it)
            rebuild()
        }
    }

    private fun edit(ctx: Context, key: String, name: String, minutes: Int) {
        MaterialAlertDialogBuilder(ctx).setTitle(name)
            .setItems(arrayOf(getString(R.string.limits_change), getString(R.string.delete))) { _, which ->
                if (which == 0) {
                    askLimit(ctx, key, name, minutes)
                } else if (!lockedToast(ctx)) {
                    Prefs(ctx).setLimit(key, 0)
                    rebuild()
                }
            }.show()
    }
}
