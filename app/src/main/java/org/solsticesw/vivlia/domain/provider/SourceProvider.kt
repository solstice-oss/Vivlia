package org.solsticesw.vivlia.domain.provider

import org.solsticesw.vivlia.domain.model.RemoteChapter
import org.solsticesw.vivlia.domain.model.RemoteEntryDetails
import org.solsticesw.vivlia.domain.model.RemotePage
import org.solsticesw.vivlia.domain.model.SourceDescriptor

interface SourceProvider {
    suspend fun getEntryDetails(source: SourceDescriptor, url: String): RemoteEntryDetails
    suspend fun getChapterList(source: SourceDescriptor, entryUrl: String): List<RemoteChapter>
    suspend fun getPageList(source: SourceDescriptor, chapterUrl: String): List<RemotePage>
}
