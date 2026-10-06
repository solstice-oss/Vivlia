package org.solsticesw.vivlia

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.extension.ExtensionManager
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.network.SourceManager
import org.solsticesw.vivlia.local.LocalStorageWorker

class VivliaApplication : Application() {
    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LocalStorageWorker.enqueueScan(this)

        applicationScope.launch {
            try {
                val db = AppDatabase.getInstance(this@VivliaApplication)
                SourceManager(db).registerLocalSource()
                ExtensionManager(this@VivliaApplication, db).refreshInstalledExtensions()
            } catch (e: Exception) {
                android.util.Log.e("VivliaApplication", "Failed to initialize sources/extensions", e)
            }
        }
    }
}
