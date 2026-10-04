package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_entries",
    indices = [Index(value = ["sourceId", "url"], unique = true)]
)
data class LibraryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: String,
    val url: String,
    val title: String,
    val coverUrl: String? = null,
    val summary: String? = null,
    val status: String? = null,
    val mediaType: String,
    val inLibrary: Boolean = false,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastReadAt: Long? = null,
    val totalChapters: Int = 0,
    val unreadChapters: Int = 0,
    val lastReadChapterId: Long? = null,
    val lastReadPageIndex: Int = 0,
    val readingProgressPercent: Float = 0f,
    val lastOpenedAt: Long? = null,
    val unreadChapterCount: Int = 0
)
