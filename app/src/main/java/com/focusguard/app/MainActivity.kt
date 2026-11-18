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
            val tab = nav.getChildAt(i)
            tab.setOnClickListener { select(tabId(tab, i)) }
        }
        applyFeatures()
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                Accent.apply(v)
            }
        }, false)
        current = savedInstanceState?.getInt("tab") ?: 0
        if (savedInstanceState == null) show(current) else styleTabs()
        UsageNotificationService.sync(this)
        Reminders.scheduleAll(this)
        if (savedInstanceState == null && !prefs.surveyDone) {
            startActivity(Intent(this, SurveyActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handled in onResume: the fragment manager can't take new screens before that.
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DIGEST, false) == true) {
            intent.removeExtra(EXTRA_OPEN_DIGEST)
            NotificationsSheet().show(supportFragmentManager, "notifs")
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_WATER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_WATER)
            WaterSheet().show(supportFragmentManager, "water")
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_GOALS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_GOALS)
            select(6)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) {
            intent.removeExtra(EXTRA_OPEN_TODAY)
            select(5)
        }
    }

    /** Each tab in the bar carries its page number in its tag (0 Focus ... 4 Stats, 5 Today, 6 Goals). */
    private fun tabId(tab: View, fallback: Int): Int = (tab.tag as? String)?.toIntOrNull() ?: fallback

    /** Shows or hides the Today and Goals tabs to match the switches in Settings. */
    fun applyFeatures() {
        val f = Features(this)
        for (i in 0 until nav.childCount) {
            val tab = nav.getChildAt(i)
            tab.visibility = when (tabId(tab, i)) {
                5 -> if (f.dashboard) View.VISIBLE else View.GONE
                6 -> if (f.goals || f.tips) View.VISIBLE else View.GONE
                else -> View.VISIBLE
            }
        }
        val hidden = (current == 5 && !f.dashboard) || (current == 6 && !(f.goals || f.tips))
        if (hidden) select(0)
        Reminders.scheduleAll(this)
    }

    /** From the goals checklist: pick the subject and start a focus session with it. */
    fun startFocusFor(subject: String) {
        prefs.subject = subject
        select(0)
        tryStartSession()
    }

    /** After a focus session ends, shows a short study tip once. */
    private fun maybeShowSessionTip() {
        try {
            prefs.finalizeIfOver()
            val sp = Days.prefsOf(this)
            val last = prefs.log().lastOrNull()
            if (!sp.contains("tp_seen")) {
                sp.edit().putLong("tp_seen", last?.startMs ?: 0L).apply()
                return
            }
            if (last == null || last.startMs <= sp.getLong("tp_seen", 0L)) return
            sp.edit().putLong("tp_seen", last.startMs).apply()
            if (!Features(this).tips || last.minutes < 5) return
            val tip = Tips.forSession(this)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.session_done_title)
                .setMessage(getString(R.string.session_done_msg, formatMinutes(last.minutes)) + "\n\n💡 " + getString(tip.body))
                .setPositiveButton(R.string.ok, null)
                .show()
        } catch (e: Exception) {
            // a tip is never worth a crash
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

    /** 0 = Focus, 1 = Planner, 2 = Usage, 3 = Blocks, 4 = Stats, 5 = Today, 6 = Goals. */
    fun select(requested: Int) {
        val f = Features(this)
        val index = when {
            requested == 5 && !f.dashboard -> 0
            requested == 6 && !(f.goals || f.tips) -> 0
            else -> requested
        }
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
            5 -> TodayFragment()
            6 -> GoalsFragment()
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
            val color = if (tabId(tab, i) == current) on else off
            (tab.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(color)
            (tab.getChildAt(1) as TextView).setTextColor(color)
        }
    }

    override fun onResume() {
        super.onResume()
        A11yWatch.check(this)
        A11yWatch.scheduleAlarm(this)
        Reminders.scheduleAll(this)
        askNotificationsOnce()
        handleIntent(intent)
        maybeShowSessionTip()
    }

    private val askNotifications = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { Reminders.scheduleAll(this) }

    /** Reminders are notifications, so Android 13+ needs the permission. Asked once, after the survey. */
    private fun askNotificationsOnce() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 33 || !prefs.surveyDone) return
            val sp = Days.prefsOf(this)
            if (sp.getBoolean("notif_asked", false)) return
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
            sp.edit().putBoolean("notif_asked", true).apply()
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } catch (e: Exception) {
            // asking is optional
        }
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
        const val EXTRA_OPEN_WATER = "open_water"
        const val EXTRA_OPEN_GOALS = "open_goals"
        const val EXTRA_OPEN_TODAY = "open_today"
    }
}
