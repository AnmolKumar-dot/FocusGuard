package com.focusguard.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

/** The "Edit" sheet: timer or pomodoro lengths, blocked apps, strict mode, and a start button. */
class SessionSheet : BaseSheet(), Refreshable {

    private lateinit var prefs: Prefs
    private lateinit var segTimer: TextView
    private lateinit var segPomo: TextView
    private lateinit var labelFocus: TextView
    private lateinit var valueFocus: TextView
    private lateinit var valueShort: TextView
    private lateinit var valueLong: TextView
    private lateinit var valueRounds: TextView
    private lateinit var valueApps: TextView
    private lateinit var pomoRows: View
    private lateinit var swStrict: MaterialSwitch

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_session, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        prefs = Prefs(ctx)
        segTimer = view.findViewById(R.id.segTimer)
        segPomo = view.findViewById(R.id.segPomo)
        labelFocus = view.findViewById(R.id.labelFocus)
        valueFocus = view.findViewById(R.id.valueFocus)
        valueShort = view.findViewById(R.id.valueShort)
        valueLong = view.findViewById(R.id.valueLong)
        valueRounds = view.findViewById(R.id.valueRounds)
        valueApps = view.findViewById(R.id.valueApps)
        pomoRows = view.findViewById(R.id.pomoRows)
        swStrict = view.findViewById(R.id.swStrict)

        segTimer.setOnClickListener { prefs.planMode = Prefs.MODE_TIMER; bind() }
        segPomo.setOnClickListener { prefs.planMode = Prefs.MODE_POMODORO; bind() }

        view.findViewById<View>(R.id.rowFocus).setOnClickListener {
            if (prefs.planMode == Prefs.MODE_POMODORO) {
                ctx.promptNumber("Focus time per round (minutes)", prefs.pomoFocusMin, 1, 180, "minutes") {
                    prefs.pomoFocusMin = it
                    bind()
                }
            } else {
                ctx.promptNumber("Session length (minutes)", prefs.timerMin, 1, 720, "minutes") {
                    prefs.timerMin = it
                    bind()
                }
            }
        }
        view.findViewById<View>(R.id.rowShort).setOnClickListener {
            ctx.promptNumber("Short break (minutes)", prefs.pomoShortMin, 1, 60, "minutes") {
                prefs.pomoShortMin = it
                bind()
            }
        }
        view.findViewById<View>(R.id.rowLong).setOnClickListener {
            ctx.promptNumber("Long break (minutes)", prefs.pomoLongMin, 1, 120, "minutes") {
                prefs.pomoLongMin = it
                bind()
            }
        }
        view.findViewById<View>(R.id.rowRounds).setOnClickListener {
            ctx.promptNumber("Number of rounds", prefs.pomoRounds, 1, 12, "rounds") {
                prefs.pomoRounds = it
                bind()
            }
        }
        view.findViewById<View>(R.id.rowApps).setOnClickListener {
            AppPickerSheet().show(parentFragmentManager, "apps")
        }
        view.findViewById<View>(R.id.btnStartSheet).setOnClickListener {
            dismiss()
            (requireActivity() as MainActivity).tryStartSession()
        }

        swStrict.isChecked = prefs.strict
        swStrict.setOnCheckedChangeListener { _, checked -> prefs.strict = checked }
        bind()
    }

    override fun refresh() = bind()

    private fun bind() {
        if (view == null || !isAdded) return
        val ctx = requireContext()
        val pomodoro = prefs.planMode == Prefs.MODE_POMODORO
        val green = ContextCompat.getColor(ctx, R.color.green)
        val white = ContextCompat.getColor(ctx, R.color.text_secondary)

        segTimer.setBackgroundResource(if (pomodoro) 0 else R.drawable.bg_segment_selected)
        segPomo.setBackgroundResource(if (pomodoro) R.drawable.bg_segment_selected else 0)
        segTimer.setTextColor(if (pomodoro) white else green)
        segPomo.setTextColor(if (pomodoro) green else white)

        pomoRows.visibility = if (pomodoro) View.VISIBLE else View.GONE
        labelFocus.text = if (pomodoro) "Focus time per round" else "Focus time"
        valueFocus.text = formatMinutes(if (pomodoro) prefs.pomoFocusMin else prefs.timerMin)
        valueShort.text = formatMinutes(prefs.pomoShortMin)
        valueLong.text = formatMinutes(prefs.pomoLongMin)
        valueRounds.text = prefs.pomoRounds.toString()
        val n = prefs.blockedPackages.size
        valueApps.text = if (n == 1) "1 app" else "$n apps"
    }
}
