package com.focusguard.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick-settings tile: tap to start or stop a focus session. */
class FocusTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val prefs = Prefs(this)
        val on = prefs.isSessionActive()
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_hourglass)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (on) {
                val phase = prefs.currentPhase()
                if (phase != null) formatMinutes(((phase.endMs - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0))
                else getString(R.string.tile_on)
            } else {
                getString(R.string.tile_start)
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = Prefs(this)
        if (prefs.isSessionActive()) {
            if (!prefs.sessionStrict) prefs.stopSession()
        } else if (isAccessibilityServiceEnabled(this)) {
            prefs.startSession()
        } else {
            openApp()
        }
        refreshTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
