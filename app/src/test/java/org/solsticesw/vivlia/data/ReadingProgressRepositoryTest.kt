package org.solsticesw.vivlia.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.repository.ReadingProgressRepository
import org.solsticesw.vivlia.domain.model.MediaType

@RunWith(RobolectricTestRunner::class)
class ReadingProgressRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ReadingProgressRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReadingProgressRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSaveMangaProgressAndTransactionUpdates(): Unit = runBlocking {
        // 1. Setup Library Entry and Chapters
        val entry = LibraryEntryEntity(
            sourceId = "src1",
            url = "/manga/1",
            title = "Test Manga",
            mediaType = MediaType.MANGA.name,
            inLibrary = true,
            totalChapters = 2,
            unreadChapters = 2
        )
        val entryId = database.libraryEntryDao().insert(entry)

        val ch1 = ChapterEntity(entryId = entryId, url = "/chap/1", name = "Chapter 1", chapterNumber = 1f)
        val ch2 = ChapterEntity(entryId = entryId, url = "/chap/2", name = "Chapter 2", chapterNumber = 2f)
        val ch1Id = database.chapterDao().insertChapter(ch1)
        database.chapterDao().insertChapter(ch2)

        // 2. Save progress for page 2 out of 10 (not last page)
        repository.saveMangaProgress(entryId = entryId, chapterId = ch1Id, pageIndex = 2, totalPages = 10)

        var updatedEntry = database.libraryEntryDao().getById(entryId)
        var updatedChapter = database.chapterDao().getChapterById(ch1Id)

        assertNotNull(updatedEntry)
        assertNotNull(updatedChapter)
        assertEquals(ch1Id, updatedEntry?.lastReadChapterId)
        assertEquals(2, updatedEntry?.lastReadPageIndex)
        assertEquals(2, updatedChapter?.lastPageRead)
        assertEquals(10, updatedChapter?.totalPages)
        assertEquals(false, updatedChapter?.read)

        // 3. Save progress for last page (page 9 out of 10)
        repository.saveMangaProgress(entryId = entryId, chapterId = ch1Id, pageIndex = 9, totalPages = 10)

        updatedEntry = database.libraryEntryDao().getById(entryId)
        updatedChapter = database.chapterDao().getChapterById(ch1Id)

        assertTrue("Chapter should automatically be marked as read at end", updatedChapter!!.read)
        assertEquals(1, updatedEntry!!.unreadChapters)
        assertEquals(1, updatedEntry.unreadChapterCount)
        assertEquals(100f, updatedEntry.readingProgressPercent, 0.01f)
    }

    @Test
    fun testSaveLightNovelProgressAndBookmarks(): Unit = runBlocking {
        val entry = LibraryEntryEntity(
            sourceId = "src2",
            url = "/novel/1",
            title = "Test Light Novel",
            mediaType = MediaType.NOVEL.name,
            inLibrary = true,
            totalChapters = 1,
            unreadChapters = 1
        )
        val entryId = database.libraryEntryDao().insert(entry)

        val ch1 = ChapterEntity(entryId = entryId, url = "/chap/1", name = "Chapter 1", chapterNumber = 1f)
        val ch1Id = database.chapterDao().insertChapter(ch1)

        // 1. Save scroll percentage progress
        repository.saveLightNovelProgress(entryId = entryId, chapterId = ch1Id, scrollPercent = 0.5f)

        var updatedEntry = database.libraryEntryDao().getById(entryId)
        var updatedChapter = database.chapterDao().getChapterById(ch1Id)

        assertNotNull(updatedEntry)
        assertEquals(ch1Id, updatedEntry?.lastReadChapterId)
        assertEquals(50f, updatedEntry!!.readingProgressPercent, 0.01f)
        assertEquals(false, updatedChapter!!.read)

        // 2. Scroll to end
        repository.saveLightNovelProgress(entryId = entryId, chapterId = ch1Id, scrollPercent = 1.0f, isEndReached = true)

        updatedEntry = database.libraryEntryDao().getById(entryId)
        updatedChapter = database.chapterDao().getChapterById(ch1Id)

        assertTrue("Novel chapter should automatically be marked read at scroll end", updatedChapter!!.read)
        assertEquals(0, updatedEntry!!.unreadChapters)

        // 3. Bookmark operations
        val bookmarkId = repository.addBookmark(entryId = entryId, chapterId = ch1Id, pageIndex = 0, note = "Great quote")
        assertTrue(bookmarkId > 0)

        val bookmarks = repository.getBookmarksForChapter(ch1Id)
        assertEquals(1, bookmarks.size)
        assertEquals("Great quote", bookmarks[0].note)

        repository.deleteBookmark(bookmarkId)
        val emptyBookmarks = repository.getBookmarksForChapter(ch1Id)
        assertTrue(emptyBookmarks.isEmpty())
    }
}
