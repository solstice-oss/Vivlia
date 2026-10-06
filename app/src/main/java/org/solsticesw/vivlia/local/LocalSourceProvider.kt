package org.solsticesw.vivlia.local

import android.content.Context
import android.net.Uri
import org.solsticesw.vivlia.domain.model.RemotePage
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.SourceProvider
import java.io.FileNotFoundException

class LocalSourceProvider(context: Context) : SourceProvider {
    private val parser = LocalMangaParser(context.applicationContext)

    override suspend fun getEntryDetails(source: SourceDescriptor, url: String) =
        parser.parseDetails(Uri.parse(url)) ?: throw FileNotFoundException("Local manga is unavailable")

    override suspend fun getChapterList(source: SourceDescriptor, entryUrl: String) =
        parser.parseChapters(Uri.parse(entryUrl))

    override suspend fun getPageList(source: SourceDescriptor, chapterUrl: String): List<RemotePage> =
        parser.pagesForChapter(chapterUrl)
}
