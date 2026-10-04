package org.solsticesw.vivlia.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.solsticesw.vivlia.ui.reader.ln.LightNovelReaderViewModel
import org.solsticesw.vivlia.ui.reader.ln.ReaderTheme

@RunWith(RobolectricTestRunner::class)
class LightNovelReaderViewModelTest {

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
    fun testLightNovelReaderInitializationAndSettings(): Unit = runBlocking {
        val entry = LibraryEntryEntity(
            sourceId = "s2",
            url = "/novel/1",
            title = "Overlord",
            mediaType = MediaType.NOVEL.name,
            inLibrary = true
        )
        val entryId = database.libraryEntryDao().insert(entry)

        val ch1 = ChapterEntity(entryId = entryId, url = "/c1", name = "Volume 1 - Chapter 1", chapterNumber = 1f)
        val ch1Id = database.chapterDao().insertChapter(ch1)

        val viewModel = LightNovelReaderViewModel(
            entryId = entryId,
            initialChapterId = ch1Id,
            readingProgressRepository = repository
        )

        // Wait for chapter load
        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals("Overlord", state.entryTitle)
        assertEquals("Volume 1 - Chapter 1", state.chapterName)
        assertTrue("Novel paragraphs should be populated", state.paragraphs.isNotEmpty())

        // Test typography settings update
        val updatedSettings = state.typographySettings.copy(fontSizeSp = 22f, theme = ReaderTheme.DARK)
        viewModel.updateTypographySettings(updatedSettings)

        val stateWithTheme = viewModel.uiState.first { it.typographySettings.theme == ReaderTheme.DARK }
        assertEquals(22f, stateWithTheme.typographySettings.fontSizeSp)

        // Test add bookmark
        viewModel.addBookmark("Interesting quote")
        val stateWithBookmark = viewModel.uiState.first { it.bookmarks.isNotEmpty() }
        assertTrue(stateWithBookmark.isBookmarked)
        assertEquals("Interesting quote", stateWithBookmark.bookmarks[0].note)
    }
}
