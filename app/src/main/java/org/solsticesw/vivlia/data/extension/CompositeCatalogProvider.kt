package org.solsticesw.vivlia.data.extension

import org.solsticesw.vivlia.data.network.DefaultCatalogProvider
import org.solsticesw.vivlia.domain.model.ProviderType
import org.solsticesw.vivlia.domain.model.RemoteEntry
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.CatalogProvider

class CompositeCatalogProvider(
    private val mihonAdapter: MihonSourceAdapter,
    private val defaultProvider: CatalogProvider = DefaultCatalogProvider()
) : CatalogProvider {

    override suspend fun getPopular(source: SourceDescriptor, page: Int): List<RemoteEntry> {
        return if (source.providerType == ProviderType.MIHON) {
            mihonAdapter.getPopular(source, page)
        } else {
            defaultProvider.getPopular(source, page)
        }
    }

    override suspend fun getLatest(source: SourceDescriptor, page: Int): List<RemoteEntry> {
        return if (source.providerType == ProviderType.MIHON) {
            mihonAdapter.getLatest(source, page)
        } else {
            defaultProvider.getLatest(source, page)
        }
    }

    override suspend fun search(
        source: SourceDescriptor,
        query: String,
        page: Int,
        filters: Map<String, Any>
    ): List<RemoteEntry> {
        return if (source.providerType == ProviderType.MIHON) {
            mihonAdapter.search(source, query, page, filters)
        } else {
            defaultProvider.search(source, query, page, filters)
        }
    }
}
