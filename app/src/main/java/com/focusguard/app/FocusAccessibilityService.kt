package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Watches the foreground app during a focus session.
 *
 *  - Blocked apps: sent home, block screen shown.
 *  - Instagram Reels / YouTube Shorts: Back is pressed as soon as the player screen appears.
 *  - YouTube study-channels-only: a video page whose channel isn't on your list is closed.
 *
 * The Reels/Shorts/channel checks read the on-screen view tree, so they depend on the IDs and
 * labels Instagram and YouTube use today (English UI). Those can change with app updates;
 * all the values to tweak are in the companion object at the bottom.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val prefs by lazy { Prefs(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionMs = 0L
    private var lastScanMs = 0L
    private var badSince = 0L
    private val recheck = Runnable { checkYouTube() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!prefs.isSessionActive()) return

        if (pkg in blockedPackages()) {
            kickToBlockScreen()
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastScanMs < SCAN_INTERVAL_MS) return
        lastScanMs = now

        when (pkg) {
            INSTAGRAM -> if (prefs.blockReels) checkInstagram()
            YOUTUBE -> checkYouTube()
        }
    }

    override fun onInterrupt() {}

    private fun blockedPackages(): Set<String> {
        val set = HashSet(prefs.blockedPackages)
        if (prefs.sessionStrict) set.addAll(STRICT_EXTRA_BLOCKED)
        return set
    }

    // ---- Instagram ----

    private fun checkInstagram() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM) return

        var found = false
        walk(root) { node ->
            val id = node.viewIdResourceName.orEmpty()
            val label = (node.contentDescription ?: node.text)?.toString().orEmpty()
            if (INSTAGRAM_REEL_ID_PARTS.any { id.contains(it) } ||
                (node.isSelected && label.equals("Reels", ignoreCase = true))
            ) {
                found = true
            }
            found
        }
        if (found) backOut("Reels blocked")
    }

    // ---- YouTube ----

    private fun checkYouTube() {
        if (!prefs.isSessionActive()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != YOUTUBE) {
            badSince = 0L
            return
        }

        var shorts = false
        var hasSubscribe = false
        var hasPlayer = false
        val namedSubscribe = ArrayList<String>()
        val texts = ArrayList<String>()

        walk(root) { node ->
            val id = node.viewIdResourceName.orEmpty()
            val desc = node.contentDescription?.toString().orEmpty().trim()
            val text = node.text?.toString().orEmpty().trim()
            val d = desc.lowercase()

            if (YOUTUBE_SHORTS_ID_PARTS.any { id.contains(it) }) shorts = true
            if (node.isSelected &&
                (desc.equals("Shorts", ignoreCase = true) || text.equals("Shorts", ignoreCase = true))
            ) {
                shorts = true
            }

            if (d.startsWith("subscribe to ") || d.startsWith("subscribed to ")) {
                hasSubscribe = true
                namedSubscribe.add(d)
            } else if (d == "subscribe" || d == "subscribed" ||
                text.equals("Subscribe", true) || text.equals("Subscribed", true)
            ) {
                hasSubscribe = true
            }

            if (id.contains(YOUTUBE_WATCH_ID_PART) ||
                d == "minimize" || d == "minimise"
            ) {
                hasPlayer = true
            }

            if (text.isNotEmpty()) texts.add(text.lowercase())
            if (d.isNotEmpty()) texts.add(d)
            false // keep scanning: we need the whole screen
        }

        if (shorts && (prefs.blockShorts || prefs.allowlistOnly)) {
            badSince = 0L
            backOut("Shorts blocked")
            return
        }

        val watchPage = hasSubscribe && hasPlayer
        if (!prefs.allowlistOnly || !watchPage) {
            badSince = 0L
            return
        }

        // Prefer the "Subscribe to <channel>" label; fall back to all text on screen.
        val haystack = if (namedSubscribe.isNotEmpty()) namedSubscribe else texts
        val allowed = prefs.allowedChannels()
        val ok = allowed.isNotEmpty() && haystack.any { h -> allowed.any { h.contains(it) } }
        if (ok) {
            badSince = 0L
            return
        }

        // Give the page a moment to finish loading before judging it.
        val now = SystemClock.uptimeMillis()
        if (badSince == 0L) badSince = now
        val waited = now - badSince
        if (waited >= GRACE_MS) {
            badSince = 0L
            kickToBlockScreen()
        } else {
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, GRACE_MS - waited + 100)
        }
    }

    // ---- actions ----

    private fun backOut(message: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionMs < ACTION_COOLDOWN_MS) return
        lastActionMs = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(this, "FocusGuard: $message", Toast.LENGTH_SHORT).show()
    }

    private fun kickToBlockScreen() {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionMs < ACTION_COOLDOWN_MS) return
        lastActionMs = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            try {
                startActivity(
                    Intent(this, BlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (_: Exception) {
                // If Android refuses to open it, being sent home still does the job.
            }
        }, 200)
    }

    /** Depth-first walk over the screen's views; stops when [visit] returns true. */
    private fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Boolean) {
        var budget = MAX_NODES
        fun go(node: AccessibilityNodeInfo?): Boolean {
            if (node == null || budget <= 0) return false
            budget--
            if (visit(node)) return true
            for (i in 0 until node.childCount) {
                if (go(node.getChild(i))) return true
            }
            return false
        }
        go(root)
    }

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"

        // Things to adjust if an Instagram/YouTube update stops the detection working.
        val INSTAGRAM_REEL_ID_PARTS = listOf("clips_viewer")
        val YOUTUBE_SHORTS_ID_PARTS =
            listOf("reel_recycler", "reel_player_page_container", "reel_watch_fragment_root")
        const val YOUTUBE_WATCH_ID_PART = "watch_player"

        // While a strict session runs these are blocked too, so it can't be switched off.
        val STRICT_EXTRA_BLOCKED = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )

        private const val SCAN_INTERVAL_MS = 350L
        private const val ACTION_COOLDOWN_MS = 1200L
        private const val GRACE_MS = 1500L
        private const val MAX_NODES = 1500
    }
}
