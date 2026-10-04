package org.solsticesw.vivlia.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.data.repository.CatalogRepository
import org.solsticesw.vivlia.data.repository.LibraryRepository
import org.solsticesw.vivlia.domain.model.RemoteEntry

data class SourceSearchResult(
    val source: SourceEntity,
    val isLoading: Boolean = false,
    val entries: List<RemoteEntry> = emptyList(),
    val error: String? = null
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val sourceResults: List<SourceSearchResult> = emptyList()
)

class SearchViewModel(
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _searchResults = MutableStateFlow<List<SourceSearchResult>>(emptyList())

    val uiState: StateFlow<SearchUiState> = combine(
        _query,
        _isSearching,
        _searchResults
    ) { query, searching, results ->
        SearchUiState(
            query = query,
            isSearching = searching,
            sourceResults = results
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun performSearch() {
        val searchQuery = _query.value.trim()
        if (searchQuery.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            val multiResults = catalogRepository.multiSourceSearch(searchQuery)
            _isSearching.value = false

            _searchResults.value = multiResults.map { (source, result) ->
                SourceSearchResult(
                    source = source,
                    isLoading = false,
                    entries = result.getOrDefault(emptyList()),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun addToLibrary(remoteEntry: RemoteEntry) {
        viewModelScope.launch {
            libraryRepository.saveNewEntry(
                sourceId = remoteEntry.sourceId,
                url = remoteEntry.url,
                title = remoteEntry.title,
                coverUrl = remoteEntry.coverUrl,
                summary = remoteEntry.summary,
                mediaType = remoteEntry.mediaType.name,
                inLibrary = true
            )
        }
    }
}
