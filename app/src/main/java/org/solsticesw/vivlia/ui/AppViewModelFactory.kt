package org.solsticesw.vivlia.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.solsticesw.vivlia.data.extension.CompositeCatalogProvider
import org.solsticesw.vivlia.data.extension.CompositeSourceProvider
import org.solsticesw.vivlia.data.extension.ExtensionManager
import org.solsticesw.vivlia.data.extension.MihonSourceAdapter
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.network.ExtensionRepositoryManager
import org.solsticesw.vivlia.data.repository.CatalogRepository
import org.solsticesw.vivlia.data.repository.HistoryRepository
import org.solsticesw.vivlia.data.repository.LibraryRepository
import org.solsticesw.vivlia.data.repository.ReadingProgressRepository
import org.solsticesw.vivlia.data.repository.SettingsRepository
import org.solsticesw.vivlia.domain.provider.CatalogProvider
import org.solsticesw.vivlia.domain.provider.SourceProvider
import org.solsticesw.vivlia.local.LocalSourceProvider
import org.solsticesw.vivlia.local.LocalStorageRepository
import org.solsticesw.vivlia.ui.browse.BrowseViewModel
import org.solsticesw.vivlia.ui.details.EntryDetailsViewModel
import org.solsticesw.vivlia.ui.history.HistoryViewModel
import org.solsticesw.vivlia.ui.home.HomeViewModel
import org.solsticesw.vivlia.ui.library.LibraryViewModel
import org.solsticesw.vivlia.ui.reader.ln.LightNovelReaderViewModel
import org.solsticesw.vivlia.ui.reader.manga.MangaReaderViewModel
import org.solsticesw.vivlia.ui.repositories.RepositoryViewModel
import org.solsticesw.vivlia.ui.search.SearchViewModel
import org.solsticesw.vivlia.ui.settings.SettingsViewModel

class AppViewModelFactory(
    private val context: Context,
    private val entryId: Long = 0L,
    private val chapterId: Long = 0L
) : ViewModelProvider.Factory {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val extensionManager: ExtensionManager by lazy { ExtensionManager.getInstance(context, database) }
    private val mihonAdapter: MihonSourceAdapter by lazy { MihonSourceAdapter(extensionManager) }
    private val localSourceProvider: LocalSourceProvider by lazy { LocalSourceProvider(context) }

    private val catalogProvider: CatalogProvider by lazy {
        CompositeCatalogProvider(mihonAdapter)
    }

    private val sourceProvider: SourceProvider by lazy {
        CompositeSourceProvider(localSourceProvider, mihonAdapter)
    }

    private val libraryRepository: LibraryRepository by lazy { LibraryRepository(database) }
    private val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(database, catalogProvider = catalogProvider, extensionManager = extensionManager)
    }
    private val historyRepository: HistoryRepository by lazy { HistoryRepository(database) }
    private val settingsRepository: SettingsRepository by lazy { SettingsRepository(database) }
    private val localStorageRepository: LocalStorageRepository by lazy { LocalStorageRepository(context, database) }
    private val readingProgressRepository: ReadingProgressRepository by lazy {
        ReadingProgressRepository(database, sourceProvider = sourceProvider, localSourceProvider = localSourceProvider)
    }
    private val repositoryManager: ExtensionRepositoryManager by lazy { ExtensionRepositoryManager(database) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(libraryRepository, historyRepository) as T
            }
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(libraryRepository) as T
            }
            modelClass.isAssignableFrom(BrowseViewModel::class.java) -> {
                BrowseViewModel(catalogRepository, extensionManager) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(catalogRepository, libraryRepository) as T
            }
            modelClass.isAssignableFrom(EntryDetailsViewModel::class.java) -> {
                EntryDetailsViewModel(
                    entryId,
                    libraryRepository,
                    catalogRepository,
                    sourceProvider = sourceProvider
                ) as T
            }
            modelClass.isAssignableFrom(org.solsticesw.vivlia.ui.local.LocalStorageViewModel::class.java) -> {
                org.solsticesw.vivlia.ui.local.LocalStorageViewModel(context, localStorageRepository) as T
            }
            modelClass.isAssignableFrom(MangaReaderViewModel::class.java) -> {
                MangaReaderViewModel(entryId, chapterId, readingProgressRepository) as T
            }
            modelClass.isAssignableFrom(LightNovelReaderViewModel::class.java) -> {
                LightNovelReaderViewModel(entryId, chapterId, readingProgressRepository) as T
            }
            modelClass.isAssignableFrom(RepositoryViewModel::class.java) -> {
                RepositoryViewModel(database, repositoryManager) as T
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(historyRepository, libraryRepository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(settingsRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}
