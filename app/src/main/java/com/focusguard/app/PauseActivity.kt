package com.focusguard.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/** A short breathing pause shown before a distracting app opens. */
class PauseActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null
    private lateinit var pkg: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        val prefs = Prefs(this)
        val seconds = prefs.pauseSeconds.coerceAtLeast(3)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(dp(28), dp(28), dp(28), dp(28))
        root.tag = "aurora"

        val title = TextView(this)
        title.text = getString(R.string.pause_title, Usage.appLabel(this, pkg))
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        root.addView(title)

        val sub = TextView(this)
        sub.text = getString(R.string.pause_subtitle)
        sub.textSize = 16f
        sub.gravity = Gravity.CENTER
        sub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        sub.setPadding(0, dp(10), 0, dp(24))
        root.addView(sub)

        val count = TextView(this)
        count.textSize = 64f
        count.typeface = Typeface.DEFAULT_BOLD
        count.gravity = Gravity.CENTER
        count.tag = "accent_text"
        count.setTextColor(Accent.color)
        root.addView(count)

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        lp.topMargin = dp(32)
        val stay = Ui.pillButton(this, getString(R.string.pause_not_now)) { goHome() }
        root.addView(stay, lp)

        val open = MaterialButton(this)
        open.isAllCaps = false
        open.text = getString(R.string.pause_open_wait)
        open.isEnabled = false
        val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        lp2.topMargin = dp(12)
        root.addView(open, lp2)
        open.setOnClickListener { openAnyway() }

        setContentView(root)
        Accent.setFocus(false, animate = false)
        Accent.apply(root)

        timer = object : CountDownTimer(seconds * 1000L, 250L) {
            override fun onTick(left: Long) {
                count.text = ((left + 999L) / 1000L).toString()
            }

            override fun onFinish() {
                count.text = "0"
                open.isEnabled = true
                open.text = getString(R.string.pause_open_anyway)
            }
        }.start()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private fun openAnyway() {
        val prefs = Prefs(this)
        FocusAccessibilityService.pauseGrants[pkg] =
            System.currentTimeMillis() + prefs.pauseGraceMin * 60_000L
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
