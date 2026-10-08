package org.solsticesw.vivlia.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.extension.ExtensionManager
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.data.network.DefaultCatalogProvider
import org.solsticesw.vivlia.data.network.SourceManager
import org.solsticesw.vivlia.domain.model.RemoteEntry
import org.solsticesw.vivlia.domain.provider.CatalogProvider

class CatalogRepository(
    private val database: AppDatabase,
    private val catalogProvider: CatalogProvider = DefaultCatalogProvider(),
    extensionManager: ExtensionManager? = ExtensionManager.getInstanceOrNull()
) {
    private val sourceManager = SourceManager(database, extensionManager)

    fun getAllSourcesFlow(): Flow<List<SourceEntity>> {
        return sourceManager.getAllSourcesFlow()
    }

    fun getEnabledSourcesFlow(): Flow<List<SourceEntity>> {
        return sourceManager.getEnabledSourcesFlow()
    }

    suspend fun togglePinSource(sourceId: String, pinned: Boolean) {
        sourceManager.togglePinSource(sourceId, pinned)
    }

    suspend fun toggleEnableSource(sourceId: String, enabled: Boolean) {
        sourceManager.toggleEnableSource(sourceId, enabled)
    }

    suspend fun searchSourceCatalog(
        source: SourceEntity,
        query: String,
        page: Int = 1
    ): Result<List<RemoteEntry>> {
        return try {
            val descriptor = sourceManager.toSourceDescriptor(source)
            val results = if (query.isBlank()) {
                catalogProvider.getPopular(descriptor, page)
            } else {
                catalogProvider.search(descriptor, query, page)
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun multiSourceSearch(
        query: String
    ): Map<SourceEntity, Result<List<RemoteEntry>>> = coroutineScope {
        val enabledSources = database.sourceDao().getAllSources().filter { it.enabled }
        val deferreds = enabledSources.map { source ->
            async {
                val result = searchSourceCatalog(source, query)
                source to result
            }
        }
        deferreds.awaitAll().toMap()
    }
}
