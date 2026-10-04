package org.solsticesw.vivlia.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.network.DefaultSourceProvider
import org.solsticesw.vivlia.data.repository.CatalogRepository
import org.solsticesw.vivlia.data.repository.LibraryRepository
import org.solsticesw.vivlia.domain.model.SourceDescriptor
import org.solsticesw.vivlia.domain.provider.SourceProvider

data class EntryDetailsUiState(
    val entryId: Long = 0L,
    val entry: LibraryEntryEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorBannerMessage: String? = null
)

private data class MetadataState(
    val authors: List<String>,
    val genres: List<String>,
    val isRefreshing: Boolean,
    val errorBannerMessage: String?
)

class EntryDetailsViewModel(
    private val entryId: Long,
    private val libraryRepository: LibraryRepository,
    private val catalogRepository: CatalogRepository,
    private val sourceProvider: SourceProvider = DefaultSourceProvider()
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorBannerMessage = MutableStateFlow<String?>(null)

    private val _entryFlow = libraryRepository.getEntryFlow(entryId)
    private val _chaptersFlow = libraryRepository.getChaptersForEntryFlow(entryId)
    private val _authorsFlow = libraryRepository.getAuthorsFlow(entryId).map { list -> list.map { it.name } }
    private val _genresFlow = libraryRepository.getGenresFlow(entryId).map { list -> list.map { it.genre } }

    private val _metadataFlow = combine(
        _authorsFlow,
        _genresFlow,
        _isRefreshing,
        _errorBannerMessage
    ) { authors, genres, refreshing, error ->
        MetadataState(authors, genres, refreshing, error)
    }

    val uiState: StateFlow<EntryDetailsUiState> = combine(
        _entryFlow,
        _chaptersFlow,
        _metadataFlow
    ) { entry, chapters, meta ->
        EntryDetailsUiState(
            entryId = entryId,
            entry = entry,
            chapters = chapters,
            authors = meta.authors,
            genres = meta.genres,
            isRefreshing = meta.isRefreshing,
            errorBannerMessage = meta.errorBannerMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EntryDetailsUiState(entryId = entryId)
    )

    fun refreshMetadata() {
        val currentEntry = uiState.value.entry ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorBannerMessage.value = null

            val descriptor = SourceDescriptor(
                id = currentEntry.sourceId,
                name = currentEntry.sourceId,
                lang = "en",
                baseUrl = ""
            )

            val result = libraryRepository.refreshEntryMetadata(
                entryId = entryId,
                sourceProvider = sourceProvider,
                sourceDescriptor = descriptor
            )

            _isRefreshing.value = false

            result.onFailure { e ->
                _errorBannerMessage.value = e.message ?: "Failed to refresh details from network"
            }
        }
    }

    fun toggleInLibrary() {
        val currentEntry = uiState.value.entry ?: return
        viewModelScope.launch {
            val newInLibrary = !currentEntry.inLibrary
            libraryRepository.toggleInLibrary(entryId, newInLibrary)
        }
    }

    fun toggleChapterRead(chapter: ChapterEntity) {
        viewModelScope.launch {
            libraryRepository.markChapterRead(chapter.id, !chapter.read)
        }
    }

    fun toggleChapterBookmark(chapter: ChapterEntity) {
        viewModelScope.launch {
            libraryRepository.toggleChapterBookmark(chapter.id, !chapter.bookmark)
        }
    }

    fun dismissErrorBanner() {
        _errorBannerMessage.value = null
    }
}
