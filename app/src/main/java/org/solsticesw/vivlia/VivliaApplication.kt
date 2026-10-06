package org.solsticesw.vivlia

import android.app.Application
import org.solsticesw.vivlia.local.LocalStorageWorker

class VivliaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalStorageWorker.enqueueScan(this)
    }
}
