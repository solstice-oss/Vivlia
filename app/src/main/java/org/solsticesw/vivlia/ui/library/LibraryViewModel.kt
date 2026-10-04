package org.solsticesw.vivlia.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.repository.LibraryRepository

enum class LibraryTab { ALL, MANGA, LIGHT_NOVEL }
enum class LibrarySortOption { TITLE, RECENTLY_ADDED, UNREAD }
enum class LibraryLayoutMode { GRID, LIST }

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.ALL,
    val searchQuery: String = "",
    val sortOption: LibrarySortOption = LibrarySortOption.TITLE,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID,
    val entries: List<LibraryEntryEntity> = emptyList(),
    val isLoading: Boolean = false
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(LibrarySortOption.TITLE)
    private val _layoutMode = MutableStateFlow(LibraryLayoutMode.GRID)

    val uiState: StateFlow<LibraryUiState> = combine(
        _selectedTab,
        _searchQuery,
        _sortOption,
        _layoutMode,
        libraryRepository.getAllLibraryEntriesFlow()
    ) { tab, query, sort, layout, allEntries ->
        var filtered = allEntries

        // Filter by Tab
        filtered = when (tab) {
            LibraryTab.MANGA -> filtered.filter { it.mediaType.equals("MANGA", ignoreCase = true) }
            LibraryTab.LIGHT_NOVEL -> filtered.filter { it.mediaType.equals("LIGHT_NOVEL", ignoreCase = true) }
            LibraryTab.ALL -> filtered
        }

        // Filter by Query
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        }

        // Sort
        filtered = when (sort) {
            LibrarySortOption.TITLE -> filtered.sortedBy { it.title }
            LibrarySortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedAt }
            LibrarySortOption.UNREAD -> filtered.sortedByDescending { it.unreadChapters }
        }

        LibraryUiState(
            selectedTab = tab,
            searchQuery = query,
            sortOption = sort,
            layoutMode = layout,
            entries = filtered,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(isLoading = true)
    )

    fun onTabSelected(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChanged(option: LibrarySortOption) {
        _sortOption.value = option
    }

    fun toggleLayoutMode() {
        _layoutMode.value = if (_layoutMode.value == LibraryLayoutMode.GRID) {
            LibraryLayoutMode.LIST
        } else {
            LibraryLayoutMode.GRID
        }
    }
}
