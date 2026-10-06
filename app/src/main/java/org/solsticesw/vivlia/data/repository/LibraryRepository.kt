package org.solsticesw.vivlia.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.EntryAuthorEntity
import org.solsticesw.vivlia.data.local.entity.EntryGenreEntity
import org.solsticesw.vivlia.data.local.entity.EntryTagEntity
import org.solsticesw.vivlia.data.local.entity.EntryTitleEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.local.LOCAL_SOURCE_ID
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.SourceProvider

class LibraryRepository(private val database: AppDatabase) {
    private val libraryEntryDao = database.libraryEntryDao()
    private val chapterDao = database.chapterDao()

    fun getAllLibraryEntriesFlow(): Flow<List<LibraryEntryEntity>> {
        return libraryEntryDao.getAllLibraryEntriesFlow(LOCAL_SOURCE_ID)
    }

    fun searchLibraryEntriesFlow(query: String): Flow<List<LibraryEntryEntity>> {
        return if (query.isBlank()) {
            libraryEntryDao.getAllLibraryEntriesFlow(LOCAL_SOURCE_ID)
        } else {
            libraryEntryDao.searchLibraryEntriesFlow(query, LOCAL_SOURCE_ID)
        }
    }

    suspend fun getEntryById(entryId: Long): LibraryEntryEntity? {
        return libraryEntryDao.getById(entryId)
    }

    fun getEntryFlow(entryId: Long): Flow<LibraryEntryEntity?> {
        return libraryEntryDao.getByIdFlow(entryId)
    }

    fun getAuthorsFlow(entryId: Long): Flow<List<EntryAuthorEntity>> {
        return libraryEntryDao.getAuthorsForEntryFlow(entryId)
    }

    fun getGenresFlow(entryId: Long): Flow<List<EntryGenreEntity>> {
        return libraryEntryDao.getGenresForEntryFlow(entryId)
    }

    fun getChaptersForEntryFlow(entryId: Long): Flow<List<ChapterEntity>> {
        return chapterDao.getChaptersForEntryFlow(entryId)
    }

    suspend fun toggleInLibrary(entryId: Long, inLibrary: Boolean) {
        libraryEntryDao.toggleInLibrary(entryId, inLibrary)
    }

    suspend fun markChapterRead(chapterId: Long, read: Boolean) {
        chapterDao.markAsRead(chapterId, read)
        val chapter = chapterDao.getChapterById(chapterId)
        if (chapter != null) {
            updateUnreadCount(chapter.entryId)
        }
    }

    suspend fun toggleChapterBookmark(chapterId: Long, bookmark: Boolean) {
        chapterDao.updateBookmark(chapterId, bookmark)
    }

    suspend fun updateLastPageRead(chapterId: Long, lastPage: Int) {
        chapterDao.updateLastPageRead(chapterId, lastPage)
    }

    suspend fun saveNewEntry(
        sourceId: String,
        url: String,
        title: String,
        coverUrl: String?,
        summary: String?,
        mediaType: String,
        inLibrary: Boolean = true
    ): Long {
        val existing = libraryEntryDao.getBySourceAndUrl(sourceId, url)
        if (existing != null) {
            if (inLibrary && !existing.inLibrary) {
                libraryEntryDao.toggleInLibrary(existing.id, true)
            }
            return existing.id
        }
        val entity = LibraryEntryEntity(
            sourceId = sourceId,
            url = url,
            title = title,
            coverUrl = coverUrl,
            summary = summary,
            mediaType = mediaType,
            inLibrary = inLibrary,
            addedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return libraryEntryDao.insert(entity)
    }

    suspend fun refreshEntryMetadata(
        entryId: Long,
        sourceProvider: SourceProvider,
        sourceDescriptor: SourceDescriptor
    ): Result<Unit> {
        val entry = libraryEntryDao.getById(entryId)
            ?: return Result.failure(IllegalArgumentException("Entry $entryId not found in database"))

        return try {
            val remoteDetails = sourceProvider.getEntryDetails(sourceDescriptor, entry.url)
            val remoteChapters = sourceProvider.getChapterList(sourceDescriptor, entry.url)

            database.withTransaction {
                val updatedEntry = entry.copy(
                    title = remoteDetails.title.ifBlank { entry.title },
                    coverUrl = remoteDetails.coverUrl ?: entry.coverUrl,
                    summary = remoteDetails.summary ?: entry.summary,
                    status = remoteDetails.status ?: entry.status,
                    mediaType = remoteDetails.mediaType.name,
                    updatedAt = System.currentTimeMillis()
                )
                libraryEntryDao.update(updatedEntry)

                // Preserve existing chapter states (read, bookmark, lastPageRead)
                val existingChaptersMap = chapterDao.getChaptersForEntry(entryId).associateBy { it.url }

                val mergedChapters = remoteChapters.map { remote ->
                    val existing = existingChaptersMap[remote.url]
                    if (existing != null) {
                        existing.copy(
                            name = remote.name,
                            chapterNumber = if (remote.chapterNumber >= 0) remote.chapterNumber else existing.chapterNumber,
                            dateUploaded = if (remote.dateUploaded > 0) remote.dateUploaded else existing.dateUploaded,
                            scanlator = remote.scanlator ?: existing.scanlator,
                            fetchedAt = System.currentTimeMillis()
                        )
                    } else {
                        ChapterEntity(
                            entryId = entryId,
                            url = remote.url,
                            name = remote.name,
                            chapterNumber = remote.chapterNumber,
                            dateUploaded = remote.dateUploaded,
                            scanlator = remote.scanlator,
                            fetchedAt = System.currentTimeMillis()
                        )
                    }
                }

                if (mergedChapters.isNotEmpty()) {
                    chapterDao.insertChapters(mergedChapters)
                }

                // Update metadata tables
                if (remoteDetails.alternativeTitles.isNotEmpty()) {
                    libraryEntryDao.deleteTitlesForEntry(entryId)
                    libraryEntryDao.insertTitles(
                        remoteDetails.alternativeTitles.map { EntryTitleEntity(entryId = entryId, title = it) }
                    )
                }

                val authorName = remoteDetails.author
                if (!authorName.isNullOrBlank()) {
                    libraryEntryDao.deleteAuthorsForEntry(entryId)
                    libraryEntryDao.insertAuthors(
                        listOf(EntryAuthorEntity(entryId = entryId, name = authorName, role = "author"))
                    )
                }

                if (remoteDetails.genres.isNotEmpty()) {
                    libraryEntryDao.deleteGenresForEntry(entryId)
                    libraryEntryDao.insertGenres(
                        remoteDetails.genres.map { EntryGenreEntity(entryId = entryId, genre = it) }
                    )
                }

                if (remoteDetails.tags.isNotEmpty()) {
                    libraryEntryDao.deleteTagsForEntry(entryId)
                    libraryEntryDao.insertTags(
                        remoteDetails.tags.map { EntryTagEntity(entryId = entryId, tag = it) }
                    )
                }

                updateUnreadCount(entryId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateUnreadCount(entryId: Long) {
        val allChapters = chapterDao.getChaptersForEntry(entryId)
        val unreadCount = allChapters.count { !it.read }
        val entry = libraryEntryDao.getById(entryId)
        if (entry != null) {
            libraryEntryDao.update(
                entry.copy(
                    totalChapters = allChapters.size,
                    unreadChapters = unreadCount
                )
            )
        }
    }
}
