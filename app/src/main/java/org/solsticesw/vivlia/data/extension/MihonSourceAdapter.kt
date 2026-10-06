package org.solsticesw.vivlia.data.extension

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.solsticesw.vivlia.domain.model.RemoteChapter
import org.solsticesw.vivlia.domain.model.RemoteEntry
import org.solsticesw.vivlia.domain.model.RemoteEntryDetails
import org.solsticesw.vivlia.domain.model.RemotePage
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.CatalogProvider
import org.solsticesw.vivlia.domain.provider.SourceProvider

class MihonSourceAdapter(
    private val extensionManager: ExtensionManager
) : CatalogProvider, SourceProvider {

    override suspend fun getPopular(source: SourceDescriptor, page: Int): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val mihonSource = extensionManager.getSource(source.id)
            ?: return@withContext emptyList()
        val mangasPage = mihonSource.getPopularManga(page)
        mangasPage.mangas.map { sManga -> sManga.toRemoteEntry(source) }
    }

    override suspend fun getLatest(source: SourceDescriptor, page: Int): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val mihonSource = extensionManager.getSource(source.id)
            ?: return@withContext emptyList()
        val mangasPage = mihonSource.getLatestUpdates(page)
        mangasPage.mangas.map { sManga -> sManga.toRemoteEntry(source) }
    }

    override suspend fun search(
        source: SourceDescriptor,
        query: String,
        page: Int,
        filters: Map<String, Any>
    ): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val mihonSource = extensionManager.getSource(source.id)
            ?: return@withContext emptyList()
        val filterList = mihonSource.getFilterList()
        val mangasPage = mihonSource.getSearchManga(page, query, filterList)
        mangasPage.mangas.map { sManga -> sManga.toRemoteEntry(source) }
    }

    override suspend fun getEntryDetails(
        source: SourceDescriptor,
        url: String
    ): RemoteEntryDetails = withContext(Dispatchers.IO) {
        val mihonSource = extensionManager.getSource(source.id)
            ?: throw IllegalArgumentException("Mihon source ${source.id} not found")
        val sManga = SManga.create().apply { this.url = url }
        val updatedManga = mihonSource.getMangaUpdate(
            manga = sManga,
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false
        ).manga
        updatedManga.toRemoteEntryDetails(source)
    }

    override suspend fun getChapterList(
        source: SourceDescriptor,
        entryUrl: String
    ): List<RemoteChapter> = withContext(Dispatchers.IO) {
        val mihonSource = extensionManager.getSource(source.id)
            ?: throw IllegalArgumentException("Mihon source ${source.id} not found")
        val sManga = SManga.create().apply { this.url = entryUrl }
        val chapters = mihonSource.getMangaUpdate(
            manga = sManga,
            chapters = emptyList(),
            fetchDetails = false,
            fetchChapters = true
        ).chapters
        chapters.map { it.toRemoteChapter() }
    }

    override suspend fun getPageList(
        source: SourceDescriptor,
        chapterUrl: String
    ): List<RemotePage> = withContext(Dispatchers.IO) {
        val mihonSource = extensionManager.getSource(source.id)
            ?: throw IllegalArgumentException("Mihon source ${source.id} not found")
        val sChapter = SChapter.create().apply { this.url = chapterUrl }
        val pages = mihonSource.getPageList(sChapter)
        if (mihonSource is HttpSource) {
            for (page in pages) {
                if (page.imageUrl.isNullOrBlank()) {
                    try {
                        page.imageUrl = mihonSource.getImageUrl(page)
                    } catch (_: Exception) {}
                }
            }
        }
        pages.mapIndexed { idx, page -> page.toRemotePage(idx, mihonSource) }
    }
}
