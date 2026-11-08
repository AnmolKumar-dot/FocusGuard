package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * Watches the foreground app. While blocking is on (a focus session, a scheduled window or
 * bedtime) it applies the rules of the profile in force:
 *
 *  - Blocked apps, or every app except the allow-list: sent home, block screen shown.
 *  - Browsers: blocked websites, or only study websites.
 *  - Instagram Reels / YouTube Shorts: Back is pressed as soon as the player screen appears.
 *  - YouTube study mode: a video page whose channel isn't on your list is closed.
 *  - Strict sessions: split screen and picture-in-picture are closed.
 * Always on: daily time limits and the "pause" screen before chosen apps.
 *
 * The Reels/Shorts/channel/URL checks read the on-screen view tree, so they depend on the IDs and
 * labels those apps use today (English UI). Those can change with app updates; all the values to
 * tweak are in the companion object at the bottom.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val prefs by lazy { Prefs(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionMs = 0L
    private var lastScanMs = 0L
    private var badSince = 0L
    private var lastForeground = ""
    private var limitCheckAt = 0L
    private val recheck = Runnable { checkYouTube(prefs.effective()) }

    private val homePackages: Set<String> by lazy {
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private val dialerPackage: String by lazy {
        try {
            (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            checkWindows()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (isExempt(pkg)) return

        val previous = lastForeground
        lastForeground = pkg
        val stateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val blocking = prefs.isBlockingNow()
        val r = if (blocking) prefs.effective() else prefs

        // Daily time limits apply all the time.
        val nowMs = SystemClock.uptimeMillis()
        if (stateChange || nowMs - limitCheckAt > 5000L) {
            limitCheckAt = nowMs
            val reason = limitReason(pkg)
            if (reason != null) {
                kickToBlockScreen(reason)
                return
            }
        }

        if (!blocking) {
            if (stateChange && pkg != previous) maybePause(pkg)
            return
        }

        if (pkg in blockedPackages(r)) {
            kickToBlockScreen(null)
            return
        }
        if (r.allowMode && pkg !in r.allowPackages) {
            kickToBlockScreen(getString(R.string.block_not_allowed))
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastScanMs < SCAN_INTERVAL_MS) return
        lastScanMs = now

        when {
            pkg == INSTAGRAM -> if (r.blockReels) checkInstagram()
            pkg == YOUTUBE -> checkYouTube(r)
            pkg in BROWSERS && r.siteMode != Prefs.SITE_OFF -> checkBrowser(pkg, r)
        }
    }

    override fun onInterrupt() {}

    /** Apps that must never be blocked: the phone, the keyboard, the launcher, system dialogs. */
    private fun isExempt(pkg: String): Boolean {
        if (pkg == packageName || pkg in ALWAYS_OK) return true
        if (pkg in homePackages || pkg == dialerPackage) return true
        return pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains("deskclock")
    }

    private fun blockedPackages(r: Prefs): Set<String> {
        val set = HashSet(r.blockedPackages)
        if (r.ytMode == Prefs.YT_BLOCK) set.add(YOUTUBE)
        // Only a strict manual session locks Settings, so a schedule can always be switched off.
        if (r.strictBlockSettings && prefs.isLocked()) set.addAll(STRICT_EXTRA_BLOCKED)
        return set
    }

    // ---- daily limits ----

    private fun limitReason(pkg: String): String? {
        if (!prefs.limitsEnabled) return null
        if (System.currentTimeMillis() < emergencyGraceUntil) return null
        val limits = prefs.limits()
        if (limits.isEmpty() || !Usage.hasAccess(this)) return null
        val appKey = "pkg:$pkg"
        val appLimit = limits[appKey]
        if (appLimit != null && Usage.usedMinutes(this, appKey) >= appLimit) {
            return getString(R.string.limit_reached_app, Usage.appLabel(this, pkg), formatMinutes(appLimit))
        }
        val cat = Usage.categoryOf(this, pkg)
        val catKey = "cat:" + cat.name
        val catLimit = limits[catKey]
        if (catLimit != null && Usage.usedMinutes(this, catKey) >= catLimit) {
            return getString(R.string.limit_reached_cat, Usage.categoryLabel(this, cat), formatMinutes(catLimit))
        }
        return null
    }

    // ---- pause screen ----

    private fun maybePause(pkg: String) {
        if (pkg !in prefs.pausePackages) return
        val until = pauseGrants[pkg] ?: 0L
        if (System.currentTimeMillis() < until) return
        try {
            startActivity(
                Intent(this, PauseActivity::class.java)
                    .putExtra(PauseActivity.EXTRA_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            // If Android refuses to open it, the app simply opens as usual.
        }
    }

    // ---- split screen / picture-in-picture (strict sessions only) ----

    private fun checkWindows() {
        if (!prefs.blockSplitScreen || !prefs.isLocked() || !prefs.isBlockingNow()) return
        val list = try { windows } catch (e: Exception) { return }
        var bad = false
        for (w in list) {
            if (w.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER || w.isInPictureInPictureMode) {
                bad = true
                break
            }
        }
        if (bad) backOutHome(getString(R.string.toast_split_closed))
    }

    // ---- browsers ----

    private fun checkBrowser(pkg: String, r: Prefs) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        var url: String? = null
        walk(root) { node ->
            val id = node.viewIdResourceName.orEmpty()
            if (URL_ID_PARTS.any { id.contains(it) }) {
                val t = node.text?.toString()
                if (!t.isNullOrBlank()) {
                    url = t
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        val host = hostOf(url ?: return) ?: return
        when (r.siteMode) {
            Prefs.SITE_BLOCK ->
                if (hostMatches(host, r.blockedSites)) kickToBlockScreen(getString(R.string.block_site, host))
            Prefs.SITE_STUDY ->
                if (!hostMatches(host, r.studySites)) kickToBlockScreen(getString(R.string.block_site_study, host))
        }
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
        if (found) backOut(getString(R.string.toast_reels_blocked))
    }

    // ---- YouTube ----

    private fun checkYouTube(r: Prefs) {
        if (!prefs.isBlockingNow()) return
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

        val study = r.ytMode == Prefs.YT_STUDY
        if (shorts && (r.blockShorts || study)) {
            badSince = 0L
            backOut(getString(R.string.toast_shorts_blocked))
            return
        }

        val watchPage = hasSubscribe && hasPlayer
        if (!study || !watchPage) {
            badSince = 0L
            return
        }

        // Prefer the "Subscribe to <channel>" label; fall back to all text on screen.
        val haystack = if (namedSubscribe.isNotEmpty()) namedSubscribe else texts
        val allowed = prefs.allowedChannelNames()
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
            kickToBlockScreen(getString(R.string.block_channel))
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
        prefs.addBlockEvent()
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(this, getString(R.string.toast_prefix, message), Toast.LENGTH_SHORT).show()
    }

    private fun backOutHome(message: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionMs < ACTION_COOLDOWN_MS) return
        lastActionMs = now
        prefs.addBlockEvent()
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, getString(R.string.toast_prefix, message), Toast.LENGTH_SHORT).show()
    }

    private fun kickToBlockScreen(reason: String?) {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionMs < ACTION_COOLDOWN_MS) return
        lastActionMs = now
        prefs.addBlockEvent()
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            try {
                val intent = Intent(this, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (reason != null) intent.putExtra(BlockActivity.EXTRA_REASON, reason)
                startActivity(intent)
            } catch (e: Exception) {
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

        val BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.sec.android.app.sbrowser",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "com.brave.browser",
            "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.duckduckgo.mobile.android",
            "com.vivaldi.browser", "com.kiwibrowser.browser", "com.mi.globalbrowser", "com.UCMobile.intl",
            "com.google.android.apps.chrome", "com.android.browser", "org.chromium.chrome"
        )

        // Names of the address-bar views in those browsers.
        val URL_ID_PARTS = listOf(
            "url_bar", "location_bar_edit_text", "mozac_browser_toolbar_url_view", "url_bar_title",
            "toolbar_url", "omnibox", "addressbar", "url_field"
        )

        // Never blocked, so the phone stays usable.
        val ALWAYS_OK = setOf(
            "com.android.systemui", "android", "com.android.phone", "com.android.server.telecom",
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
            "com.android.incallui", "com.android.emergency", "com.google.android.gms",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller"
        )

        /** Apps the user chose "Open anyway" for on the pause screen, and until when (epoch ms). */
        val pauseGrants = java.util.concurrent.ConcurrentHashMap<String, Long>()

        /** After an emergency exit, limits are lifted until this time (epoch ms). */
        @Volatile
        var emergencyGraceUntil = 0L

        fun hostOf(text: String): String? {
            var t = text.trim().lowercase()
            if (t.isEmpty() || t.contains(' ')) return null
            t = t.removePrefix("https://").removePrefix("http://")
            val end = t.indexOfFirst { it == '/' || it == '?' || it == '#' || it == ':' }
            if (end >= 0) t = t.substring(0, end)
            t = t.removePrefix("www.").removePrefix("m.")
            return if (t.contains('.')) t else null
        }

        fun hostMatches(host: String, sites: Set<String>): Boolean =
            sites.any { host == it || host.endsWith(".$it") }

        private const val SCAN_INTERVAL_MS = 350L
        private const val ACTION_COOLDOWN_MS = 1200L
        private const val GRACE_MS = 1500L
        private const val MAX_NODES = 1500
    }
}
