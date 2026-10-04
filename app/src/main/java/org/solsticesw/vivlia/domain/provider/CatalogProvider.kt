package org.solsticesw.vivlia.domain.provider

import org.solsticesw.vivlia.domain.model.RemoteEntry
import org.solsticesw.vivlia.domain.model.SourceDescriptor

interface CatalogProvider {
    suspend fun getPopular(source: SourceDescriptor, page: Int): List<RemoteEntry>
    suspend fun getLatest(source: SourceDescriptor, page: Int): List<RemoteEntry>
    suspend fun search(
        source: SourceDescriptor,
        query: String,
        page: Int,
        filters: Map<String, Any> = emptyMap()
    ): List<RemoteEntry>
}
