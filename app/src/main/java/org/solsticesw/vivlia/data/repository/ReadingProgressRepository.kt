package org.solsticesw.vivlia.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.BookmarkEntity
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.PageEntity
import org.solsticesw.vivlia.data.local.entity.ReadingSessionEntity
import org.solsticesw.vivlia.data.network.DefaultSourceProvider
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.SourceProvider
import kotlin.math.max

class ReadingProgressRepository(
    private val database: AppDatabase,
    private val sourceProvider: SourceProvider = DefaultSourceProvider()
) {
    private val libraryEntryDao = database.libraryEntryDao()
    private val chapterDao = database.chapterDao()
    private val pageDao = database.pageDao()
    private val readingSessionDao = database.readingSessionDao()
    private val bookmarkDao = database.bookmarkDao()

    suspend fun getEntryById(entryId: Long): LibraryEntryEntity? {
        return libraryEntryDao.getById(entryId)
    }

    suspend fun getChapterById(chapterId: Long): ChapterEntity? {
        return chapterDao.getChapterById(chapterId)
    }

    suspend fun getChaptersForEntry(entryId: Long): List<ChapterEntity> {
        return chapterDao.getChaptersForEntry(entryId)
    }

    suspend fun getPagesForChapter(
        entryId: Long,
        chapterId: Long,
        sourceId: String = "",
        chapterUrl: String = ""
    ): List<PageEntity> {
        val existingPages = pageDao.getPagesForChapter(chapterId)
        if (existingPages.isNotEmpty()) {
            return existingPages
        }

        // Fetch from source provider if url provided
        if (chapterUrl.isNotBlank()) {
            try {
                val descriptor = SourceDescriptor(
                    id = sourceId,
                    name = sourceId,
                    lang = "en",
                    baseUrl = ""
                )
                val remotePages = sourceProvider.getPageList(descriptor, chapterUrl)
                if (remotePages.isNotEmpty()) {
                    val entities = remotePages.mapIndexed { idx, page ->
                        PageEntity(
                            chapterId = chapterId,
                            index = idx,
                            imageUrl = page.imageUrl,
                            text = page.text
                        )
                    }
                    pageDao.insertPages(entities)
                    return pageDao.getPagesForChapter(chapterId)
                }
            } catch (_: Exception) {
            }
        }

        return emptyList()
    }

    suspend fun saveMangaProgress(
        entryId: Long,
        chapterId: Long,
        pageIndex: Int,
        totalPages: Int
    ) {
        database.withTransaction {
            val chapter = chapterDao.getChapterById(chapterId) ?: return@withTransaction
            val effectiveTotal = max(chapter.totalPages, totalPages)
            val isLastPage = effectiveTotal > 0 && pageIndex >= (effectiveTotal - 1)
            val isRead = chapter.read || isLastPage
            val updatedChapter = chapter.copy(
                lastPageRead = pageIndex,
                totalPages = effectiveTotal,
                read = isRead
            )
            chapterDao.updateChapter(updatedChapter)

            val now = System.currentTimeMillis()
            val progressPercent = if (effectiveTotal > 0) ((pageIndex + 1).toFloat() / effectiveTotal).coerceIn(0f, 1f) else 0f
            val session = ReadingSessionEntity(
                entryId = entryId,
                chapterId = chapterId,
                startTime = now,
                endTime = now,
                progress = if (isRead) 1.0f else progressPercent,
                pagesRead = pageIndex + 1
            )
            readingSessionDao.insertSession(session)

            updateEntryReadingProgress(
                entryId = entryId,
                lastReadChapterId = chapterId,
                lastReadPageIndex = pageIndex,
                progressPercent = if (isRead) 100f else (progressPercent * 100f)
            )
        }
    }

    suspend fun saveLightNovelProgress(
        entryId: Long,
        chapterId: Long,
        scrollPercent: Float,
        isEndReached: Boolean = false
    ) {
        database.withTransaction {
            val chapter = chapterDao.getChapterById(chapterId) ?: return@withTransaction
            val isRead = chapter.read || isEndReached || scrollPercent >= 0.99f
            val updatedChapter = chapter.copy(
                read = isRead
            )
            chapterDao.updateChapter(updatedChapter)

            val now = System.currentTimeMillis()
            val progressVal = if (isRead) 1.0f else scrollPercent.coerceIn(0f, 1f)
            val session = ReadingSessionEntity(
                entryId = entryId,
                chapterId = chapterId,
                startTime = now,
                endTime = now,
                progress = progressVal,
                pagesRead = if (isRead) 1 else 0
            )
            readingSessionDao.insertSession(session)

            updateEntryReadingProgress(
                entryId = entryId,
                lastReadChapterId = chapterId,
                lastReadPageIndex = 0,
                progressPercent = if (isRead) 100f else (scrollPercent * 100f)
            )
        }
    }

    suspend fun markChapterRead(chapterId: Long, read: Boolean) {
        database.withTransaction {
            chapterDao.markAsRead(chapterId, read)
            val chapter = chapterDao.getChapterById(chapterId)
            if (chapter != null) {
                updateEntryReadingProgress(
                    entryId = chapter.entryId,
                    lastReadChapterId = chapterId,
                    lastReadPageIndex = chapter.lastPageRead,
                    progressPercent = if (read) 100f else 0f
                )
            }
        }
    }

    private suspend fun updateEntryReadingProgress(
        entryId: Long,
        lastReadChapterId: Long,
        lastReadPageIndex: Int,
        progressPercent: Float
    ) {
        val entry = libraryEntryDao.getById(entryId) ?: return
        val allChapters = chapterDao.getChaptersForEntry(entryId)
        val unreadCount = allChapters.count { !it.read }
        val now = System.currentTimeMillis()

        val updatedEntry = entry.copy(
            lastReadChapterId = lastReadChapterId,
            lastReadPageIndex = lastReadPageIndex,
            readingProgressPercent = progressPercent,
            lastOpenedAt = now,
            lastReadAt = now,
            unreadChapters = unreadCount,
            unreadChapterCount = unreadCount
        )
        libraryEntryDao.update(updatedEntry)
    }

    fun getBookmarksForEntryFlow(entryId: Long): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForEntryFlow(entryId)
    }

    suspend fun getBookmarksForChapter(chapterId: Long): List<BookmarkEntity> {
        return bookmarkDao.getBookmarksForChapter(chapterId)
    }

    suspend fun addBookmark(
        entryId: Long,
        chapterId: Long,
        pageIndex: Int,
        note: String? = null
    ): Long {
        val bookmark = BookmarkEntity(
            entryId = entryId,
            chapterId = chapterId,
            pageIndex = pageIndex,
            note = note,
            createdAt = System.currentTimeMillis()
        )
        return bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        bookmarkDao.deleteBookmarkById(bookmarkId)
    }

    suspend fun insertPages(pages: List<PageEntity>): List<Long> {
        return pageDao.insertPages(pages)
    }
}
