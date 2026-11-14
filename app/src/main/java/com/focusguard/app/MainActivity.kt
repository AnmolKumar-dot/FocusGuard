package com.focusguard.app

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var nav: LinearLayout
    private var current = 0
    private val handler = Handler(Looper.getMainLooper())

    // Fades the accent colour between blue (idle) and red (focus mode on).
    private val themeTicker = object : Runnable {
        override fun run() {
            Accent.setFocus(prefs.isBlockingNow())
            handler.postDelayed(this, 700)
        }
    }
    private val accentListener: () -> Unit = {
        styleTabs()
        Accent.apply(findViewById<View>(R.id.container))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Accent.setFocus(prefs.isBlockingNow(), animate = false)
        nav = findViewById(R.id.nav)
        for (i in 0 until nav.childCount) {
            nav.getChildAt(i).setOnClickListener { select(i) }
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                Accent.apply(v)
            }
        }, false)
        current = savedInstanceState?.getInt("tab") ?: 0
        if (savedInstanceState == null) show(current) else styleTabs()
        UsageNotificationService.sync(this)
        handleIntent(intent)
        if (savedInstanceState == null && !prefs.surveyDone) {
            startActivity(Intent(this, SurveyActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DIGEST, false) == true) {
            intent.removeExtra(EXTRA_OPEN_DIGEST)
            NotificationsSheet().show(supportFragmentManager, "notifs")
        }
    }

    override fun onStart() {
        super.onStart()
        Accent.addListener(accentListener)
        handler.post(themeTicker)
    }

    override fun onStop() {
        super.onStop()
        Accent.removeListener(accentListener)
        handler.removeCallbacks(themeTicker)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    /** 0 = Focus, 1 = Planner, 2 = Usage, 3 = Blocks, 4 = Stats. */
    fun select(index: Int) {
        if (index == current && supportFragmentManager.findFragmentById(R.id.container) != null) return
        current = index
        show(index)
    }

    private fun show(index: Int) {
        val fragment: Fragment = when (index) {
            0 -> FocusFragment()
            1 -> PlannerFragment()
            2 -> UsageFragment()
            3 -> BlocksFragment()
            else -> StatsFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
        styleTabs()
    }

    private fun styleTabs() {
        val on = Accent.color
        val off = ContextCompat.getColor(this, R.color.text_secondary)
        for (i in 0 until nav.childCount) {
            val tab = nav.getChildAt(i) as LinearLayout
            val color = if (i == current) on else off
            (tab.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(color)
            (tab.getChildAt(1) as TextView).setTextColor(color)
        }
    }

    override fun onResume() {
        super.onResume()
        A11yWatch.check(this)
        A11yWatch.scheduleAlarm(this)
    }

    /** Starts a session with the current plan, after checking that blocking can actually work. */
    fun tryStartSession() {
        if (prefs.isSessionActive()) return
        if (!isAccessibilityServiceEnabled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.a11y_first_title)
                .setMessage(R.string.a11y_first_message)
                .setPositiveButton(R.string.open_settings) { _, _ -> openAccessibilitySettings(this) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        prefs.startSession()
        Accent.setFocus(true)
    }

    companion object {
        const val EXTRA_OPEN_DIGEST = "open_digest"
    }
}
