package org.solsticesw.vivlia.data.extension

import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import java.util.concurrent.ConcurrentHashMap

class ExtensionManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val loader = ExtensionLoader(context)
    private val activeSources = ConcurrentHashMap<Long, Source>()
    private val sourceDao = database.sourceDao()
    private val extensionDao = database.extensionDao()

    init {
        setupInjekt(context)
    }

    private fun setupInjekt(context: Context) {
        try {
            Injekt.addSingleton(context.applicationContext)
        } catch (_: Exception) {}
        try {
            Injekt.addSingletonFactory { NetworkHelper(context.applicationContext) }
        } catch (_: Exception) {}
    }

    suspend fun refreshInstalledExtensions(): List<LoadedExtension> = withContext(Dispatchers.IO) {
        val loaded = loader.loadInstalledExtensions()
        activeSources.clear()

        val extensionEntities = mutableListOf<ExtensionEntity>()
        val sourceEntities = mutableListOf<SourceEntity>()

        for (ext in loaded) {
            val repoId = "installed_extension"
            extensionEntities.add(
                ExtensionEntity(
                    id = ext.pkgName,
                    repoId = repoId,
                    name = ext.name,
                    pkgName = ext.pkgName,
                    versionName = ext.versionName,
                    versionCode = ext.versionCode.toInt(),
                    lang = ext.lang,
                    isNsfw = ext.isNsfw,
                    installed = true,
                    hasUpdate = false
                )
            )

            for (source in ext.sources) {
                activeSources[source.id] = source
                val baseUrl = (source as? HttpSource)?.baseUrl ?: ""
                sourceEntities.add(
                    SourceEntity(
                        id = source.id.toString(),
                        extensionId = ext.pkgName,
                        repoId = repoId,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = baseUrl,
                        providerType = ProviderType.MIHON.name,
                        mediaType = MediaType.MANGA.name,
                        supportsLatest = source.supportsLatest,
                        isNsfw = ext.isNsfw,
                        enabled = true
                    )
                )
            }
        }

        if (sourceEntities.isNotEmpty()) {
            val existingSources = sourceDao.getAllSources().associateBy { it.id }
            val mergedSources = sourceEntities.map { newSource ->
                val existing = existingSources[newSource.id]
                if (existing != null) {
                    newSource.copy(
                        pinned = existing.pinned,
                        enabled = existing.enabled
                    )
                } else {
                    newSource
                }
            }
            database.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    extensionDao.insertExtensions(extensionEntities)
                    sourceDao.insertSources(mergedSources)
                }
            }
        }

        loaded
    }

    fun getSource(sourceId: Long): Source? {
        if (activeSources.isEmpty()) {
            synchronized(this) {
                if (activeSources.isEmpty()) {
                    val loaded = loader.loadInstalledExtensions()
                    for (ext in loaded) {
                        for (source in ext.sources) {
                            activeSources[source.id] = source
                        }
                    }
                }
            }
        }
        return activeSources[sourceId]
    }

    fun getSource(sourceIdStr: String): Source? {
        val id = sourceIdStr.toLongOrNull() ?: return null
        return getSource(id)
    }

    fun getActiveSources(): List<Source> {
        return activeSources.values.toList()
    }
}
