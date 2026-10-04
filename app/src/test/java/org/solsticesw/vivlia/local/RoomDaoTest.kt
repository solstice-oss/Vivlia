package org.solsticesw.vivlia.local

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
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity
import org.solsticesw.vivlia.data.local.entity.ExtensionRepositoryEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.PageEntity
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType

@RunWith(RobolectricTestRunner::class)
class RoomDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testLibraryEntryAndChaptersOperations(): Unit = runBlocking {
        val libraryEntryDao = database.libraryEntryDao()
        val chapterDao = database.chapterDao()

        val entry = LibraryEntryEntity(
            sourceId = "mangadex_123",
            url = "https://mangadex.org/title/456",
            title = "Solo Leveling",
            coverUrl = "https://cover.jpg",
            mediaType = MediaType.MANGA.name,
            inLibrary = true,
            addedAt = System.currentTimeMillis()
        )

        val entryId = libraryEntryDao.insert(entry)
        assertTrue(entryId > 0)

        val fetchedEntry = libraryEntryDao.getById(entryId)
        assertNotNull(fetchedEntry)
        assertEquals("Solo Leveling", fetchedEntry?.title)

        val chapters = listOf(
            ChapterEntity(entryId = entryId, url = "/chap/1", name = "Chapter 1", chapterNumber = 1f),
            ChapterEntity(entryId = entryId, url = "/chap/2", name = "Chapter 2", chapterNumber = 2f)
        )
        chapterDao.insertChapters(chapters)

        val fetchedChapters = chapterDao.getChaptersForEntry(entryId)
        assertEquals(2, fetchedChapters.size)

        // Mark chapter 1 as read
        val chap1 = fetchedChapters.find { it.chapterNumber == 1f }!!
        chapterDao.markAsRead(chap1.id, true)

        val updatedChap1 = chapterDao.getChapterById(chap1.id)
        assertTrue(updatedChap1!!.read)
    }

    @Test
    fun testPagesOperations(): Unit = runBlocking {
        val pageDao = database.pageDao()
        val chapterId = 10L

        val pages = listOf(
            PageEntity(chapterId = chapterId, index = 0, imageUrl = "https://img1.jpg"),
            PageEntity(chapterId = chapterId, index = 1, imageUrl = "https://img2.jpg")
        )
        pageDao.insertPages(pages)

        val fetchedPages = pageDao.getPagesForChapter(chapterId)
        assertEquals(2, fetchedPages.size)
        assertEquals("https://img1.jpg", fetchedPages[0].imageUrl)
    }

    @Test
    fun testRepositoryAndLocalStatePreservation(): Unit = runBlocking {
        val repositoryDao = database.repositoryDao()
        val extensionDao = database.extensionDao()
        val sourceDao = database.sourceDao()

        val repoId = "repo_keiyoushi"
        val repo = ExtensionRepositoryEntity(
            id = repoId,
            name = "Keiyoushi Repo",
            url = "https://github.com/keiyoushi/extensions",
            rawUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json",
            type = ProviderType.MIHON.name
        )
        repositoryDao.insertRepository(repo)

        val extId = "${repoId}_eu.kanade.mangadex"
        val extension = ExtensionEntity(
            id = extId,
            repoId = repoId,
            name = "MangaDex Ext",
            pkgName = "eu.kanade.mangadex",
            versionName = "1.0.0",
            versionCode = 1,
            lang = "en"
        )
        extensionDao.insertExtensions(listOf(extension))

        val sourceId = "${extId}_mangadex_src"
        val source = SourceEntity(
            id = sourceId,
            extensionId = extId,
            repoId = repoId,
            name = "MangaDex",
            lang = "en",
            baseUrl = "https://mangadex.org",
            providerType = ProviderType.MIHON.name,
            mediaType = MediaType.MANGA.name,
            pinned = false,
            enabled = true
        )
        sourceDao.insertSources(listOf(source))

        // User pins the source
        sourceDao.updatePinned(sourceId, true)
        val pinnedSource = sourceDao.getSourceById(sourceId)
        assertTrue(pinnedSource!!.pinned)

        // Simulate repository refresh: Query existing sources, map new refreshed data while preserving pinned state
        val existingSourcesMap = sourceDao.getSourcesForRepo(repoId).associateBy { it.id }

        val newRefreshedSource = SourceEntity(
            id = sourceId,
            extensionId = extId,
            repoId = repoId,
            name = "MangaDex (Updated)",
            lang = "en",
            baseUrl = "https://mangadex.org",
            providerType = ProviderType.MIHON.name,
            mediaType = MediaType.MANGA.name,
            pinned = false, // Raw refreshed data is unpinned
            enabled = true
        )

        val mergedSource = newRefreshedSource.copy(
            pinned = existingSourcesMap[sourceId]?.pinned ?: newRefreshedSource.pinned,
            enabled = existingSourcesMap[sourceId]?.enabled ?: newRefreshedSource.enabled
        )

        // Transactionally update
        sourceDao.insertSources(listOf(mergedSource))

        val finalSource = sourceDao.getSourceById(sourceId)
        assertNotNull(finalSource)
        assertEquals("MangaDex (Updated)", finalSource!!.name)
        assertTrue("Pinned state should be preserved across refreshes!", finalSource.pinned)
    }
}
