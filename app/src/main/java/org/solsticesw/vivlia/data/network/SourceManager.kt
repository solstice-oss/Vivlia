package org.solsticesw.vivlia.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.local.LOCAL_SOURCE_ID

class SourceManager(
    private val database: AppDatabase
) {
    private val sourceDao = database.sourceDao()

    fun getAllSourcesFlow(): Flow<List<SourceEntity>> {
        return sourceDao.getAllSourcesFlow()
    }

    fun getEnabledSourcesFlow(): Flow<List<SourceEntity>> {
        return sourceDao.getAllSourcesFlow().map { sources ->
            sources.filter { it.enabled }
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
