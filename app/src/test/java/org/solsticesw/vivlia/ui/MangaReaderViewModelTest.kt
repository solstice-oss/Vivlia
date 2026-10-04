package org.solsticesw.vivlia.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
import org.solsticesw.vivlia.ui.reader.manga.MangaReaderMode
import org.solsticesw.vivlia.ui.reader.manga.MangaReaderViewModel

@RunWith(RobolectricTestRunner::class)
class MangaReaderViewModelTest {

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
    fun testMangaReaderInitializationAndPageNavigation(): Unit = runBlocking {
        val entry = LibraryEntryEntity(
            sourceId = "s1",
            url = "/manga/1",
            title = "One Piece",
            mediaType = MediaType.MANGA.name,
            inLibrary = true
        )
        val entryId = database.libraryEntryDao().insert(entry)

        val ch1 = ChapterEntity(entryId = entryId, url = "/c1", name = "Chapter 1", chapterNumber = 1f, lastPageRead = 1)
        val ch2 = ChapterEntity(entryId = entryId, url = "/c2", name = "Chapter 2", chapterNumber = 2f)
        val ch1Id = database.chapterDao().insertChapter(ch1)
        val ch2Id = database.chapterDao().insertChapter(ch2)

        val viewModel = MangaReaderViewModel(
            entryId = entryId,
            initialChapterId = ch1Id,
            readingProgressRepository = repository
        )

        // Wait for chapter loaded state
        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals("One Piece", state.entryTitle)
        assertEquals("Chapter 1", state.chapterName)
        assertEquals(ch2Id, state.nextChapterId)
        assertTrue(state.pages.isNotEmpty())
        assertEquals(1, state.currentPageIndex)

        // Change page
        viewModel.onPageChanged(3)
        val updatedState = viewModel.uiState.first { it.currentPageIndex == 3 }
        assertEquals(3, updatedState.currentPageIndex)

        // Change reader mode
        viewModel.setReaderMode(MangaReaderMode.WEBTOON)
        val webtoonState = viewModel.uiState.first { it.readerMode == MangaReaderMode.WEBTOON }
        assertEquals(MangaReaderMode.WEBTOON, webtoonState.readerMode)

        // Toggle overlay
        val initialOverlay = webtoonState.showOverlay
        viewModel.toggleOverlay()
        val toggledState = viewModel.uiState.first { it.showOverlay != initialOverlay }
        assertEquals(!initialOverlay, toggledState.showOverlay)
    }
}
