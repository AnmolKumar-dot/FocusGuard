package com.focusguard.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen screen shown when a blocked app is opened: a random message shaped by the
 * first-launch survey, why it was blocked, and a way back to studying.
 */
class BlockActivity : AppCompatActivity() {

    private var pulse: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)
        Accent.setFocus(Prefs(this).isBlockingNow(), animate = false)
        Accent.apply(findViewById<View>(android.R.id.content))

        findViewById<Button>(R.id.backButton).setOnClickListener { goHome() }
        findViewById<TextView>(R.id.blockAnother).setOnClickListener { showJoke() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        val ring = findViewById<View>(R.id.blockPulse)
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            ring,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0.1f)
        ).apply {
            duration = 1400L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        showJoke()
    }

    override fun onDestroy() {
        pulse?.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showJoke()
    }

    private fun showJoke() {
        val prefs = Prefs(this)
        findViewById<TextView>(R.id.blockEmoji).text = Jokes.emoji(this)
        val joke = findViewById<TextView>(R.id.blockJoke)
        joke.text = Jokes.pick(this, prefs)
        joke.alpha = 0f
        joke.translationY = 24f
        joke.animate().alpha(1f).translationY(0f).setDuration(450L).start()
        bindDetails(prefs)
    }

    private fun bindDetails(prefs: Prefs) {
        val phase = prefs.currentPhase()
        val reason = intent.getStringExtra(EXTRA_REASON)
        val message = when {
            reason != null -> reason
            phase != null && phase.focus -> {
                val minutes = (phase.endMs - System.currentTimeMillis() + 59_999L) / 60_000L
                getString(R.string.block_app_for, minutes)
            }
            prefs.scheduleActive() -> getString(R.string.block_scheduled)
            else -> getString(R.string.block_finished)
        }
        findViewById<TextView>(R.id.blockMessage).text = message
        findViewById<TextView>(R.id.blockStatToday).text =
            getString(R.string.block_stat_blocked, prefs.blocksDaysAgo(0))
        val left = findViewById<TextView>(R.id.blockStatLeft)
        if (phase != null && phase.focus) {
            val minutes = ((phase.endMs - System.currentTimeMillis() + 59_999L) / 60_000L).toInt()
            left.text = getString(R.string.block_stat_left, formatMinutes(minutes))
        } else {
            left.text = getString(R.string.block_stat_today, formatMinutes(prefs.todayMinutes()))
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_REASON = "reason"
    }
}
