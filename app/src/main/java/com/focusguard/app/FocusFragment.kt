package com.focusguard.app

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FocusFragment : Fragment(R.layout.fragment_focus), Refreshable {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    private lateinit var ring: RingView
    private lateinit var timeText: TextView
    private lateinit var phaseLabel: TextView
    private lateinit var appsText: TextView
    private lateinit var iconRow: LinearLayout
    private lateinit var btnEdit: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var chipStreak: TextView
    private lateinit var chipFocus: TextView
    private lateinit var btnEmergency: TextView
    private var iconKey = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        ring = view.findViewById(R.id.ring)
        timeText = view.findViewById(R.id.timeText)
        phaseLabel = view.findViewById(R.id.phaseLabel)
        appsText = view.findViewById(R.id.appsText)
        iconRow = view.findViewById(R.id.iconRow)
        btnEdit = view.findViewById(R.id.btnEdit)
        btnStart = view.findViewById(R.id.btnStart)
        chipStreak = view.findViewById(R.id.chipStreak)
        chipFocus = view.findViewById(R.id.chipFocus)

        btnStart.setOnClickListener { onStartStop() }
        btnEdit.setOnClickListener { SessionSheet().show(parentFragmentManager, "session") }
        view.findViewById<View>(R.id.btnProfile).setOnClickListener {
            SettingsSheet().show(parentFragmentManager, "settings")
        }
        btnEmergency = view.findViewById(R.id.btnEmergency)
        btnEmergency.setOnClickListener { emergencyExit() }
        timeText.setOnClickListener {
            if (!prefs.isSessionActive()) editDuration()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    /** Tap the big timer to set the length with scroll wheels. */
    private fun editDuration() {
        val ctx = requireContext()
        if (prefs.planMode == Prefs.MODE_POMODORO) {
            ctx.promptDuration(getString(R.string.sheet_focus_per_round), prefs.pomoFocusMin, 180) {
                prefs.pomoFocusMin = it
                refresh()
            }
        } else {
            ctx.promptDuration(getString(R.string.sheet_session_length), prefs.timerMin, 720) {
                prefs.timerMin = it
                refresh()
            }
        }
    }

    private fun emergencyExit() {
        val ctx = requireContext()
        if (!prefs.isSessionActive()) return
        if (!prefs.emergencyEnabled) {
            ctx.toast(R.string.emergency_disabled)
            return
        }
        val wait = prefs.emergencyWaitMs()
        if (wait > 0L) {
            val hours = (wait + 3_599_999L) / 3_600_000L
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.emergency_title)
                .setMessage(getString(R.string.emergency_wait, hours.toInt()))
                .setPositiveButton(R.string.ok, null).show()
            return
        }
        val word = getString(R.string.emergency_word)
        val input = EditText(ctx)
        input.hint = word
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        val box = FrameLayout(ctx)
        box.setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), 0)
        box.addView(input)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.emergency_title)
            .setMessage(getString(R.string.emergency_confirm, word, prefs.emergencyCooldownHours))
            .setView(box)
            .setPositiveButton(R.string.emergency_go) { _, _ ->
                if (input.text.toString().trim().equals(word, ignoreCase = true)) {
                    prefs.lastEmergencyMs = System.currentTimeMillis()
                    FocusAccessibilityService.emergencyGraceUntil = System.currentTimeMillis() + 15 * 60_000L
                    prefs.stopSession()
                    refresh()
                } else {
                    ctx.toast(R.string.emergency_wrong)
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun onStartStop() {
        if (prefs.isSessionActive()) {
            if (!prefs.sessionStrict) prefs.stopSession()
        } else {
            (requireActivity() as MainActivity).tryStartSession()
        }
        refresh()
    }

    override fun refresh() {
        if (!isAdded || view == null) return
        val now = System.currentTimeMillis()
        val phase = if (prefs.isSessionActive()) prefs.phaseAt(now) else null

        if (phase != null) {
            timeText.text = formatClock(phase.endMs - now)
            val length = (phase.endMs - phase.startMs).toFloat()
            ring.progress = if (length > 0f) (now - phase.startMs) / length else 0f
            val rounds = prefs.sessionRounds
            val roundText = if (rounds > 1) "  ·  " + getString(R.string.round_of, phase.round, rounds) else ""
            phaseLabel.text = getString(if (phase.focus) R.string.phase_focus else R.string.phase_break) + roundText
            btnEmergency.visibility = if (prefs.sessionStrict) View.VISIBLE else View.GONE
            btnEdit.visibility = View.GONE
            if (prefs.sessionStrict) {
                btnStart.text = getString(R.string.btn_strict_locked)
                btnStart.isEnabled = false
                btnStart.alpha = 0.55f
            } else {
                btnStart.text = getString(R.string.btn_stop)
                btnStart.isEnabled = true
                btnStart.alpha = 1f
            }
        } else {
            val pomodoro = prefs.planMode == Prefs.MODE_POMODORO
            val minutes = if (pomodoro) prefs.pomoFocusMin else prefs.timerMin
            timeText.text = formatClock(minutes * 60_000L)
            ring.progress = 0f
            phaseLabel.text = if (pomodoro) getString(R.string.phase_pomodoro, prefs.pomoRounds) else getString(R.string.phase_timer)
            btnEmergency.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
            btnStart.text = getString(R.string.btn_start)
            btnStart.isEnabled = true
            btnStart.alpha = 1f
        }

        chipFocus.text = formatMinutes(prefs.todayMinutes())
        chipStreak.text = requireContext().daysText(prefs.streak())
        bindApps()
    }

    private fun bindApps() {
        val pkgs = prefs.blockedPackages.toList().sorted()
        appsText.text = getString(R.string.n_apps_blocked, pkgs.size)
        val key = pkgs.take(3).joinToString(",")
        if (key == iconKey) return
        iconKey = key

        iconRow.removeAllViews()
        val ctx = requireContext()
        val size = ctx.dp(26)
        val pad = ctx.dp(2)
        var shown = 0
        for (p in pkgs) {
            if (shown == 3) break
            val icon = iconOf(p) ?: continue
            val iv = ImageView(ctx)
            iv.setImageDrawable(icon)
            iv.setBackgroundResource(R.drawable.bg_icon_circle)
            iv.setPadding(pad, pad, pad, pad)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.outlineProvider = ViewOutlineProvider.BACKGROUND
            iv.clipToOutline = true
            val lp = LinearLayout.LayoutParams(size, size)
            if (shown > 0) lp.marginStart = -ctx.dp(8)
            iconRow.addView(iv, lp)
            shown++
        }
    }

    private fun iconOf(pkg: String): Drawable? {
        return try {
            requireContext().packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
    }
}
