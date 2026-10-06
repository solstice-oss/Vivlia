package org.solsticesw.vivlia.data.extension

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.PageEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType
import org.solsticesw.vivlia.domain.model.RemoteChapter
import org.solsticesw.vivlia.domain.model.RemoteEntry
import org.solsticesw.vivlia.domain.model.RemoteEntryDetails
import org.solsticesw.vivlia.domain.model.RemotePage
import org.solsticesw.vivlia.domain.model.SourceDescriptor

fun SManga.toRemoteEntry(source: SourceDescriptor): RemoteEntry {
    return RemoteEntry(
        id = "${source.id}_$url",
        title = title,
        url = url,
        coverUrl = thumbnail_url,
        summary = description,
        author = author,
        artist = artist,
        status = parseMangaStatus(status),
        genres = getGenres() ?: emptyList(),
        sourceId = source.id,
        mediaType = MediaType.MANGA,
        providerType = ProviderType.MIHON
    )
}

fun SManga.toRemoteEntryDetails(source: SourceDescriptor): RemoteEntryDetails {
    return RemoteEntryDetails(
        url = url,
        title = title,
        author = author,
        artist = artist,
        summary = description,
        coverUrl = thumbnail_url,
        status = parseMangaStatus(status),
        genres = getGenres() ?: emptyList(),
        tags = emptyList(),
        alternativeTitles = emptyList(),
        mediaType = MediaType.MANGA,
        sourceId = source.id
    )
}

fun SChapter.toRemoteChapter(): RemoteChapter {
    return RemoteChapter(
        url = url,
        name = name,
        chapterNumber = if (chapter_number >= 0) chapter_number else -1f,
        dateUploaded = if (date_upload > 0) date_upload else 0L,
        scanlator = scanlator,
        type = MediaType.MANGA
    )
}

fun Page.toRemotePage(index: Int, source: Source?): RemotePage {
    val headersMap = mutableMapOf<String, String>()
    if (source is HttpSource) {
        try {
            val headers = source.headers
            for (i in 0 until headers.size) {
                headersMap[headers.name(i)] = headers.value(i)
            }
        } catch (_: Exception) {}
    }
    return RemotePage(
        index = index,
        imageUrl = imageUrl,
        text = null,
        headers = headersMap
    )
}

fun SManga.toLibraryEntryEntity(sourceId: String): LibraryEntryEntity {
    return LibraryEntryEntity(
        sourceId = sourceId,
        url = url,
        title = title,
        coverUrl = thumbnail_url,
        summary = description,
        status = parseMangaStatus(status),
        mediaType = MediaType.MANGA.name
    )
}

fun SChapter.toChapterEntity(entryId: Long): ChapterEntity {
    return ChapterEntity(
        entryId = entryId,
        url = url,
        name = name,
        chapterNumber = if (chapter_number >= 0) chapter_number else -1f,
        dateUploaded = if (date_upload > 0) date_upload else 0L,
        scanlator = scanlator,
        fetchedAt = System.currentTimeMillis()
    )
}

fun Page.toPageEntity(chapterId: Long): PageEntity {
    return PageEntity(
        chapterId = chapterId,
        index = index,
        imageUrl = imageUrl,
        text = null
    )
}

fun parseMangaStatus(status: Int): String {
    return when (status) {
        SManga.ONGOING -> "Ongoing"
        SManga.COMPLETED -> "Completed"
        SManga.LICENSED -> "Licensed"
        SManga.PUBLISHING_FINISHED -> "Publishing Finished"
        SManga.CANCELLED -> "Cancelled"
        SManga.ON_HIATUS -> "On Hiatus"
        else -> "Unknown"
    }
}
