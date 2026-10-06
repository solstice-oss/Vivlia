package org.solsticesw.vivlia.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStorageRulesTest {

    @Test
    fun detectsSupportedContentExtensionsWithoutCaseSensitivity() {
        assertTrue(LocalStorageRules.isImage("Page.JpG"))
        assertTrue(LocalStorageRules.isImage("cover.webp"))
        assertTrue(LocalStorageRules.isArchive("Volume 01.CBZ"))
        assertTrue(LocalStorageRules.isArchive("chapters.ZIP"))
        assertTrue(LocalStorageRules.isEpub("book.EPUB"))
        assertTrue(LocalStorageRules.isSupportedImport("scan.PDF"))
        assertFalse(LocalStorageRules.isSupportedImport("program.exe"))
    }

    @Test
    fun filtersHiddenAndTemporaryEntries() {
        assertTrue(LocalStorageRules.isTemporaryOrHidden(".hidden"))
        assertTrue(LocalStorageRules.isTemporaryOrHidden("chapter.tmp"))
        assertTrue(LocalStorageRules.isTemporaryOrHidden("chapter_tmp"))
        assertFalse(LocalStorageRules.isTemporaryOrHidden("chapter 01.cbz"))
    }

    @Test
    fun naturalSortOrdersNumbersAndUnicodeNamesDeterministically() {
        val names = listOf("Chapter 10", "第2話", "Chapter 2", "chapter 1")
        assertEquals(
            listOf("chapter 1", "Chapter 2", "Chapter 10", "第2話"),
            names.sortedWith { first, second -> LocalStorageRules.compareNatural(first, second) }
        )
    }

    @Test
    fun parsesChapterNumbersAndLeavesUnnumberedTitlesUnknown() {
        assertEquals(12.5f, LocalStorageRules.parseChapterNumber("Chapter 12.5 - Reunion"))
        assertEquals(3f, LocalStorageRules.parseChapterNumber("Vol. 3"))
        assertNull(LocalStorageRules.parseChapterNumber("Extra - Reunion"))
    }

    @Test
    fun rejectsTraversalAndAbsoluteArchivePaths() {
        assertTrue(LocalStorageRules.isSafeArchiveEntry("Chapter 1/page 001.jpg"))
        assertFalse(LocalStorageRules.isSafeArchiveEntry("../outside.jpg"))
        assertFalse(LocalStorageRules.isSafeArchiveEntry("chapter/../../outside.jpg"))
        assertFalse(LocalStorageRules.isSafeArchiveEntry("C:\\outside.jpg"))
        assertFalse(LocalStorageRules.isSafeArchiveEntry("/outside.jpg"))
    }
}
