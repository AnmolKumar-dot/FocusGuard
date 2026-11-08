package com.focusguard.app

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class BlocksFragment : Fragment(R.layout.fragment_blocks), Refreshable {

    private lateinit var prefs: Prefs
    private lateinit var banner: View
    private lateinit var swShorts: MaterialSwitch
    private lateinit var swReels: MaterialSwitch
    private lateinit var statusShorts: TextView
    private lateinit var statusReels: TextView
    private lateinit var ytGroup: RadioGroup
    private lateinit var channelsValue: TextView
    private lateinit var appsValue: TextView
    private lateinit var swStrictSettings: MaterialSwitch

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        prefs = Prefs(ctx)
        banner = view.findViewById(R.id.banner)
        swShorts = view.findViewById(R.id.swShorts)
        swReels = view.findViewById(R.id.swReels)
        statusShorts = view.findViewById(R.id.statusShorts)
        statusReels = view.findViewById(R.id.statusReels)
        ytGroup = view.findViewById(R.id.ytGroup)
        channelsValue = view.findViewById(R.id.channelsValue)
        appsValue = view.findViewById(R.id.appsValue)
        swStrictSettings = view.findViewById(R.id.swStrictSettings)

        view.findViewById<View>(R.id.btnFix).setOnClickListener { openAccessibilitySettings(ctx) }
        view.findViewById<View>(R.id.rowChannels).setOnClickListener {
            ChannelPickerSheet().show(parentFragmentManager, "channels")
        }
        view.findViewById<View>(R.id.rowApps).setOnClickListener {
            AppPickerSheet().show(parentFragmentManager, "apps")
        }

        loadIcon(view.findViewById(R.id.iconYt), "com.google.android.youtube")
        loadIcon(view.findViewById(R.id.iconIg), "com.instagram.android")
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun loadIcon(iv: ImageView, pkg: String) {
        val icon: Drawable? = try {
            requireContext().packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
        if (icon != null) iv.setImageDrawable(icon)
    }

    private fun bindSwitch(sw: CompoundButton, value: Boolean, onChange: (Boolean) -> Unit) {
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, checked ->
            if (prefs.isLocked()) {
                requireContext().toast(R.string.locked_strict)
            } else {
                onChange(checked)
            }
            refresh()
        }
    }

    private fun status(tv: TextView, on: Boolean, hasPermission: Boolean) {
        val ctx = requireContext()
        when {
            !hasPermission -> {
                tv.text = getString(R.string.status_permission_missing)
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.danger))
            }
            on -> {
                tv.text = getString(R.string.status_blocking)
                tv.setTextColor(Accent.color)
            }
            else -> {
                tv.text = getString(R.string.status_off)
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
        }
    }

    override fun refresh() {
        if (!isAdded || view == null) return
        val hasPermission = isAccessibilityServiceEnabled(requireContext())
        banner.visibility = if (hasPermission) View.GONE else View.VISIBLE

        bindSwitch(swShorts, prefs.blockShorts) { prefs.blockShorts = it }
        bindSwitch(swReels, prefs.blockReels) { prefs.blockReels = it }
        bindSwitch(swStrictSettings, prefs.strictBlockSettings) { prefs.strictBlockSettings = it }
        // Study mode always blocks Shorts, so show that honestly.
        status(statusShorts, prefs.blockShorts || prefs.ytMode == Prefs.YT_STUDY, hasPermission)
        status(statusReels, prefs.blockReels, hasPermission)

        ytGroup.setOnCheckedChangeListener(null)
        ytGroup.check(
            when (prefs.ytMode) {
                Prefs.YT_STUDY -> R.id.rbStudy
                Prefs.YT_BLOCK -> R.id.rbBlock
                else -> R.id.rbAllow
            }
        )
        ytGroup.setOnCheckedChangeListener { _, id ->
            if (prefs.isLocked()) {
                requireContext().toast(R.string.locked_strict)
            } else {
                prefs.ytMode = when (id) {
                    R.id.rbStudy -> Prefs.YT_STUDY
                    R.id.rbBlock -> Prefs.YT_BLOCK
                    else -> Prefs.YT_ALLOW
                }
            }
            refresh()
        }

        val channels = prefs.channels().size
        channelsValue.text = when {
            channels == 0 -> getString(R.string.channels_none)
            else -> getString(R.string.n_channels_allowed, channels)
        }
        val apps = prefs.blockedPackages.size
        appsValue.text = requireContext().getString(R.string.n_apps, apps)
        buildExtras()
        Accent.apply(requireView())
    }

    private fun buildExtras() {
        val ctx = requireContext()
        val v = requireView()
        val top = v.findViewById<android.widget.LinearLayout>(R.id.extraTop)
        val bottom = v.findViewById<android.widget.LinearLayout>(R.id.extraBottom)
        top.removeAllViews()
        bottom.removeAllViews()

        // which set of rules is being edited
        top.addView(
            Ui.cardOf(
                ctx, listOf(
                    Ui.row(ctx, getString(R.string.profile_rules_for), getString(R.string.profile_rules_sub),
                        prefs.profileName(prefs.activeProfile)) {
                        ProfilesSheet().show(parentFragmentManager, "profiles")
                    })
            ), Ui.match()
        )
        top.addView(Ui.gap(ctx, 16))

        bottom.addView(Ui.section(ctx, getString(R.string.more_rules)))
        val siteNames = arrayOf(getString(R.string.sites_off), getString(R.string.sites_block), getString(R.string.sites_study))
        val notifNames = arrayOf(getString(R.string.notif_mode_off), getString(R.string.notif_mode_blocked), getString(R.string.notif_mode_all))
        val pending = prefs.silenced().size
        val rows = ArrayList<View>()
        rows.add(Ui.row(ctx, getString(R.string.sites_title), getString(R.string.sites_row_sub),
            siteNames[prefs.siteMode.coerceIn(0, 2)]) { SitesSheet().show(parentFragmentManager, "sites") })
        rows.add(Ui.switchRow(ctx, getString(R.string.allow_mode), getString(R.string.allow_mode_sub), prefs.allowMode) { on ->
            if (prefs.isLocked()) {
                ctx.toast(R.string.locked_strict)
            } else {
                prefs.allowMode = on
            }
            refresh()
        })
        if (prefs.allowMode) {
            rows.add(Ui.row(ctx, getString(R.string.allow_apps), getString(R.string.allow_apps_sub),
                ctx.getString(R.string.n_apps, prefs.allowPackages.size)) {
                AppPickerSheet.newInstance(AppPickerSheet.MODE_ALLOW).show(parentFragmentManager, "apps")
            })
        }
        rows.add(Ui.row(ctx, getString(R.string.notif_title), getString(R.string.notif_row_sub),
            if (pending > 0) getString(R.string.n_silenced, pending) else notifNames[prefs.notifMode.coerceIn(0, 2)]) {
            NotificationsSheet().show(parentFragmentManager, "notifs")
        })
        bottom.addView(Ui.cardOf(ctx, rows), Ui.match())
    }
}
