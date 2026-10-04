package org.solsticesw.vivlia.data.network

import okhttp3.OkHttpClient
import org.solsticesw.vivlia.domain.model.RemoteChapter
import org.solsticesw.vivlia.domain.model.RemoteEntry
import org.solsticesw.vivlia.domain.model.RemoteEntryDetails
import org.solsticesw.vivlia.domain.model.RemotePage
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.CatalogProvider
import org.solsticesw.vivlia.domain.provider.SourceProvider

class DefaultCatalogProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : CatalogProvider {
    override suspend fun getPopular(source: SourceDescriptor, page: Int): List<RemoteEntry> {
        return emptyList()
    }

    override suspend fun getLatest(source: SourceDescriptor, page: Int): List<RemoteEntry> {
        return emptyList()
    }

    override suspend fun search(
        source: SourceDescriptor,
        query: String,
        page: Int,
        filters: Map<String, Any>
    ): List<RemoteEntry> {
        return emptyList()
    }
}

class DefaultSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : SourceProvider {
    override suspend fun getEntryDetails(source: SourceDescriptor, url: String): RemoteEntryDetails {
        return RemoteEntryDetails(
            url = url,
            title = "Sample Title",
            sourceId = source.id
        )
    }

    override suspend fun getChapterList(source: SourceDescriptor, entryUrl: String): List<RemoteChapter> {
        return emptyList()
    }

    override suspend fun getPageList(source: SourceDescriptor, chapterUrl: String): List<RemotePage> {
        return emptyList()
    }
}
