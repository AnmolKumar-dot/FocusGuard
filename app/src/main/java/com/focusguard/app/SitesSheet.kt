package com.focusguard.app

import android.content.Context
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Website blocking in browsers: block a list, or allow only study websites. */
class SitesSheet : ProgSheet() {

    override val sheetTitle = R.string.sites_title

    override fun render(ctx: Context, col: LinearLayout) {
        val prefs = Prefs(ctx)
        col.addView(Ui.note(ctx, getString(R.string.sites_help)))
        col.addView(Ui.gap(ctx, 8))

        val modeNames = arrayOf(
            getString(R.string.sites_off), getString(R.string.sites_block), getString(R.string.sites_study)
        )
        addCard(
            col,
            Ui.row(ctx, getString(R.string.sites_mode), null, modeNames[prefs.siteMode.coerceIn(0, 2)]) {
                if (lockedToast(ctx)) return@row
                MaterialAlertDialogBuilder(ctx).setTitle(R.string.sites_mode)
                    .setSingleChoiceItems(modeNames, prefs.siteMode) { d, which ->
                        prefs.siteMode = which
                        d.dismiss()
                        rebuild()
                    }.show()
            }
        )

        if (prefs.siteMode == Prefs.SITE_BLOCK) listSection(ctx, col, prefs, true)
        if (prefs.siteMode == Prefs.SITE_STUDY) listSection(ctx, col, prefs, false)
    }

    private fun listSection(ctx: Context, col: LinearLayout, prefs: Prefs, block: Boolean) {
        col.addView(Ui.section(ctx, getString(if (block) R.string.sites_blocked_list else R.string.sites_study_list)))
        val sites = (if (block) prefs.blockedSites else prefs.studySites).toList().sorted()
        if (sites.isEmpty()) {
            col.addView(Ui.note(ctx, getString(if (block) R.string.sites_none_block else R.string.sites_none_study)))
        } else {
            val rows = sites.map { site ->
                Ui.row(ctx, site, null, getString(R.string.remove)) {
                    if (lockedToast(ctx)) return@row
                    val set = (if (block) prefs.blockedSites else prefs.studySites) - site
                    if (block) prefs.blockedSites = set else prefs.studySites = set
                    rebuild()
                }
            }
            addCard(col, *rows.toTypedArray())
        }
        col.addView(Ui.gap(ctx, 12))
        col.addView(Ui.pillButton(ctx, getString(R.string.sites_add)) {
            if (lockedToast(ctx)) return@pillButton
            ctx.promptText(getString(R.string.sites_add), "", getString(R.string.sites_hint)) { text ->
                val host = FocusAccessibilityService.hostOf(text.trim().ifEmpty { return@promptText })
                if (host == null) {
                    ctx.toast(R.string.sites_invalid)
                } else {
                    if (block) prefs.blockedSites = prefs.blockedSites + host else prefs.studySites = prefs.studySites + host
                    rebuild()
                }
            }
        })
        if (!block) {
            col.addView(Ui.gap(ctx, 8))
            col.addView(Ui.pillButton(ctx, getString(R.string.sites_add_suggested)) {
                if (lockedToast(ctx)) return@pillButton
                prefs.studySites = prefs.studySites + SUGGESTED_STUDY
                rebuild()
            })
        }
    }

    companion object {
        val SUGGESTED_STUDY = setOf(
            "wikipedia.org", "khanacademy.org", "ncert.nic.in", "byjus.com", "geeksforgeeks.org",
            "w3schools.com", "britannica.com", "coursera.org", "edx.org", "docs.google.com", "scholar.google.com"
        )
    }
}
