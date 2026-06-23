package com.grabam.quicksettings

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.grabam.R
import com.grabam.service.DownloadService
import com.grabam.utils.UrlUtils

class DownloadTile : TileService() {

    private val TAG = "GrabAmTile"

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "onTileAdded")
        updateTileState()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "onTileRemoved")
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        updateTileState()
    }

    private fun updateTileState() {
        try {
            val tile = qsTile ?: run {
                Log.w(TAG, "qsTile was null while updating state")
                return
            }

            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.quick_settings_tile_label)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_download)
            tile.updateTile()
            Log.d(TAG, "Tile state updated to ACTIVE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update tile state", e)
            Toast.makeText(applicationContext, "Tile Update Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile Clicked")
        updateTileState()

        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (keyguardManager.isKeyguardLocked) {
                unlockAndRun {
                    startClipboardActivitySafe()
                }
            } else {
                startClipboardActivitySafe()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tile click failed", e)
            Toast.makeText(applicationContext, "Click Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startClipboardActivitySafe() {
        try {
            val intent = Intent(this, TileDownloadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // On Android 14+, use PendingIntent as required by the OS
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                // On older versions, use the direct intent launch
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity", e)
            Toast.makeText(applicationContext, "Launch Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

