package org.solsticesw.vivlia.ui.browse

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
import org.solsticesw.vivlia.domain.model.RemoteEntry

data class BrowseUiState(
    val sources: List<SourceEntity> = emptyList(),
    val selectedSource: SourceEntity? = null,
    val catalogEntries: List<RemoteEntry> = emptyList(),
    val searchQuery: String = "",
    val isLoadingCatalog: Boolean = false,
    val errorMessage: String? = null
)

class BrowseViewModel(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val _selectedSource = MutableStateFlow<SourceEntity?>(null)
    private val _catalogEntries = MutableStateFlow<List<RemoteEntry>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoadingCatalog = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _internalState = combine(
        _selectedSource,
        _catalogEntries,
        _searchQuery,
        _isLoadingCatalog,
        _errorMessage
    ) { selected, catalog, query, loading, error ->
        BrowseUiState(
            selectedSource = selected,
            catalogEntries = catalog,
            searchQuery = query,
            isLoadingCatalog = loading,
            errorMessage = error
        )
    }

    val uiState: StateFlow<BrowseUiState> = combine(
        catalogRepository.getEnabledSourcesFlow(),
        _internalState
    ) { sources, state ->
        state.copy(sources = sources)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BrowseUiState()
    )

    fun selectSource(source: SourceEntity) {
        _selectedSource.value = source
        loadCatalog(source, _searchQuery.value)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        val currentSource = _selectedSource.value
        if (currentSource != null) {
            loadCatalog(currentSource, query)
        }
    }

    fun refreshCatalog() {
        val currentSource = _selectedSource.value
        if (currentSource != null) {
            loadCatalog(currentSource, _searchQuery.value)
        }
    }

    fun togglePinSource(source: SourceEntity) {
        viewModelScope.launch {
            catalogRepository.togglePinSource(source.id, !source.pinned)
        }
    }

    private fun loadCatalog(source: SourceEntity, query: String) {
        viewModelScope.launch {
            _isLoadingCatalog.value = true
            _errorMessage.value = null
            val result = catalogRepository.searchSourceCatalog(source, query)
            _isLoadingCatalog.value = false
            result.onSuccess { entries ->
                _catalogEntries.value = entries
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to load catalog from ${source.name}"
            }
        }
    }
}
