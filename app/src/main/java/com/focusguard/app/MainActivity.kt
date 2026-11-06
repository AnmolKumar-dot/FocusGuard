package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var minutesInput: EditText
    private lateinit var strictSwitch: MaterialSwitch
    private lateinit var startButton: Button
    private lateinit var reelsSwitch: MaterialSwitch
    private lateinit var shortsSwitch: MaterialSwitch
    private lateinit var allowSwitch: MaterialSwitch
    private lateinit var channelsInput: EditText
    private lateinit var appsButton: Button
    private lateinit var a11yButton: Button
    private lateinit var a11yStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
        minutesInput = findViewById(R.id.minutesInput)
        strictSwitch = findViewById(R.id.strictSwitch)
        startButton = findViewById(R.id.startButton)
        reelsSwitch = findViewById(R.id.reelsSwitch)
        shortsSwitch = findViewById(R.id.shortsSwitch)
        allowSwitch = findViewById(R.id.allowSwitch)
        channelsInput = findViewById(R.id.channelsInput)
        appsButton = findViewById(R.id.appsButton)
        a11yButton = findViewById(R.id.a11yButton)
        a11yStatus = findViewById(R.id.a11yStatus)

        // Set the saved state first, then attach listeners so they don't fire during setup.
        strictSwitch.isChecked = prefs.strict
        reelsSwitch.isChecked = prefs.blockReels
        shortsSwitch.isChecked = prefs.blockShorts
        allowSwitch.isChecked = prefs.allowlistOnly
        channelsInput.setText(prefs.studyChannels)

        strictSwitch.setOnCheckedChangeListener { _, c -> prefs.strict = c }
        reelsSwitch.setOnCheckedChangeListener { _, c -> prefs.blockReels = c }
        shortsSwitch.setOnCheckedChangeListener { _, c -> prefs.blockShorts = c }
        allowSwitch.setOnCheckedChangeListener { _, c -> prefs.allowlistOnly = c }
        channelsInput.doAfterTextChanged { prefs.studyChannels = it?.toString() ?: "" }

        startButton.setOnClickListener { onStartStop() }
        appsButton.setOnClickListener { pickApps() }
        a11yButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
            if (!prefs.sessionStrict) {
                prefs.stopSession()
                refresh()
            }
            return
        }

        if (!isServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Turn on FocusGuard first")
                .setMessage(
                    "Blocking only works once FocusGuard is enabled in Accessibility settings. " +
                        "If Android says the setting is restricted, open this app's App info, " +
                        "tap the three-dot menu and choose \"Allow restricted settings\"."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val minutes = minutesInput.text.toString().toIntOrNull()
        if (minutes == null || minutes !in 1..720) {
            Toast.makeText(this, "Enter a length between 1 and 720 minutes", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        prefs.sessionStartMs = now
        prefs.sessionStrict = strictSwitch.isChecked
        prefs.sessionEndMs = now + minutes * 60_000L
        refresh()
    }

    private fun pickApps() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val selected = prefs.blockedPackages.toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].first in selected }

        AlertDialog.Builder(this)
            .setTitle("Apps to block during focus")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, index, isChecked ->
                if (isChecked) selected.add(apps[index].first) else selected.remove(apps[index].first)
            }
            .setPositiveButton("Save") { _, _ ->
                prefs.blockedPackages = selected
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.startsWith("$packageName/") && it.endsWith("FocusAccessibilityService")
        }
    }

    private fun refresh() {
        val active = prefs.isSessionActive()
        val lock = active && prefs.sessionStrict

        if (active) {
            val left = (prefs.sessionEndMs - System.currentTimeMillis()) / 1000
            statusText.text = String.format(
                "Focusing · %02d:%02d left%s", left / 60, left % 60, if (lock) " · strict" else ""
            )
        } else {
            statusText.text = "No active session"
        }

        statsText.text = "Today: ${prefs.todayMinutes()} min focused · Streak: ${prefs.streak()} day(s)"

        startButton.text = if (active) "Stop session" else "Start focus session"
        startButton.isEnabled = !lock

        minutesInput.isEnabled = !active
        strictSwitch.isEnabled = !active
        reelsSwitch.isEnabled = !lock
        shortsSwitch.isEnabled = !lock
        allowSwitch.isEnabled = !lock
        channelsInput.isEnabled = !lock
        appsButton.isEnabled = !lock

        appsButton.text = "Choose apps to block (${prefs.blockedPackages.size} selected)"
        a11yStatus.text = if (isServiceEnabled()) {
            "Accessibility service: on"
        } else {
            "Accessibility service: OFF, blocking won't work until you enable it"
        }
    }
}
