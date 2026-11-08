package com.focusguard.app

import android.content.Context
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Pick, create, rename and delete profiles (Study, Exam week, Sleep ...). */
class ProfilesSheet : ProgSheet() {

    override val sheetTitle = R.string.profiles_title

    override fun render(ctx: Context, col: LinearLayout) {
        val prefs = Prefs(ctx)
        col.addView(Ui.note(ctx, getString(R.string.profiles_help)))
        col.addView(Ui.gap(ctx, 8))
        val rows = prefs.profiles().map { p ->
            val r = Prefs(ctx, p.id)
            val sub = getString(R.string.profile_summary, r.blockedPackages.size, r.allowPackages.size)
            val active = p.id == prefs.activeProfile
            val row = Ui.row(
                ctx, p.name, sub, if (active) getString(R.string.profile_active) else null,
                null
            )
            row.isClickable = true
            row.setOnClickListener {
                if (lockedToast(ctx)) return@setOnClickListener
                prefs.activeProfile = p.id
                rebuild()
            }
            row.setOnLongClickListener {
                manage(ctx, prefs, p)
                true
            }
            row
        }
        addCard(col, *rows.toTypedArray())
        col.addView(Ui.gap(ctx, 12))
        col.addView(Ui.pillButton(ctx, getString(R.string.profile_new)) { create(ctx, prefs) })
    }

    private fun create(ctx: Context, prefs: Prefs) {
        if (lockedToast(ctx)) return
        val presets = listOf(
            getString(R.string.profile_exam), getString(R.string.profile_sleep), getString(R.string.profile_custom)
        )
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.profile_new)
            .setItems(presets.toTypedArray()) { _, which ->
                if (which < 2) {
                    add(prefs, presets[which])
                } else {
                    ctx.promptText(getString(R.string.profile_name), "", getString(R.string.profile_name_hint)) {
                        if (it.isNotEmpty()) add(prefs, it)
                    }
                }
            }.show()
    }

    private fun add(prefs: Prefs, name: String) {
        val id = prefs.addProfile(name, prefs.activeProfile)
        prefs.activeProfile = id
        rebuild()
    }

    private fun manage(ctx: Context, prefs: Prefs, p: Profile) {
        val items = if (p.id == Prefs.DEFAULT_PROFILE) arrayOf(getString(R.string.rename))
        else arrayOf(getString(R.string.rename), getString(R.string.delete))
        MaterialAlertDialogBuilder(ctx).setTitle(p.name).setItems(items) { _, which ->
            if (which == 0) {
                ctx.promptText(getString(R.string.profile_name), p.name, getString(R.string.profile_name_hint)) {
                    if (it.isNotEmpty()) {
                        prefs.renameProfile(p.id, it)
                        rebuild()
                    }
                }
            } else if (!lockedToast(ctx)) {
                prefs.deleteProfile(p.id)
                rebuild()
            }
        }.show()
    }
}
