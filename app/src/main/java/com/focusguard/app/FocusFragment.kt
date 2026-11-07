package com.focusguard.app

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

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
            (requireActivity() as MainActivity).select(3)
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
            val roundText = if (rounds > 1) "  ·  round ${phase.round} of $rounds" else ""
            phaseLabel.text = (if (phase.focus) "Focus" else "Break") + roundText
            btnEdit.visibility = View.GONE
            if (prefs.sessionStrict) {
                btnStart.text = "Strict mode · locked in"
                btnStart.isEnabled = false
                btnStart.alpha = 0.55f
            } else {
                btnStart.text = "Stop session"
                btnStart.isEnabled = true
                btnStart.alpha = 1f
            }
        } else {
            val pomodoro = prefs.planMode == Prefs.MODE_POMODORO
            val minutes = if (pomodoro) prefs.pomoFocusMin else prefs.timerMin
            timeText.text = formatClock(minutes * 60_000L)
            ring.progress = 0f
            phaseLabel.text = if (pomodoro) "Pomodoro  ·  ${prefs.pomoRounds} rounds" else "Timer"
            btnEdit.visibility = View.VISIBLE
            btnStart.text = "Start Focus Now"
            btnStart.isEnabled = true
            btnStart.alpha = 1f
        }

        chipFocus.text = formatMinutes(prefs.todayMinutes())
        chipStreak.text = prefs.streak().toString() + (if (prefs.streak() == 1) " day" else " days")
        bindApps()
    }

    private fun bindApps() {
        val pkgs = prefs.blockedPackages.toList().sorted()
        appsText.text = if (pkgs.size == 1) "1 app blocked" else "${pkgs.size} apps blocked"
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
