package org.solsticesw.vivlia.data.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import dalvik.system.DelegateLastClassLoader
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

class DelegateLastClassLoaderCompat(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    constructor(dexPath: String, parent: ClassLoader) : this(dexPath, null, parent)

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        val c = findLoadedClass(name)
        if (c != null) {
            return c
        }

        if (name.startsWith("java.") || name.startsWith("javax.") ||
            name.startsWith("android.") || name.startsWith("androidx.") ||
            name.startsWith("dalvik.") || name.startsWith("com.android.")
        ) {
            try {
                return super.loadClass(name, resolve)
            } catch (_: ClassNotFoundException) {}
        }

        try {
            val clazz = findClass(name)
            if (clazz != null) {
                return clazz
            }
        } catch (_: ClassNotFoundException) {
        }

        return super.loadClass(name, resolve)
    }
}

class ExtensionLoader(
    private val context: Context
) {
    companion object {
        const val EXTENSION_FEATURE = "tachiyomi.extension"
        const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
        const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"

        val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.5, 1.6)
    }

    fun loadInstalledExtensions(): List<LoadedExtension> {
        val packageManager = context.packageManager
        val extensionPackages = try {
            findExtensionPackages(packageManager)
        } catch (t: Throwable) {
            android.util.Log.e("ExtensionLoader", "Failed to find extension packages", t)
            emptyList()
        }

        val loadedExtensions = mutableListOf<LoadedExtension>()
        for (pkgInfo in extensionPackages) {
            try {
                val extension = loadExtension(pkgInfo, packageManager)
                if (extension != null && extension.sources.isNotEmpty()) {
                    loadedExtensions.add(extension)
                }
            } catch (t: Throwable) {
                android.util.Log.e("ExtensionLoader", "Failed to load extension ${pkgInfo.packageName}", t)
            }
        }
        return loadedExtensions
    }

    private fun extractLibVersion(metaData: Bundle?, versionName: String?): Double? {
        if (metaData != null) {
            @Suppress("DEPRECATION")
            val rawLib = metaData.get(METADATA_EXTENSION_LIB)?.toString()
                ?: @Suppress("DEPRECATION") metaData.get("tachiyomi.extension.lib")?.toString()
            if (!rawLib.isNullOrBlank()) {
                val doubleVal = rawLib.toDoubleOrNull()
                if (doubleVal != null) return doubleVal
            }
        }

        if (!versionName.isNullOrBlank()) {
            val parts = versionName.split(".")
            if (parts.size >= 2) {
                val prefix = "${parts[0]}.${parts[1]}"
                val doubleVal = prefix.toDoubleOrNull()
                if (doubleVal != null) return doubleVal
            }
        }

        return null
    }

    private fun isLibVersionSupported(libVersion: Double): Boolean {
        return SUPPORTED_LIB_VERSIONS.any { supported ->
            kotlin.math.abs(supported - libVersion) < 0.001
        }
    }

    private fun findExtensionPackages(pm: PackageManager): List<PackageInfo> {
        val flags = (PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES).toLong()

        val installedPackages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags.toInt())
            }
        } catch (t: Throwable) {
            android.util.Log.e("ExtensionLoader", "Error getting installed packages", t)
            emptyList()
        }

        val extensionPackages = mutableListOf<PackageInfo>()
        for (pkgInfo in installedPackages) {
            var appInfo = pkgInfo.applicationInfo
            if (appInfo == null || appInfo.metaData == null) {
                try {
                    appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkgInfo.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkgInfo.packageName, PackageManager.GET_META_DATA)
                    }
                } catch (_: Throwable) {}
            }
            val metaData = appInfo?.metaData
            val hasFeature = pkgInfo.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true
            val hasClassMeta = metaData?.containsKey(METADATA_SOURCE_CLASS) == true
            val hasFactoryMeta = metaData?.containsKey(METADATA_SOURCE_FACTORY) == true

            if (hasFeature || hasClassMeta || hasFactoryMeta) {
                if (appInfo != null) {
                    pkgInfo.applicationInfo = appInfo
                }
                extensionPackages.add(pkgInfo)
            }
        }

        return extensionPackages.distinctBy { it.packageName }
    }

    private fun loadExtension(pkgInfo: PackageInfo, pm: PackageManager): LoadedExtension? {
        val pkgName = pkgInfo.packageName
        var appInfo = pkgInfo.applicationInfo
        if (appInfo == null || appInfo.metaData == null) {
            try {
                appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
                }
            } catch (t: Throwable) {
                android.util.Log.e("ExtensionLoader", "Failed to get ApplicationInfo for $pkgName", t)
                return null
            }
        }

        val metaData = appInfo.metaData ?: return null

        val versionName = pkgInfo.versionName ?: ""
        val libVersion = extractLibVersion(metaData, versionName)
        if (libVersion == null || !isLibVersionSupported(libVersion)) {
            android.util.Log.w("ExtensionLoader", "Extension $pkgName has unsupported lib version $libVersion")
            return null
        }

        val classNamesStr = metaData.getString(METADATA_SOURCE_CLASS)
        val factoryClassNamesStr = metaData.getString(METADATA_SOURCE_FACTORY)

        if (classNamesStr.isNullOrBlank() && factoryClassNamesStr.isNullOrBlank()) {
            return null
        }

        val classLoader = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                DelegateLastClassLoader(appInfo.sourceDir, context.classLoader)
            } else {
                DelegateLastClassLoaderCompat(appInfo.sourceDir, context.classLoader)
            }
        } catch (t: Throwable) {
            android.util.Log.e("ExtensionLoader", "Failed to create ClassLoader for $pkgName", t)
            return null
        }

        val sources = mutableListOf<Source>()
        val loadedClassNames = mutableSetOf<String>()

        fun loadClass(rawClassName: String) {
            if (rawClassName.isBlank()) return
            val fullClassName = if (rawClassName.startsWith(".")) "$pkgName$rawClassName" else rawClassName
            if (!loadedClassNames.add(fullClassName)) {
                return
            }
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
            } catch (t: Throwable) {
                android.util.Log.e("ExtensionLoader", "Failed to instantiate $fullClassName from $pkgName", t)
            }
        }

        if (!factoryClassNamesStr.isNullOrBlank()) {
            val factoryClasses = factoryClassNamesStr.split(";").map { it.trim() }
            for (factoryClass in factoryClasses) {
                loadClass(factoryClass)
            }
        }

        if (!classNamesStr.isNullOrBlank()) {
            val classes = classNamesStr.split(";").map { it.trim() }
            for (cls in classes) {
                loadClass(cls)
            }
        }

        val distinctSources = sources.distinctBy { it.id }
        if (distinctSources.isEmpty()) return null

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val appLabel = try {
            appInfo.loadLabel(pm).toString()
        } catch (_: Throwable) {
            pkgName
        }
        val extensionName = appLabel.removePrefix("Tachiyomi: ").removePrefix("Mihon: ").trim()

        val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1
        val lang = distinctSources.firstOrNull()?.lang ?: "all"

        return LoadedExtension(
            pkgName = pkgName,
            name = if (extensionName.isNotBlank()) extensionName else pkgName,
            versionName = versionName,
            versionCode = versionCode,
            lang = lang,
            isNsfw = isNsfw,
            sources = distinctSources
        )
    }
}
