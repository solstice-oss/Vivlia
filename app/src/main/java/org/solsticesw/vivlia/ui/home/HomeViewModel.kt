package org.solsticesw.vivlia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.ReadingSessionEntity
import org.solsticesw.vivlia.data.repository.HistoryRepository
import org.solsticesw.vivlia.data.repository.LibraryRepository

data class HomeUiState(
    val continueReadingEntry: LibraryEntryEntity? = null,
    val recentSession: ReadingSessionEntity? = null,
    val recentlyAdded: List<LibraryEntryEntity> = emptyList(),
    val favorites: List<LibraryEntryEntity> = emptyList(),
    val totalEntriesCount: Int = 0,
    val unreadChaptersCount: Int = 0,
    val isLoading: Boolean = false,
    val isEmptyState: Boolean = true
)

class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        libraryRepository.getAllLibraryEntriesFlow(),
        historyRepository.getRecentHistoryFlow(limit = 5)
    ) { entries, history ->
        val recentlyAdded = entries.sortedByDescending { it.addedAt }.take(5)
        val favorites = entries.take(5)
        val totalCount = entries.size
        val unreadCount = entries.sumOf { it.unreadChapters }

        val recentSession = history.firstOrNull()
        val continueReading = if (recentSession != null) {
            entries.find { it.id == recentSession.entryId }
        } else {
            entries.maxByOrNull { it.lastReadAt ?: 0L }
        }

        HomeUiState(
            continueReadingEntry = continueReading,
            recentSession = recentSession,
            recentlyAdded = recentlyAdded,
            favorites = favorites,
            totalEntriesCount = totalCount,
            unreadChaptersCount = unreadCount,
            isLoading = false,
            isEmptyState = entries.isEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )
}
