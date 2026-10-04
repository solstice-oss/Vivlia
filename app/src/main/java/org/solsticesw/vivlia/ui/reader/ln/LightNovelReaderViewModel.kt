package org.solsticesw.vivlia.ui.reader.ln

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.local.entity.BookmarkEntity
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.repository.ReadingProgressRepository

enum class ReaderTheme(
    val title: String,
    val backgroundColor: Color,
    val textColor: Color
) {
    LIGHT("Light", Color(0xFFFFFFFF), Color(0xFF121212)),
    SEPIA("Sepia", Color(0xFFFBF0D9), Color(0xFF3E2723)),
    DARK("Dark", Color(0xFF1E1E1E), Color(0xFFE0E0E0)),
    OLED("OLED Black", Color(0xFF000000), Color(0xFFE0E0E0))
}

enum class LnTextAlignment(val title: String, val align: TextAlign) {
    LEFT("Left", TextAlign.Left),
    CENTER("Center", TextAlign.Center),
    RIGHT("Right", TextAlign.Right),
    JUSTIFY("Justify", TextAlign.Justify)
}

data class LightNovelTypographySettings(
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.5f,
    val paragraphSpacingDp: Float = 12f,
    val alignment: LnTextAlignment = LnTextAlignment.LEFT,
    val theme: ReaderTheme = ReaderTheme.SEPIA
)

data class LightNovelReaderUiState(
    val entryId: Long = 0L,
    val chapterId: Long = 0L,
    val entryTitle: String = "",
    val chapterName: String = "",
    val paragraphs: List<String> = emptyList(),
    val typographySettings: LightNovelTypographySettings = LightNovelTypographySettings(),
    val showOverlay: Boolean = true,
    val showCustomizationSheet: Boolean = false,
    val showBookmarkDialog: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isBookmarked: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val prevChapterId: Long? = null,
    val nextChapterId: Long? = null,
    val currentScrollPercent: Float = 0f
)

class LightNovelReaderViewModel(
    private val entryId: Long,
    private val initialChapterId: Long,
    private val readingProgressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LightNovelReaderUiState(
            entryId = entryId,
            chapterId = initialChapterId
        )
    )
    val uiState: StateFlow<LightNovelReaderUiState> = _uiState.asStateFlow()

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

            val pages = readingProgressRepository.getPagesForChapter(
                entryId = entryId,
                chapterId = chapterId,
                sourceId = entry?.sourceId ?: "",
                chapterUrl = chapter.url
            )

            val rawParagraphs = pages.mapNotNull { it.text }.filter { it.isNotBlank() }
            val paragraphs = if (rawParagraphs.isNotEmpty()) {
                rawParagraphs
            } else {
                generateSampleNovelParagraphs(chapter.name, entry?.title ?: "Light Novel")
            }

            val chapterBookmarks = readingProgressRepository.getBookmarksForChapter(chapterId)

            _uiState.update {
                it.copy(
                    entryId = entryId,
                    chapterId = chapterId,
                    entryTitle = entry?.title ?: "Light Novel",
                    chapterName = chapter.name.ifBlank { "Chapter ${chapter.chapterNumber}" },
                    paragraphs = paragraphs,
                    bookmarks = chapterBookmarks,
                    isBookmarked = chapter.bookmark || chapterBookmarks.isNotEmpty(),
                    isLoading = false,
                    prevChapterId = prevChapterId,
                    nextChapterId = nextChapterId
                )
            }
        }
    }

    fun updateScrollProgress(scrollPercent: Float, isEndReached: Boolean = false) {
        val validPercent = scrollPercent.coerceIn(0f, 1f)
        _uiState.update { it.copy(currentScrollPercent = validPercent) }

        viewModelScope.launch {
            readingProgressRepository.saveLightNovelProgress(
                entryId = entryId,
                chapterId = _uiState.value.chapterId,
                scrollPercent = validPercent,
                isEndReached = isEndReached
            )
        }
    }

    fun updateTypographySettings(settings: LightNovelTypographySettings) {
        _uiState.update { it.copy(typographySettings = settings) }
    }

    fun toggleOverlay() {
        _uiState.update { it.copy(showOverlay = !it.showOverlay) }
    }

    fun setCustomizationSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showCustomizationSheet = visible) }
    }

    fun setBookmarkDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showBookmarkDialog = visible) }
    }

    fun addBookmark(note: String?) {
        viewModelScope.launch {
            val chapterId = _uiState.value.chapterId
            readingProgressRepository.addBookmark(
                entryId = entryId,
                chapterId = chapterId,
                pageIndex = 0,
                note = note
            )
            val updatedBookmarks = readingProgressRepository.getBookmarksForChapter(chapterId)
            _uiState.update {
                it.copy(
                    bookmarks = updatedBookmarks,
                    isBookmarked = true,
                    showBookmarkDialog = false
                )
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            readingProgressRepository.deleteBookmark(bookmarkId)
            val updatedBookmarks = readingProgressRepository.getBookmarksForChapter(_uiState.value.chapterId)
            _uiState.update {
                it.copy(
                    bookmarks = updatedBookmarks,
                    isBookmarked = updatedBookmarks.isNotEmpty()
                )
            }
        }
    }

    private fun generateSampleNovelParagraphs(chapterTitle: String, bookTitle: String): List<String> {
        return listOf(
            "The gentle breeze carried the rustle of leaves through the quiet courtyard. $bookTitle had stood as a sanctuary for countless seekers of truth across generations.",
            "As the golden afternoon sun slowly dipped behind the horizon, long shadows stretched across the marble floor. Every stone in this place held memories of age-old mysteries waiting to be unraveled.",
            "\"We must proceed with caution,\" whispered the scholar, holding up an ancient brass lamp. \"The records indicated that $chapterTitle contains insights hidden from ordinary eyes.\"",
            "A sudden click echoed through the silent corridor. The heavy wooden door swung open smoothly, revealing an inner chamber lined with pristine manuscripts and gilded leather volumes.",
            "Each page contained delicate calligraphy, rendered in metallic ink that shimmered under the warm lantern light. Reading these passages felt like listening to a lost voice from centuries past.",
            "With bated breath, the journey deeper into the chapter continued. The boundary between legend and reality grew thinner with every paragraph consumed by eager eyes.",
            "Outside, the stars began to appear one by one, painting the night sky with quiet majesty. But inside the reader's focus remained unbroken, immersed completely in the rich tapestry of the narrative."
        )
    }
}
