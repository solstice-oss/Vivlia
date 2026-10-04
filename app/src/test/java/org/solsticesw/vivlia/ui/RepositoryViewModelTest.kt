package org.solsticesw.vivlia.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.ExtensionRepositoryEntity
import org.solsticesw.vivlia.domain.model.ProviderType
import org.solsticesw.vivlia.ui.repositories.RepositoryViewModel

@RunWith(RobolectricTestRunner::class)
class RepositoryViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var viewModel: RepositoryViewModel

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = RepositoryViewModel(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testProviderAutoDetectionOnUrlChange() {
        viewModel.onInputUrlChanged("https://example.com/repo/plugins.min.json")
        assertEquals(ProviderType.LN_READER, viewModel.uiState.value.selectedProviderType)

        viewModel.onInputUrlChanged("https://example.com/repo/index.json")
        assertEquals(ProviderType.MIHON, viewModel.uiState.value.selectedProviderType)
    }

    @Test
    fun testToggleRepositoryEnabled(): Unit = runBlocking {
        val repo = ExtensionRepositoryEntity(
            id = "test_repo",
            name = "Test Repo",
            url = "https://example.com/index.json",
            rawUrl = "https://example.com/index.json",
            type = ProviderType.MIHON.name,
            enabled = true
        )
        database.repositoryDao().insertRepository(repo)

        viewModel.toggleRepositoryEnabled(repo)

        val updatedRepo = database.repositoryDao().getRepositoryById("test_repo")
        assertNotNull(updatedRepo)
        assertFalse(updatedRepo!!.enabled)
    }

    @Test
    fun testTestConnectionLogic(): Unit = runBlocking {
        viewModel.onInputUrlChanged("https://raw.githubusercontent.com/test/repo/main")
        viewModel.testConnection()

        val state = viewModel.uiState.value
        assertNotNull(state.successMessage)
        assertTrue(state.successMessage!!.contains("URL normalized"))
    }

    @Test
    fun testDeleteRepository(): Unit = runBlocking {
        val repo = ExtensionRepositoryEntity(
            id = "repo_to_delete",
            name = "ToDelete",
            url = "https://example.com/index.json",
            rawUrl = "https://example.com/index.json",
            type = ProviderType.MIHON.name
        )
        database.repositoryDao().insertRepository(repo)

        viewModel.deleteRepository("repo_to_delete")

        val deleted = database.repositoryDao().getRepositoryById("repo_to_delete")
        assertNull(deleted)
    }
}
