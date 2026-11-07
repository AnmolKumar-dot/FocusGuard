package com.focusguard.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var nav: LinearLayout
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        nav = findViewById(R.id.nav)
        for (i in 0 until nav.childCount) {
            nav.getChildAt(i).setOnClickListener { select(i) }
        }
        current = savedInstanceState?.getInt("tab") ?: 0
        if (savedInstanceState == null) show(current) else styleTabs()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    /** 0 = Focus, 1 = Planner, 2 = Blocks, 3 = Stats. */
    fun select(index: Int) {
        if (index == current && supportFragmentManager.findFragmentById(R.id.container) != null) return
        current = index
        show(index)
    }

    private fun show(index: Int) {
        val fragment: Fragment = when (index) {
            0 -> FocusFragment()
            1 -> PlannerFragment()
            2 -> BlocksFragment()
            else -> StatsFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
        styleTabs()
    }

    private fun styleTabs() {
        val on = ContextCompat.getColor(this, R.color.green)
        val off = ContextCompat.getColor(this, R.color.text_secondary)
        for (i in 0 until nav.childCount) {
            val tab = nav.getChildAt(i) as LinearLayout
            val color = if (i == current) on else off
            (tab.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(color)
            (tab.getChildAt(1) as TextView).setTextColor(color)
        }
    }

    /** Starts a session with the current plan, after checking that blocking can actually work. */
    fun tryStartSession() {
        if (prefs.isSessionActive()) return
        if (!isAccessibilityServiceEnabled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Turn on FocusGuard first")
                .setMessage(
                    "Blocking only works once FocusGuard is enabled in Accessibility settings. " +
                        "If Android says the setting is restricted, open this app's App info, " +
                        "tap the three-dot menu and choose Allow restricted settings."
                )
                .setPositiveButton("Open settings") { _, _ -> openAccessibilitySettings(this) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        prefs.startSession()
    }
}
