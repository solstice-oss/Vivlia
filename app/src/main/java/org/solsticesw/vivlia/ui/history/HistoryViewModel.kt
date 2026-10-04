package org.solsticesw.vivlia.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.ReadingSessionEntity
import org.solsticesw.vivlia.data.repository.HistoryRepository
import org.solsticesw.vivlia.data.repository.LibraryRepository

data class HistoryItemUiState(
    val session: ReadingSessionEntity,
    val entry: LibraryEntryEntity?
)

data class HistoryUiState(
    val historyItems: List<HistoryItemUiState> = emptyList(),
    val showClearConfirmation: Boolean = false,
    val isLoading: Boolean = false
)

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _showClearConfirmation = MutableStateFlow(false)

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.getRecentHistoryFlow(),
        libraryRepository.getAllLibraryEntriesFlow(),
        _showClearConfirmation
    ) { sessions, entries, showClear ->
        val entriesMap = entries.associateBy { it.id }
        val items = sessions.map { session ->
            HistoryItemUiState(
                session = session,
                entry = entriesMap[session.entryId]
            )
        }
        HistoryUiState(
            historyItems = items,
            showClearConfirmation = showClear,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun askClearHistory() {
        _showClearConfirmation.value = true
    }

    fun dismissClearHistory() {
        _showClearConfirmation.value = false
    }

    fun confirmClearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
            _showClearConfirmation.value = false
        }
    }
}
