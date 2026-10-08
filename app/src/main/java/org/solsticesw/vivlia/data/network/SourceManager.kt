package org.solsticesw.vivlia.data.network

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.solsticesw.vivlia.data.extension.ExtensionManager
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.local.LOCAL_SOURCE_ID

class SourceManager(
    private val database: AppDatabase,
    private val extensionManager: ExtensionManager? = ExtensionManager.getInstanceOrNull()
) {
    private val sourceDao = database.sourceDao()

    fun getAllSourcesFlow(): Flow<List<SourceEntity>> {
        return sourceDao.getAllSourcesFlow()
    }

    fun getEnabledSourcesFlow(): Flow<List<SourceEntity>> {
        val extManager = extensionManager ?: ExtensionManager.getInstanceOrNull()
        val activeSourcesFlow: Flow<List<Source>> = extManager?.activeSourcesFlow ?: flowOf(emptyList())

        return combine(sourceDao.getAllSourcesFlow(), activeSourcesFlow) { dbSources, activeSources ->
            val result = mutableListOf<SourceEntity>()

            for (dbSource in dbSources) {
                if (dbSource.enabled) {
                    result.add(dbSource)
                }
            }

            for (active in activeSources) {
                val activeId = active.id.toString()
                if (result.none { it.id == activeId }) {
                    val baseUrl = (active as? HttpSource)?.baseUrl ?: ""
                    result.add(
                        SourceEntity(
                            id = activeId,
                            extensionId = "",
                            repoId = "installed_extension",
                            name = active.name,
                            lang = active.lang,
                            baseUrl = baseUrl,
                            providerType = ProviderType.MIHON.name,
                            mediaType = MediaType.MANGA.name,
                            supportsLatest = active.supportsLatest,
                            isNsfw = false,
                            pinned = false,
                            enabled = true
                        )
                    )
                }
            }

            result.sortedWith(compareByDescending<SourceEntity> { it.pinned }.thenBy { it.name })
        }
    }

    suspend fun getSourceById(id: String): SourceEntity? {
        return sourceDao.getSourceById(id)
    }

    suspend fun togglePinSource(id: String, isPinned: Boolean) {
        sourceDao.updatePinned(id, isPinned)
    }

    suspend fun toggleEnableSource(id: String, isEnabled: Boolean) {
        sourceDao.updateEnabled(id, isEnabled)
    }

    suspend fun registerSources(sources: List<SourceEntity>) {
        if (sources.isEmpty()) return
        val existing = sourceDao.getAllSources().associateBy { it.id }
        val merged = sources.map { newSource ->
            val old = existing[newSource.id]
            if (old != null) {
                newSource.copy(pinned = old.pinned, enabled = old.enabled)
            } else {
                newSource
            }
        }
        sourceDao.insertSources(merged)
    }

    suspend fun registerLocalSource() {
        val localSource = SourceEntity(
            id = LOCAL_SOURCE_ID,
            extensionId = "local",
            repoId = "local_storage",
            name = "Local Storage",
            lang = "all",
            baseUrl = "",
            providerType = ProviderType.UNKNOWN.name,
            mediaType = MediaType.UNKNOWN.name,
            supportsLatest = false,
            isNsfw = false,
            pinned = false,
            enabled = true
        )
        registerSources(listOf(localSource))
    }

    fun toSourceDescriptor(entity: SourceEntity): SourceDescriptor {
        return SourceDescriptor(
            id = entity.id,
            name = entity.name,
            lang = entity.lang,
            baseUrl = entity.baseUrl,
            iconUrl = entity.iconUrl,
            providerType = ProviderType.fromString(entity.providerType),
            mediaType = MediaType.fromString(entity.mediaType),
            supportsLatest = entity.supportsLatest,
            isNsfw = entity.isNsfw
        )
    }
}
