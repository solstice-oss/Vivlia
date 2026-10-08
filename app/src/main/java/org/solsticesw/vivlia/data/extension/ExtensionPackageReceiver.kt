package org.solsticesw.vivlia.data.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExtensionPackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_PACKAGE_ADDED ||
            action == Intent.ACTION_PACKAGE_REMOVED ||
            action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ExtensionManager.getInstance(context).refreshInstalledExtensions()
                } catch (e: Throwable) {
                    android.util.Log.e("ExtensionPackageReceiver", "Failed to refresh extensions", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
