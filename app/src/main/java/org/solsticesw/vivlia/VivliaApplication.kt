package org.solsticesw.vivlia

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.extension.ExtensionManager
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.network.SourceManager
import org.solsticesw.vivlia.local.LocalStorageWorker

class VivliaApplication : Application() {
    private val applicationScope = CoroutineScope(Dispatchers.IO)

    val extensionManager: ExtensionManager by lazy {
        ExtensionManager.getInstance(this)
    }

    companion object {
        lateinit var instance: VivliaApplication
            private set

        fun getExtensionManager(context: Context): ExtensionManager {
            return ExtensionManager.getInstance(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LocalStorageWorker.enqueueScan(this)

        applicationScope.launch {
            try {
                val db = AppDatabase.getInstance(this@VivliaApplication)
                SourceManager(db, extensionManager).registerLocalSource()
                extensionManager.refreshInstalledExtensions()
            } catch (e: Exception) {
                android.util.Log.e("VivliaApplication", "Failed to initialize sources/extensions", e)
            }
        }
    }
}
