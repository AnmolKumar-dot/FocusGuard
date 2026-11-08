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
                ctx.promptDuration(getString(R.string.sheet_focus_per_round), prefs.pomoFocusMin, 180) {
                    prefs.pomoFocusMin = it
                    bind()
                }
            } else {
                ctx.promptDuration(getString(R.string.sheet_session_length), prefs.timerMin, 720) {
                    prefs.timerMin = it
                    bind()
                }
            }
        }
        view.findViewById<View>(R.id.rowShort).setOnClickListener {
            ctx.promptNumber(getString(R.string.sheet_short_break), prefs.pomoShortMin, 1, 60, getString(R.string.unit_min)) {
                prefs.pomoShortMin = it
                bind()
            }
        }
        view.findViewById<View>(R.id.rowLong).setOnClickListener {
            ctx.promptNumber(getString(R.string.sheet_long_break), prefs.pomoLongMin, 1, 120, getString(R.string.unit_min)) {
                prefs.pomoLongMin = it
                bind()
            }
        }
        view.findViewById<View>(R.id.rowRounds).setOnClickListener {
            ctx.promptNumber(getString(R.string.sheet_rounds), prefs.pomoRounds, 1, 12, getString(R.string.unit_rounds)) {
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

    /** Profile and subject rows, built in code. */
    private fun buildExtra(ctx: android.content.Context) {
        val box = requireView().findViewById<android.widget.LinearLayout>(R.id.sheetExtra)
        box.removeAllViews()
        val subject = prefs.subject
        val rows = listOf(
            Ui.row(ctx, getString(R.string.sheet_profile), null, prefs.profileName(prefs.activeProfile)) {
                ProfilesSheet().show(parentFragmentManager, "profiles")
            },
            Ui.row(ctx, getString(R.string.sheet_subject), null,
                if (subject.isEmpty()) getString(R.string.subject_none) else subject) { pickSubject(ctx) }
        )
        box.addView(Ui.cardOf(ctx, rows), Ui.match())
        Accent.apply(box)
    }

    private fun pickSubject(ctx: android.content.Context) {
        val list = prefs.subjects()
        val items = ArrayList<String>()
        items.add(getString(R.string.subject_none))
        items.addAll(list)
        items.add(getString(R.string.subject_new))
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.sheet_subject)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> { prefs.subject = ""; bind() }
                    which == items.size - 1 ->
                        ctx.promptText(getString(R.string.subject_new), "", getString(R.string.subject_hint)) {
                            if (it.isNotEmpty()) {
                                if (it !in list) prefs.saveSubjects(list + it)
                                prefs.subject = it
                                bind()
                            }
                        }
                    else -> { prefs.subject = list[which - 1]; bind() }
                }
            }
            .setNeutralButton(R.string.subject_manage) { _, _ -> manageSubjects(ctx) }
            .show()
    }

    private fun manageSubjects(ctx: android.content.Context) {
        val list = prefs.subjects()
        if (list.isEmpty()) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.subject_delete_title)
            .setItems(list.toTypedArray()) { _, which ->
                val name = list[which]
                prefs.saveSubjects(list - name)
                if (prefs.subject == name) prefs.subject = ""
                bind()
            }.show()
    }

    private fun bind() {
        if (view == null || !isAdded) return
        val ctx = requireContext()
        val pomodoro = prefs.planMode == Prefs.MODE_POMODORO
        val green = Accent.color
        val white = ContextCompat.getColor(ctx, R.color.text_secondary)

        segTimer.setBackgroundResource(if (pomodoro) 0 else R.drawable.bg_segment_selected)
        segPomo.setBackgroundResource(if (pomodoro) R.drawable.bg_segment_selected else 0)
        segTimer.setTextColor(if (pomodoro) white else green)
        segPomo.setTextColor(if (pomodoro) green else white)

        pomoRows.visibility = if (pomodoro) View.VISIBLE else View.GONE
        labelFocus.text = getString(if (pomodoro) R.string.sheet_focus_per_round else R.string.sheet_focus_time)
        valueFocus.text = formatMinutes(if (pomodoro) prefs.pomoFocusMin else prefs.timerMin)
        valueShort.text = formatMinutes(prefs.pomoShortMin)
        valueLong.text = formatMinutes(prefs.pomoLongMin)
        valueRounds.text = prefs.pomoRounds.toString()
        val n = prefs.blockedPackages.size
        valueApps.text = ctx.getString(R.string.n_apps, n)
        buildExtra(ctx)
    }
}
