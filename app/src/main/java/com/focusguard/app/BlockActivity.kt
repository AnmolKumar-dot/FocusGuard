package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** Full-screen "get back to studying" screen shown when a blocked app is opened. */
class BlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        findViewById<Button>(R.id.backButton).setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    override fun onResume() {
        super.onResume()
        val prefs = Prefs(this)
        val phase = prefs.currentPhase()
        val message = when {
            phase != null && phase.focus -> {
                val minutes = (phase.endMs - System.currentTimeMillis() + 59_999L) / 60_000L
                "That app is blocked for another $minutes min."
            }
            prefs.scheduleActive() -> "Your scheduled focus time is on, so that app is blocked right now."
            else -> "Focus session finished. Nice work!"
        }
        findViewById<TextView>(R.id.blockMessage).text = message
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
