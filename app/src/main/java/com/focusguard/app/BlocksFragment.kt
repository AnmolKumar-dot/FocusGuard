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
                requireContext().toast("Locked during a strict session")
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
                tv.text = "Permission missing"
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.danger))
            }
            on -> {
                tv.text = "Blocking"
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.green))
            }
            else -> {
                tv.text = "Turned off"
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
                requireContext().toast("Locked during a strict session")
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
            channels == 0 -> "None yet - in study mode every video is blocked"
            channels == 1 -> "1 channel allowed"
            else -> "$channels channels allowed"
        }
        val apps = prefs.blockedPackages.size
        appsValue.text = if (apps == 1) "1 app" else "$apps apps"
    }
}
