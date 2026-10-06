package org.solsticesw.vivlia.data.extension

import org.solsticesw.vivlia.data.network.DefaultSourceProvider
import org.solsticesw.vivlia.domain.model.ProviderType
import org.solsticesw.vivlia.domain.model.RemoteChapter
import org.solsticesw.vivlia.domain.model.RemoteEntryDetails
import org.solsticesw.vivlia.domain.model.RemotePage
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.SourceProvider
import org.solsticesw.vivlia.local.LOCAL_SOURCE_ID

class CompositeSourceProvider(
    private val localSourceProvider: SourceProvider,
    private val mihonAdapter: MihonSourceAdapter,
    private val defaultProvider: SourceProvider = DefaultSourceProvider()
) : SourceProvider {

    override suspend fun getEntryDetails(source: SourceDescriptor, url: String): RemoteEntryDetails {
        return when {
            source.id == LOCAL_SOURCE_ID -> localSourceProvider.getEntryDetails(source, url)
            source.providerType == ProviderType.MIHON -> mihonAdapter.getEntryDetails(source, url)
            else -> defaultProvider.getEntryDetails(source, url)
        }
    }

    override suspend fun getChapterList(source: SourceDescriptor, entryUrl: String): List<RemoteChapter> {
        return when {
            source.id == LOCAL_SOURCE_ID -> localSourceProvider.getChapterList(source, entryUrl)
            source.providerType == ProviderType.MIHON -> mihonAdapter.getChapterList(source, entryUrl)
            else -> defaultProvider.getChapterList(source, entryUrl)
        }
    }

    override suspend fun getPageList(source: SourceDescriptor, chapterUrl: String): List<RemotePage> {
        return when {
            source.id == LOCAL_SOURCE_ID -> localSourceProvider.getPageList(source, chapterUrl)
            source.providerType == ProviderType.MIHON -> mihonAdapter.getPageList(source, chapterUrl)
            else -> defaultProvider.getPageList(source, chapterUrl)
        }
    }
}
