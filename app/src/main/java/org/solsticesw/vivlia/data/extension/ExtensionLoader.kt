package org.solsticesw.vivlia.data.extension

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

data class LoadedExtension(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val isNsfw: Boolean,
    val sources: List<Source>
)

class ExtensionLoader(
    private val context: Context
) {
    companion object {
        const val EXTENSION_FEATURE = "eu.kanade.tachiyomi.EXTENSION"
        const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
        const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    }

    fun loadInstalledExtensions(): List<LoadedExtension> {
        val packageManager = context.packageManager
        val extensionPackages = findExtensionPackages(packageManager)

        val loadedExtensions = mutableListOf<LoadedExtension>()
        for (pkgInfo in extensionPackages) {
            try {
                val extension = loadExtension(pkgInfo, packageManager)
                if (extension != null && extension.sources.isNotEmpty()) {
                    loadedExtensions.add(extension)
                }
            } catch (e: Exception) {
                android.util.Log.e("ExtensionLoader", "Failed to load extension ${pkgInfo.packageName}", e)
            }
        }
        return loadedExtensions
    }

    private fun findExtensionPackages(pm: PackageManager): List<PackageInfo> {
        val intent = Intent(EXTENSION_FEATURE)
        val flags = PackageManager.GET_META_DATA

        val resolvedPackages = mutableSetOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flagsObj = PackageManager.ResolveInfoFlags.of(flags.toLong())
            pm.queryIntentServices(intent, flagsObj).mapNotNullTo(resolvedPackages) { it.serviceInfo?.packageName }
            pm.queryIntentActivities(intent, flagsObj).mapNotNullTo(resolvedPackages) { it.activityInfo?.packageName }
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, flags).mapNotNullTo(resolvedPackages) { it.serviceInfo?.packageName }
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags).mapNotNullTo(resolvedPackages) { it.activityInfo?.packageName }
        }

        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

        val extensionPackages = mutableListOf<PackageInfo>()
        for (pkgInfo in installedPackages) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            val metaData = appInfo.metaData
            val hasExtensionMeta = metaData?.containsKey(METADATA_SOURCE_CLASS) == true
            val isExtensionPkg = pkgInfo.packageName.startsWith("eu.kanade.tachiyomi.extension") ||
                    pkgInfo.packageName.startsWith("mihon.extension") ||
                    resolvedPackages.contains(pkgInfo.packageName)

            if (hasExtensionMeta || isExtensionPkg) {
                extensionPackages.add(pkgInfo)
            }
        }

        return extensionPackages.distinctBy { it.packageName }
    }

    private fun loadExtension(pkgInfo: PackageInfo, pm: PackageManager): LoadedExtension? {
        val pkgName = pkgInfo.packageName
        val appInfo = pkgInfo.applicationInfo ?: return null
        val metaData = appInfo.metaData ?: return null

        val className = metaData.getString(METADATA_SOURCE_CLASS) ?: return null
        val fullClassName = if (className.startsWith(".")) "$pkgName$className" else className

        val classLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)

        val sources = mutableListOf<Source>()
        try {
            val clazz = Class.forName(fullClassName, false, classLoader)
            val instance = clazz.getDeclaredConstructor().newInstance()

            when (instance) {
                is SourceFactory -> {
                    sources.addAll(instance.createSources())
                }
                is Source -> {
                    sources.add(instance)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExtensionLoader", "Failed to instantiate $fullClassName from $pkgName", e)
            return null
        }

        if (sources.isEmpty()) return null

        val versionName = pkgInfo.versionName ?: ""
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val appLabel = appInfo.loadLabel(pm).toString()
        val extensionName = appLabel.removePrefix("Tachiyomi: ").removePrefix("Mihon: ").trim()

        val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1

        val lang = sources.firstOrNull()?.lang ?: "all"

        return LoadedExtension(
            pkgName = pkgName,
            name = if (extensionName.isNotBlank()) extensionName else pkgName,
            versionName = versionName,
            versionCode = versionCode,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources
        )
    }
}
