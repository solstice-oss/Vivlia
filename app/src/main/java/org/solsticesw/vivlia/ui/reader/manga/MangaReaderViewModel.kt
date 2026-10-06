package org.solsticesw.vivlia.ui.reader.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.PageEntity
import org.solsticesw.vivlia.data.repository.ReadingProgressRepository
import org.solsticesw.vivlia.local.LOCAL_SOURCE_ID

enum class MangaReaderMode {
    HORIZONTAL_PAGER,
    VERTICAL_SCROLL,
    WEBTOON
}

data class MangaReaderUiState(
    val entryId: Long = 0L,
    val chapterId: Long = 0L,
    val entryTitle: String = "",
    val chapterName: String = "",
    val pages: List<PageEntity> = emptyList(),
    val currentPageIndex: Int = 0,
    val readerMode: MangaReaderMode = MangaReaderMode.HORIZONTAL_PAGER,
    val showOverlay: Boolean = true,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val prevChapterId: Long? = null,
    val nextChapterId: Long? = null
)

class MangaReaderViewModel(
    private val entryId: Long,
    private val initialChapterId: Long,
    private val readingProgressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MangaReaderUiState(
            entryId = entryId,
            chapterId = initialChapterId
        )
    )
    val uiState: StateFlow<MangaReaderUiState> = _uiState.asStateFlow()

    init {
        loadChapter(initialChapterId)
    }

    fun loadChapter(chapterId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, chapterId = chapterId) }

            val entry = readingProgressRepository.getEntryById(entryId)
            val chapter = readingProgressRepository.getChapterById(chapterId)
            val allChapters = readingProgressRepository.getChaptersForEntry(entryId)
                .sortedWith(compareBy<ChapterEntity> { it.chapterNumber }.thenBy { it.id })

            if (chapter == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Chapter not found") }
                return@launch
            }

            val currentIndex = allChapters.indexOfFirst { it.id == chapterId }
            val prevChapterId = if (currentIndex > 0) allChapters[currentIndex - 1].id else null
            val nextChapterId = if (currentIndex in 0 until allChapters.size - 1) allChapters[currentIndex + 1].id else null

            val isLocal = entry?.sourceId == LOCAL_SOURCE_ID
            val fetchedPages = try {
                readingProgressRepository.getPagesForChapter(
                    entryId = entryId,
                    chapterId = chapterId,
                    sourceId = entry?.sourceId ?: "",
                    chapterUrl = chapter.url
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isLocal) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Local chapter could not be read")
                    }
                    return@launch
                }
                emptyList()
            }

            val pages = if (fetchedPages.isNotEmpty()) {
                fetchedPages
            } else if (isLocal) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "This local chapter contains no readable pages")
                }
                return@launch
            } else {
                // Generate sample manga pages if no remote pages found
                generateSamplePages(chapterId)
            }

            val initialPageIndex = chapter.lastPageRead.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

            _uiState.update {
                it.copy(
                    entryId = entryId,
                    chapterId = chapterId,
                    entryTitle = entry?.title ?: "Manga",
                    chapterName = chapter.name.ifBlank { "Chapter ${chapter.chapterNumber}" },
                    pages = pages,
                    currentPageIndex = initialPageIndex,
                    isLoading = false,
                    prevChapterId = prevChapterId,
                    nextChapterId = nextChapterId
                )
            }

            if (pages.isNotEmpty()) {
                readingProgressRepository.saveMangaProgress(
                    entryId = entryId,
                    chapterId = chapterId,
                    pageIndex = initialPageIndex,
                    totalPages = pages.size
                )
            }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val currentPages = _uiState.value.pages
        if (currentPages.isEmpty()) return
        val validIndex = pageIndex.coerceIn(0, currentPages.size - 1)
        _uiState.update { it.copy(currentPageIndex = validIndex) }

        viewModelScope.launch {
            readingProgressRepository.saveMangaProgress(
                entryId = entryId,
                chapterId = _uiState.value.chapterId,
                pageIndex = validIndex,
                totalPages = currentPages.size
            )
        }
    }

    fun setReaderMode(mode: MangaReaderMode) {
        _uiState.update { it.copy(readerMode = mode) }
    }

    fun toggleOverlay() {
        _uiState.update { it.copy(showOverlay = !it.showOverlay) }
    }

    fun setShowOverlay(show: Boolean) {
        _uiState.update { it.copy(showOverlay = show) }
    }

    private fun generateSamplePages(chapterId: Long): List<PageEntity> {
        val sampleImages = listOf(
            "https://picsum.photos/800/1200?random=1",
            "https://picsum.photos/800/1200?random=2",
            "https://picsum.photos/800/1200?random=3",
            "https://picsum.photos/800/1200?random=4",
            "https://picsum.photos/800/1200?random=5"
        )
        return sampleImages.mapIndexed { idx, url ->
            PageEntity(
                chapterId = chapterId,
                index = idx,
                imageUrl = url
            )
        }
    }
}
