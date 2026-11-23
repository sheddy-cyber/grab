package com.grab

import android.app.Application
import com.grab.data.DownloadStore

class GrabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadStore.init(this)
    }
}
