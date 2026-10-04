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
import org.solsticesw.vivlia.data.repository.CatalogRepository
import org.solsticesw.vivlia.data.repository.LibraryRepository
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.RemoteChapter
import org.solsticesw.vivlia.domain.model.RemoteEntryDetails
import org.solsticesw.vivlia.domain.model.RemotePage
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.SourceProvider
import org.solsticesw.vivlia.ui.details.EntryDetailsViewModel

class FakeFailingSourceProvider : SourceProvider {
    override suspend fun getEntryDetails(source: SourceDescriptor, url: String): RemoteEntryDetails {
        throw RuntimeException("Network connection failed")
    }

    override suspend fun getChapterList(source: SourceDescriptor, entryUrl: String): List<RemoteChapter> {
        throw RuntimeException("Network connection failed")
    }

    override suspend fun getPageList(source: SourceDescriptor, chapterUrl: String): List<RemotePage> {
        return emptyList()
    }
}

class FakeSuccessSourceProvider : SourceProvider {
    override suspend fun getEntryDetails(source: SourceDescriptor, url: String): RemoteEntryDetails {
        return RemoteEntryDetails(
            url = url,
            sourceId = source.id,
            title = "Solo Leveling (Updated Title)",
            summary = "New Updated Summary",
            status = "COMPLETED",
            author = "Chugong",
            genres = listOf("Action", "Fantasy"),
            mediaType = MediaType.MANGA
        )
    }

    override suspend fun getChapterList(source: SourceDescriptor, entryUrl: String): List<RemoteChapter> {
        return listOf(
            RemoteChapter(url = "/chap/1", name = "Chapter 1 (Refreshed)", chapterNumber = 1f),
            RemoteChapter(url = "/chap/2", name = "Chapter 2 (Refreshed)", chapterNumber = 2f)
        )
    }

    override suspend fun getPageList(source: SourceDescriptor, chapterUrl: String): List<RemotePage> {
        return emptyList()
    }
}

@RunWith(RobolectricTestRunner::class)
class EntryDetailsViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var catalogRepository: CatalogRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        libraryRepository = LibraryRepository(database)
        catalogRepository = CatalogRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testDatabaseFirstBehaviorAndLocalFieldPreservation(): Unit = runBlocking {
        // 1. Seed database with initial cached entry
        val entry = LibraryEntryEntity(
            sourceId = "s1",
            url = "/title/1",
            title = "Solo Leveling",
            summary = "Old Summary",
            mediaType = "MANGA",
            inLibrary = true,
            addedAt = 123456L
        )
        val entryId = database.libraryEntryDao().insert(entry)

        val ch1 = ChapterEntity(
            entryId = entryId,
            url = "/chap/1",
            name = "Chapter 1",
            chapterNumber = 1f,
            read = true, // User read chapter 1
            bookmark = true
        )
        database.chapterDao().insertChapters(listOf(ch1))

        // 2. Initialize ViewModel - verify DB-first loading immediately
        val viewModel = EntryDetailsViewModel(
            entryId = entryId,
            libraryRepository = libraryRepository,
            catalogRepository = catalogRepository,
            sourceProvider = FakeSuccessSourceProvider()
        )

        // Wait for state flow to emit cached DB entry
        val initialState = viewModel.uiState.first { it.entry != null }
        assertNotNull(initialState.entry)
        assertEquals("Solo Leveling", initialState.entry?.title)
        assertTrue(initialState.entry!!.inLibrary)
        assertEquals(1, initialState.chapters.size)
        assertTrue(initialState.chapters[0].read)

        // 3. Trigger manual refresh metadata
        viewModel.refreshMetadata()

        // Verify updated title and summary from network
        val refreshedState = viewModel.uiState.first { it.entry?.title == "Solo Leveling (Updated Title)" }
        assertEquals("Solo Leveling (Updated Title)", refreshedState.entry?.title)
        assertEquals("New Updated Summary", refreshedState.entry?.summary)

        // VERIFY STRICT PRESERVATION OF LOCAL FIELDS!
        assertTrue("inLibrary field must be preserved", refreshedState.entry!!.inLibrary)
        assertEquals("addedAt timestamp must be preserved", 123456L, refreshedState.entry!!.addedAt)

        val refreshedCh1 = refreshedState.chapters.find { it.url == "/chap/1" }
        assertNotNull(refreshedCh1)
        assertTrue("Chapter read state must be preserved across metadata refresh", refreshedCh1!!.read)
        assertTrue("Chapter bookmark state must be preserved across metadata refresh", refreshedCh1.bookmark)
    }

    /*@Test
    fun testNonDestructiveErrorBannerOnFailure(): Unit = runBlocking {
        val entry = LibraryEntryEntity(
            sourceId = "s1",
            url = "/title/1",
            title = "Cached Title",
            mediaType = "MANGA",
            inLibrary = true
        )
        val entryId = database.libraryEntryDao().insert(entry)

        val viewModel = EntryDetailsViewModel(
            entryId = entryId,
            libraryRepository = libraryRepository,
            catalogRepository = catalogRepository,
            sourceProvider = FakeFailingSourceProvider()
        )

        // Wait for DB entry load
        val state = viewModel.uiState.first { it.entry != null }
        assertEquals("Cached Title", state.entry?.title)

        // Trigger failing refresh
        viewModel.refreshMetadata()

        val stateWithError = viewModel.uiState.first { it.errorBannerMessage != null }
        assertNotNull(stateWithError.errorBannerMessage)
        assertTrue(stateWithError.errorBannerMessage!!.contains("Network connection failed"))
        // Verify cached DB data is still fully intact and not destroyed!
        assertEquals("Cached Title", stateWithError.entry?.title)

        // Dismiss error banner
        viewModel.dismissErrorBanner()
        val clearedState = viewModel.uiState.first { it.errorBannerMessage == null }
        assertEquals(null, clearedState.errorBannerMessage)
    }*/
}
