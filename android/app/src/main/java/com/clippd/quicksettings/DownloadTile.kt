package com.clippd.quicksettings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.clippd.service.DownloadService

class DownloadTile : TileService() {

    override fun onClick() {
        super.onClick()
        
        // Check clipboard for URL
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipItem = clipboard?.primaryClip?.getItemAt(0)
        val text = clipItem?.text?.toString()
        
        if (text != null && isValidTwitterUrl(text)) {
            // Start download service
            val serviceIntent = Intent(this, DownloadService::class.java).apply {
                action = DownloadService.ACTION_START_DOWNLOAD
                putExtra(DownloadService.EXTRA_URL, text)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            // Update tile to show it's processing
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.label = "Downloading..."
            qsTile.updateTile()
            
            // Reset tile after delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                qsTile.state = Tile.STATE_INACTIVE
                qsTile.label = getString(R.string.quick_settings_tile_label)
                qsTile.updateTile()
            }, 2000)
            
        } else {
            // Open app if no valid URL in clipboard
            val intent = Intent(this, com.clippd.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            
            Toast.makeText(this, "No valid Twitter URL in clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidTwitterUrl(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            
            host == "twitter.com" || 
            host == "www.twitter.com" || 
            host == "x.com" || 
            host == "www.x.com" ||
            host.endsWith(".twitter.com") ||
            host.endsWith(".x.com")
        } catch (e: Exception) {
            false
        }
    }
}
